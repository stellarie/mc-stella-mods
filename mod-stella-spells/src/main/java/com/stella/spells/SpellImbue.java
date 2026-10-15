package com.stella.spells;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.codec.ByteBufCodecs;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
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
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

/**
 * A weapon imbued at an anvil with a spellbook may cast that spell, weaker, when it lands a melee hit. A bow or
 * a crossbow may cast it when its arrow lands. Each imbued book rolls its own chance on each hit.
 */
public final class SpellImbue {
	/** The first imbue's price, and the base each held spell multiplies; with the equips mod's anvil it reads as emeralds. */
	public static final int IMBUE_XP_COST = 5;
	/** Keeps the price inside an int however many spells a Compendium holds. */
	private static final int MAX_COST = 1_000_000_000;
	public static final double IMBUE_POWER = 0.5;
	public static final double IMBUE_DURATION = 0.5;
	/** The power and duration share of a spell that a fully charged staff attack triggers. */
	public static final double STAFF_STRENGTH = 0.75;
	/** The chance that one imbued book casts on a hit, at 0 LUK. */
	public static final double PROC_BASE_CHANCE = 0.10;
	/** The same chance for a staff's normal attack. */
	public static final double STAFF_PROC_BASE_CHANCE = 0.20;
	/** What each point of LUK adds to that chance. */
	public static final double PROC_CHANCE_PER_LUK = 0.01;
	/** How far behind its target an imbued Riftwalk sets the wielder down. */
	static final double RIFTWALK_STEP_BEHIND = 0.8;

	/** The spells a piece holds, in the order they were imbued; an old single-spell save reads as a list of one. */
	public record ImbuedSpell(List<Identifier> spells) {
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

	}

