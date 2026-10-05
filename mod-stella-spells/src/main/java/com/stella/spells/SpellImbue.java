package com.stella.spells;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.codec.ByteBufCodecs;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/** A weapon imbued at an anvil with a spellbook casts that spell, weaker, when it lands a melee hit. */
public final class SpellImbue {
	/** The anvil's price in levels; with the equips mod's anvil it reads as emeralds. */
	public static final int IMBUE_XP_COST = 5;
	public static final double IMBUE_POWER = 0.5;
	public static final double IMBUE_DURATION = 0.5;
	/** How far behind its target an imbued Riftwalk sets the wielder down. */
	static final double RIFTWALK_STEP_BEHIND = 0.8;

	/** The spells a piece holds, in the order they were imbued; an old single-spell save reads as a list of one. */
	public record ImbuedSpell(List<Identifier> spells) implements TooltipProvider {
		public static final Codec<ImbuedSpell> CODEC = Codec.either(Identifier.CODEC, Identifier.CODEC.listOf()).xmap(
			either -> either.map(ImbuedSpell::new, list -> new ImbuedSpell(List.copyOf(list))),
			imbued -> imbued.spells().size() == 1 ? Either.left(imbued.spell()) : Either.right(imbued.spells())
		);

		public ImbuedSpell {
			spells = List.copyOf(spells);
		}

		public ImbuedSpell(final Identifier spell) {
			this(List.of(spell));
		}

		/** The first spell imbued. */
		public Identifier spell() {
			return this.spells.get(0);
		}

		@Override
		public void addToTooltip(final Item.TooltipContext context, final Consumer<Component> lines, final TooltipFlag flag, final DataComponentGetter components) {
			for (Identifier spell : this.spells) {
				lines.accept(Component.translatable(
					"spellbook." + Spellbooks.MOD_ID + ".imbued",
					Component.translatable("item." + spell.getNamespace() + "." + spell.getPath())
				).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	public static final DataComponentType<ImbuedSpell> IMBUED = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "imbued_spell"),
		DataComponentType.<ImbuedSpell>builder()
			.persistent(ImbuedSpell.CODEC)
			.networkSynchronized(Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).map(ImbuedSpell::new, ImbuedSpell::spells))
			.build()
	);

	public interface Recorder {
		void cast(ServerPlayer player, Identifier spell, LivingEntity victim);
	}

	/** Test seam: each spell a mob's imbued weapon cast, keyed by the wielder. */
	public interface MobRecorder {
		void cast(Mob wielder, Identifier spell, LivingEntity victim);
	}

	/** The spells a mob's imbued weapon casts: every spell that strikes a single foe. */
	public static final java.util.Set<Identifier> MOB_SPELLS = java.util.Set.of(
		id("spark_shot"), id("frostbind"), id("briar_snare"), id("static_lash"), id("tidal_push"), id("wither_seal"),
		id("ice_lance"), id("soul_siphon"), id("venom_dart"), id("blinding_flash"), id("ember_brand")
	);
	/** Spells no weapon takes: Tide Lungs is a breath held underwater, not a blow. */
	public static final java.util.Set<Identifier> NOT_IMBUABLE = java.util.Set.of(id("tide_lungs"), id("summon_skeleton"));
	/** Arrows imbued for one price. */
	public static final int ARROWS_PER_PRICE = 16;
	private static final MobRecorder SILENT_MOBS = (wielder, spell, victim) -> {
	};
	private static volatile MobRecorder mobRecorder = SILENT_MOBS;

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}

	public static void setMobRecorder(final MobRecorder newRecorder) {
		mobRecorder = newRecorder == null ? SILENT_MOBS : newRecorder;
	}

	/** A new server's tick counter starts again at zero, so no cooldown outlives the server it was set on. */
	public static void onServerStopping(final net.minecraft.server.MinecraftServer server) {
		READY_AT.clear();
		MobImbue.clear();
	}