	/** The tooltip of an imbued piece: the name of each imbued spell, and no description. */
	public static List<Component> tooltipLines(final ItemStack stack) {
		List<Component> lines = new java.util.ArrayList<>();
		for (Identifier spell : spellsOf(stack)) {
			DescriptionLines.add(lines, Component.translatable("item." + spell.getNamespace() + "." + spell.getPath())
				.append(" (" + Math.round(IMBUE_POWER * 100) + "%)")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return lines;
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
	/**
	 * Spells no weapon takes. An ally book is not here: heal-on-hit is a playstyle, and an imbued ally book
	 * passes the wielder - the body that swung - as its ally, so a blow on a mob mends the attacker. What
	 * stays out has no bow behind it: an archer book is refused on a sword, which is no bow; only a bow or a
	 * crossbow takes one, from {@link #BOW_IMBUABLE}. Tide Lungs is a breath held underwater, and Summon
	 * Skeleton is book-only.
	 */
	public static final java.util.Set<Identifier> NOT_IMBUABLE = java.util.stream.Stream.concat(
		SpellbookCatalog.DEFINITIONS.stream()
			.filter(definition -> definition.school() == School.ARCHER)
			.map(SpellDefinition::id),
		java.util.stream.Stream.of(id("tide_lungs"), id("summon_skeleton"), id("summon_wolf"), id("memorize"), id("soul_vulcan"), id("spell_writer"))
	).collect(java.util.stream.Collectors.toUnmodifiableSet());
	/**
	 * The archer books a bow or a crossbow takes: the ones that strike a target. A self buff or a next-shot
	 * book has no victim on an arrow hit, so a bow refuses it.
	 */
	public static final java.util.Set<Identifier> BOW_IMBUABLE = java.util.Set.of(
		id("rapid_fire"), id("piercing_shot"), id("arrow_rain"), id("ankle_snare"),
		id("arrow_repel"), id("explosive_arrow")
	);
	/** Set while a player's imbued spell lands, so the damage it deals never counts as a fresh blow. */
	private static final ThreadLocal<Boolean> PLAYER_CASTING = new ThreadLocal<>();
	/** Arrows imbued for one price. */
	public static final int ARROWS_PER_PRICE = 16;
	private static final MobRecorder SILENT_MOBS = (wielder, spell, victim) -> {
	};
	private static volatile MobRecorder mobRecorder = SILENT_MOBS;
	private static volatile UnbrokenVow.StatSource lukSource = UnbrokenVow::rpgStat;
	/** Test seam: the roll an imbued book's chance is held against. Null rolls the wielder's own random. */
	private static volatile java.util.function.DoubleSupplier procRoll;

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}

	public static void setMobRecorder(final MobRecorder newRecorder) {
		mobRecorder = newRecorder == null ? SILENT_MOBS : newRecorder;
	}

	/** Test seam: pins the LUK reader. A null source returns to the RPG mod's own stats. */
	static void setLukSource(final UnbrokenVow.StatSource source) {
		lukSource = source == null ? UnbrokenVow::rpgStat : source;
	}

	/** Test seam: pins the roll of an imbued book's chance. A null supplier returns to the wielder's own random. */
	static void setProcRoll(final java.util.function.DoubleSupplier roll) {
		procRoll = roll;
	}

	/** Test seam: forgets the cooldowns of one wielder, so a run of hits is not held back by them. */
	static void clearCooldowns(final UUID wielder) {
		READY_AT.remove(wielder);
	}

	/** The chance that one imbued book casts on a hit: the base plus 1 percent per LUK, at most 1. */
	public static double procChance(final LivingEntity wielder) {
		return procChance(wielder, PROC_BASE_CHANCE);
	}

	static double procChance(final LivingEntity wielder, final double base) {
		double luk = Math.max(0.0, lukSource.stat(wielder, "luk"));
		return Math.min(1.0, base + luk * PROC_CHANCE_PER_LUK);
	}

	private static boolean procs(final ServerPlayer wielder) {
		return procs(wielder, PROC_BASE_CHANCE);
	}

	private static boolean procs(final ServerPlayer wielder, final double base) {
		java.util.function.DoubleSupplier fixed = procRoll;
		double roll = fixed == null ? wielder.getRandom().nextDouble() : fixed.getAsDouble();
		return roll < procChance(wielder, base);
	}

	/** Test seam: the power multiplier of the latest imbued cast. */
	static volatile double lastCastPower;

	/**
	 * A staff bolt hit the victim. Only a fully charged swing triggers imbued spells. Each book rolls the staff
	 * chance, or always casts when {@code certain}, and casts at {@link #STAFF_STRENGTH}.
	 */
	static void onStaffHit(
		final ServerPlayer player, final ItemStack staff, final LivingEntity victim, final boolean fullCharge, final boolean certain
	) {
		if (!fullCharge || MobImbue.casting() || PLAYER_CASTING.get() != null) {
			return;
		}
		for (Identifier spell : spellsOf(staff)) {
			if (certain || procs(player, STAFF_PROC_BASE_CHANCE)) {
				cast(player, spell, victim, staff, STAFF_STRENGTH, true);
			}
		}
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
	private static final List<Recorder> EXTRA_RECORDERS = new CopyOnWriteArrayList<>();
	/** The cooldown component's own use time: one tick, since a sword's use is nothing to wait on. */
	private static final float COOLDOWN_GROUP_SECONDS = 0.05F;
	/** Server tick at which each player's imbued spell is ready again. */
	private static final Map<UUID, Map<Identifier, Integer>> READY_AT = new ConcurrentHashMap<>();

	private SpellImbue() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SpellImbue::afterDamage);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(SpellImbue::onUseEntity);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(SpellImbue::onServerStopping);
	}

	/** Test seam: any number of extra listeners, for classes that cannot share the two single slots. */
	public static void addRecorder(final Recorder extra) {
		EXTRA_RECORDERS.add(extra);
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

	/** The anvil's left slot takes a piece to imbue, or a Compendium to fill. */
	public static boolean canHold(final ItemStack stack) {
		return stack.getItem() instanceof CompendiumItem || canImbue(stack);
	}

	// A weapon is anything whose own attack damage the game adds to the hand holding it; arrows of every kind
	// and chestplates take spells too.
	public static boolean canImbue(final ItemStack stack) {
		return !stack.isEmpty() && (isWeapon(stack) || stack.getItem() instanceof ProjectileWeaponItem
			|| stack.is(ItemTags.ARROWS) || stack.is(ItemTags.CHEST_ARMOR));
	}

	static final net.minecraft.tags.TagKey<Item> IMBUABLE = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ITEM,
		net.minecraft.resources.Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "imbuable"));