	/** Test seam: how many wielders hold a cooldown now. */
	public static int readyEntries() {
		return READY_AT.size() + MobImbue.readyEntries();
	}

	private static final Recorder SILENT = (player, spell, victim) -> {
	};
	private static volatile Recorder recorder = SILENT;
	private static volatile Recorder secondRecorder = SILENT;
	/** The cooldown component's own use time: one tick, since a sword's use is nothing to wait on. */
	private static final float COOLDOWN_GROUP_SECONDS = 0.05F;
	/** Server tick at which each player's imbued spell is ready again. */
	private static final Map<UUID, Map<Identifier, Integer>> READY_AT = new ConcurrentHashMap<>();

	private SpellImbue() {
	}

	public static void register() {
		ItemComponentTooltipProviderRegistry.addAfter(DataComponents.LORE, IMBUED);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SpellImbue::afterDamage);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(SpellImbue::onUseEntity);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(SpellImbue::onServerStopping);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** Test seam: a second listener, so two test classes can watch imbued casts at once. */
	public static void setSecondRecorder(final Recorder newRecorder) {
		secondRecorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** The spells a stack holds, in imbue order; empty for anything never imbued. */
	public static List<Identifier> spellsOf(final ItemStack stack) {
		ImbuedSpell imbued = stack.get(IMBUED);
		return imbued == null ? List.of() : imbued.spells();
	}

	// A weapon is anything whose own attack damage the game adds to the hand holding it; arrows of every kind
	// and chestplates take spells too.
	public static boolean canImbue(final ItemStack stack) {
		return !stack.isEmpty() && (isWeapon(stack) || stack.is(ItemTags.ARROWS) || stack.is(ItemTags.CHEST_ARMOR));
	}

	static boolean isWeapon(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID));
	}

	/** The anvil's price: five for the first spell, and five times the last for each spell after. */
	public static int imbueCost(final ItemStack piece) {
		int cost = IMBUE_XP_COST;
		for (int i = 0; i < spellsOf(piece).size(); i++) {
			cost *= 5;
		}
		// A stack of arrows pays once for every sixteen it holds, so a full stack is not one arrow's price.
		if (piece.is(ItemTags.ARROWS)) {
			cost *= (piece.getCount() + ARROWS_PER_PRICE - 1) / ARROWS_PER_PRICE;
		}
		return cost;
	}

	/**
	 * The piece with the book's spell added and nothing else of the book; empty when it holds that spell already.
	 * An awakened book also marks the spell as the piece's one awakened imbue, and upgrades a plain imbue of the
	 * same spell; a piece that has an awakened imbue takes no second awakened book. Arrows never take the mark.
	 */
	public static ItemStack imbue(final ItemStack weapon, final ItemStack book) {
		SpellDefinition spell = ((SpellbookItem) book.getItem()).definition();
		List<Identifier> current = spellsOf(weapon);
		boolean awakened = Awakening.isAwakened(book) && !weapon.is(ItemTags.ARROWS);
		if (NOT_IMBUABLE.contains(spell.id()) || (awakened && weapon.has(Awakening.AWAKENED_IMBUE))) {
			return ItemStack.EMPTY;
		}
		boolean upgrade = awakened && current.contains(spell.id());
		if (current.contains(spell.id()) && !upgrade) {
			return ItemStack.EMPTY;
		}

		ItemStack result = weapon.copy();
		List<Identifier> spells = new java.util.ArrayList<>(current);
		if (!upgrade) {
			spells.add(spell.id());
		}
		result.set(IMBUED, new ImbuedSpell(spells));
		if (awakened) {
			result.set(Awakening.AWAKENED_IMBUE, spell.id());
		}
		// A weapon in hand shows its spell's cooldown in the hotbar; its own group keeps other weapons clear of it.
		if (showsCooldown(result)) {
			result.set(DataComponents.USE_COOLDOWN, new UseCooldown(COOLDOWN_GROUP_SECONDS, java.util.Optional.of(cooldownGroup(spells.get(0)))));
		}
		return result;
	}

	// Swords, axes and maces: a trident or a spear is thrown or charged with its use, which a cooldown would block.
	private static boolean showsCooldown(final ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(net.minecraft.world.item.Items.MACE);
	}

	static Identifier cooldownGroup(final Identifier spell) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "imbued_" + spell.getPath());
	}

	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked
	) {
		if (blocked || MobImbue.casting()) {
			return;
		}
		// An imbued chestplate answers a blow that landed on its wearer: not one a shield stopped, nor one inside
		// the wearer's invulnerability after a hit, which never reaches this event.
		if (victim instanceof ServerPlayer wearer && source.getEntity() instanceof LivingEntity attacker && attacker != wearer && damage > 0.0F) {
			ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
			for (Identifier spell : spellsOf(chest)) {
				cast(wearer, spell, attacker, chest, false);
			}
		}
		if (source.getEntity() instanceof Mob wielder && source.getDirectEntity() == wielder && victim != wielder) {
			mobCast(wielder, victim);
			return;
		}
		// A player's own spell hit names the player as both attacker and direct entity; it is not a blow.
		if (!(source.getEntity() instanceof ServerPlayer player) || victim == player || source.is(DamageTypes.INDIRECT_MAGIC)) {
			return;
		}
		// A melee hit casts the spells of the hand that struck; an arrow, the spells of the arrow itself.
		ItemStack focus;
		if (source.getDirectEntity() == player) {
			focus = player.getMainHandItem();
			if (!isWeapon(focus)) {
				return;
			}
		} else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			focus = arrow.getPickupItemStackOrigin();
		} else {
			return;
		}
		for (Identifier spell : spellsOf(focus)) {
			cast(player, spell, victim, focus, false);
		}
	}

	// A right-click on a hostile with an imbued weapon casts each ready spell at the book's full strength.
	private static InteractionResult onUseEntity(
		final Player player, final Level level, final InteractionHand hand, final Entity entity, final EntityHitResult hit
	) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer caster)
			|| !(entity instanceof Mob target) || !Friends.isFoe(target) || !target.isAlive()) {
			return InteractionResult.PASS;
		}
		ItemStack weapon = caster.getMainHandItem();
		if (!isWeapon(weapon) || spellsOf(weapon).isEmpty()) {
			return InteractionResult.PASS;
		}
		boolean cast = false;
		for (Identifier spell : spellsOf(weapon)) {
			cast |= cast(caster, spell, target, weapon, true);
		}
		return cast ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/**
	 * Casts one imbued spell when it is off cooldown; true when it cast. The spell enchantments on the piece
	 * and on the caster's helmet and chestplate shape it, as they shape a book; an imbued hit then works at
	 * the imbued strength, and a right-click at full strength.
	 */
	private static boolean cast(final ServerPlayer player, final Identifier spell, final LivingEntity victim, final ItemStack piece, final boolean full) {
		SpellDefinition definition = SpellbookCatalog.find(spell);
		if (definition == null) {
			return false;
		}
		int now = player.level().getServer().getTickCount();
		Map<Identifier, Integer> ready = READY_AT.computeIfAbsent(player.getUUID(), key -> new ConcurrentHashMap<>());
		if (now < ready.getOrDefault(definition.id(), Integer.MIN_VALUE)) {
			return false;
		}

		SpellModifiers.CastModifiers base = SpellModifiers.resolve(
			piece, player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST), definition.baseCooldownTicks()
		);
		base = TowerBridge.grow(piece, definition.id().getPath(), base);
		base = RpgBridge.grow(player, definition.id().getPath(), base);
		SpellModifiers.CastModifiers modifiers = full ? base : new SpellModifiers.CastModifiers(
			base.powerMultiplier() * IMBUE_POWER, base.radiusMultiplier(), base.durationMultiplier() * IMBUE_DURATION,
			base.defensiveDurationMultiplier() * IMBUE_DURATION, base.targetRangeBlocks(), base.cooldownTicks(), base.awakened()
		);
		// The piece's own awakened imbue decides; the cast book's flag never reaches an imbued hit.
		modifiers = modifiers.withAwakened(definition.id().equals(piece.get(Awakening.AWAKENED_IMBUE)));
		Mob target = null;
		Vec3 point = null;
		switch (definition.targetMode()) {
			case HOSTILE -> target = victim instanceof Mob mob && mob.isAlive() ? mob : null;
			case BLOCK -> point = victim.position();
			case SELF -> point = player.position();
			case SAFE_TELEPORT -> point = behind(player, victim);
		}
		if ((definition.targetMode() == TargetMode.HOSTILE && target == null) || (definition.targetMode() != TargetMode.HOSTILE && point == null)) {
			return false;
		}

		// The hit just started the victim's hurt cooldown, which would swallow the spell's own damage. It is
		// set aside for the spell and put back after, so a spell that deals nothing leaves it as it was.
		int hurtCooldown = victim.damageCooldownTime;
		victim.damageCooldownTime = 0;
		Vec3 departure = player.position();
		Awakening.report(definition.id(), modifiers.awakened());
		boolean cast = definition.effect().apply(player, target, point, modifiers);
		victim.damageCooldownTime = Math.max(victim.damageCooldownTime, hurtCooldown);
		if (!cast) {
			return false;
		}

		int cooldown = TowerBridge.cooldown(player, modifiers.cooldownTicks());
		ready.put(definition.id(), now + cooldown);
		if (showsCooldown(piece)) {
			// A piece imbued before the hotbar showed cooldowns gets its group on its first cast.
			if (!piece.has(DataComponents.USE_COOLDOWN)) {
				piece.set(DataComponents.USE_COOLDOWN, new UseCooldown(COOLDOWN_GROUP_SECONDS, java.util.Optional.of(cooldownGroup(definition.id()))));
			}
			player.getCooldowns().addCooldown(piece, cooldown);
		}
		SpellVisuals.play(player, definition, target, point, departure, modifiers);
		Spellbooks.LOGGER.info(
			"[SPELL] imbued_cast player={} spell={} victim={} piece={} full={} power={} cooldown={}",
			player.getUUID(), definition.id(), victim.getUUID(), BuiltInRegistries.ITEM.getKey(piece.getItem()), full,
			modifiers.powerMultiplier(), cooldown
		);
		recorder.cast(player, definition.id(), victim);
		secondRecorder.cast(player, definition.id(), victim);
		return true;
	}

	private static void mobCast(final Mob wielder, final LivingEntity victim) {
		for (Identifier spell : spellsOf(wielder.getMainHandItem())) {
			SpellDefinition definition = SpellbookCatalog.find(spell);
			if (definition == null) {
				continue;
			}
			int hurtCooldown = victim.damageCooldownTime;
			boolean cast = MobImbue.cast(wielder, definition, victim);
			victim.damageCooldownTime = Math.max(victim.damageCooldownTime, hurtCooldown);
			if (cast) {
				Spellbooks.LOGGER.info("[SPELL] mob_imbued_cast wielder={} spell={} victim={}", wielder.getUUID(), definition.id(), victim.getUUID());
				mobRecorder.cast(wielder, definition.id(), victim);
			}
		}
	}

	// The spot on the far side of the target, level with it, when the wielder fits there.
	private static Vec3 behind(final ServerPlayer player, final LivingEntity victim) {
		Vec3 away = victim.position().subtract(player.position());
		Vec3 horizontal = new Vec3(away.x, 0.0, away.z);
		if (horizontal.lengthSqr() < 1.0e-6) {
			return null;
		}

		Vec3 spot = victim.position().add(horizontal.normalize().scale(victim.getBbWidth() / 2.0 + player.getBbWidth() / 2.0 + RIFTWALK_STEP_BEHIND));
		return SpellCasting.fits(player, spot) ? spot : null;
	}
}