	static boolean isWeapon(final ItemStack stack) {
		return stack.is(IMBUABLE) || stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID));
	}

	/** The anvil's price: 5 to the power of the spells the piece holds, plus one, so 5, 25, 125 and on. */
	public static int imbueCost(final ItemStack piece) {
		long cost = IMBUE_XP_COST;
		for (int held = spellsOf(piece).size(); held > 0 && cost < MAX_COST; held--) {
			cost *= IMBUE_XP_COST;
		}
		// A stack of arrows pays once for every sixteen it holds, so a full stack is not one arrow's price.
		if (piece.is(ItemTags.ARROWS)) {
			cost *= (piece.getCount() + ARROWS_PER_PRICE - 1) / ARROWS_PER_PRICE;
		}
		return (int) Math.min(MAX_COST, cost);
	}

	/**
	 * The piece with the book's spell added and nothing else of the book; empty when it holds that spell already.
	 * An awakened book also marks the spell as the piece's one awakened imbue, and upgrades a plain imbue of the
	 * same spell; a piece that has an awakened imbue takes no second awakened book. Arrows never take the mark.
	 */
	public static ItemStack imbue(final ItemStack weapon, final ItemStack book) {
		SpellDefinition spell = ((SpellbookItem) book.getItem()).definition();
		List<Identifier> current = spellsOf(weapon);
		boolean compendium = weapon.getItem() instanceof CompendiumItem;
		boolean awakened = Awakening.isAwakened(book) && !weapon.is(ItemTags.ARROWS) && !compendium;
		// A bow takes what a sword takes, and the archer books that strike a target besides.
		boolean barred = !compendium && NOT_IMBUABLE.contains(spell.id())
			&& !(weapon.getItem() instanceof ProjectileWeaponItem && BOW_IMBUABLE.contains(spell.id()));
		if (barred || (awakened && weapon.has(Awakening.AWAKENED_IMBUE))) {
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
		if (blocked || MobImbue.casting() || PLAYER_CASTING.get() != null) {
			return;
		}
		// An imbued chestplate answers a blow that landed on its wearer: not one a shield stopped, nor one inside
		// the wearer's invulnerability after a hit, which never reaches this event.
		if (victim instanceof ServerPlayer wearer && source.getEntity() instanceof LivingEntity attacker && attacker != wearer && damage > 0.0F) {
			ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
			for (Identifier spell : spellsOf(chest)) {
				cast(wearer, spell, attacker, chest, IMBUE_POWER, false);
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
		// A melee hit rolls the spells of the hand that struck; an arrow casts the spells of the arrow itself.
		ItemStack focus;
		boolean rolled;
		if (source.getDirectEntity() == player) {
			focus = player.getMainHandItem();
			if (!isWeapon(focus)) {
				return;
			}
			rolled = true;
		} else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			focus = arrow.getPickupItemStackOrigin();
			castFromBow(player, arrow, victim);
			rolled = false;
		} else {
			return;
		}
		for (Identifier spell : spellsOf(focus)) {
			if (!rolled) {
				cast(player, spell, victim, focus, IMBUE_POWER, false);
			} else if (procs(player)) {
				cast(player, spell, victim, focus, IMBUE_POWER, true);
			}
		}
	}

	// The bow or crossbow that loosed the arrow rolls each of its imbued books on the victim.
	private static void castFromBow(final ServerPlayer player, final AbstractArrow arrow, final LivingEntity victim) {
		ItemStack bow = arrow.getWeaponItem();
		if (bow == null || !(bow.getItem() instanceof ProjectileWeaponItem)) {
			return;
		}
		for (Identifier spell : spellsOf(bow)) {
			if (procs(player)) {
				cast(player, spell, victim, bow, IMBUE_POWER, true);
			}
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
			cast |= cast(caster, spell, target, weapon, 1.0, false);
		}
		return cast ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/**
	 * Casts one imbued spell when it is off cooldown; true when it cast. The spell enchantments on the piece
	 * and on the caster's helmet and chestplate shape it, as they shape a book; an imbued hit then works at
	 * the given strength of power and duration, and a right-click at full strength. A won proc roll ignores the
	 * cooldown and sets none.
	 */
	private static boolean cast(
		final ServerPlayer player, final Identifier spell, final LivingEntity victim, final ItemStack piece, final double strength, final boolean proc
	) {
		SpellDefinition definition = SpellbookCatalog.find(spell);
		if (definition == null) {
			return false;
		}
		int now = player.level().getServer().getTickCount();
		Map<Identifier, Integer> ready = READY_AT.computeIfAbsent(player.getUUID(), key -> new ConcurrentHashMap<>());
		if (!proc && now < ready.getOrDefault(definition.id(), Integer.MIN_VALUE)) {
			return false;
		}

		SpellModifiers.CastModifiers base = SpellModifiers.resolve(
			piece, player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST), definition.baseCooldownTicks()
		);
		BookRefine.remember(player, definition.id().getPath(), 0);
		base = TowerBridge.grow(piece, definition.id().getPath(), base);
		base = RpgBridge.grow(player, definition.id().getPath(), base, definition.school());
		SpellModifiers.CastModifiers modifiers = strength >= 1.0 ? base : new SpellModifiers.CastModifiers(
			base.powerMultiplier() * strength, base.radiusMultiplier(), base.durationMultiplier() * strength,
			base.defensiveDurationMultiplier() * strength, base.targetRangeBlocks(), base.cooldownTicks(), base.awakened(), base.refineLevel()
		);
		// The piece's own awakened imbue decides; the cast book's flag never reaches an imbued hit.
		modifiers = modifiers.withAwakened(definition.id().equals(piece.get(Awakening.AWAKENED_IMBUE)));
		lastCastPower = modifiers.powerMultiplier();
		Mob target = null;
		Vec3 point = null;
		LivingEntity ally = player;
		switch (definition.targetMode()) {
			case HOSTILE -> target = victim instanceof Mob mob && mob.isAlive() ? mob : null;
			case BLOCK -> point = victim.position();
			// An ally book on a weapon has one ally, the wielder, and it points at that wielder, as the
			// book path does when it finds no other body to mend.
			case SELF, ALLY -> point = player.position();
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
		// An ally book on a weapon has one ally: the wielder. Set here rather than in findAlly, because
		// an imbued hit never walks that path - it hands the effect its target directly - and because the
		// book path's rule, the ally the caster looks at, stays its own.
		CastContext.setAlly(ally);
		CastContext.setImbuedVictim(victim);
		PLAYER_CASTING.set(Boolean.TRUE);
		boolean cast;
		try {
			cast = definition.effect().apply(player, target, point, modifiers);
			// The second landing runs inside the casting flag, so it never rolls a proc of its own.
			if (cast && SpellCasting.doublecasts(player, piece) && SpellCasting.landAgain(player, definition, target, point, modifiers)) {
				SpellCasting.doublecasts++;
			}
		} finally {
			PLAYER_CASTING.remove();
			CastContext.setImbuedVictim(null);
			CastContext.setAlly(null);
		}
		victim.damageCooldownTime = Math.max(victim.damageCooldownTime, hurtCooldown);
		if (!cast) {
			return false;
		}

		int cooldown = proc ? 0 : TowerBridge.cooldown(player, modifiers.cooldownTicks());
		if (!proc) {
			ready.put(definition.id(), now + cooldown);
		}
		if (!proc && showsCooldown(piece)) {
			// A piece imbued before the hotbar showed cooldowns gets its group on its first cast.
			if (!piece.has(DataComponents.USE_COOLDOWN)) {
				piece.set(DataComponents.USE_COOLDOWN, new UseCooldown(COOLDOWN_GROUP_SECONDS, java.util.Optional.of(cooldownGroup(definition.id()))));
			}
			player.getCooldowns().addCooldown(piece, cooldown);
		}
		SpellVisuals.play(player, definition, target, point, departure, modifiers);
		Spellbooks.LOGGER.info(
			"[SPELL] imbued_cast player={} spell={} victim={} piece={} strength={} power={} cooldown={}",
			player.getUUID(), definition.id(), victim.getUUID(), BuiltInRegistries.ITEM.getKey(piece.getItem()), strength,
			modifiers.powerMultiplier(), cooldown
		);
		recorder.cast(player, definition.id(), victim);
		secondRecorder.cast(player, definition.id(), victim);
		for (Recorder extra : EXTRA_RECORDERS) {
			extra.cast(player, definition.id(), victim);
		}
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
