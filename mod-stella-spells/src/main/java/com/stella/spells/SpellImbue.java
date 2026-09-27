package com.stella.spells;

import com.mojang.serialization.Codec;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.phys.Vec3;

/** A weapon imbued at an anvil with a spellbook casts that spell, weaker, when it lands a melee hit. */
public final class SpellImbue {
	/** The anvil's price in levels; with the equips mod's anvil it reads as emeralds. */
	public static final int IMBUE_XP_COST = 5;
	public static final double IMBUE_POWER = 0.5;
	public static final double IMBUE_DURATION = 0.5;
	/** How far behind its target an imbued Riftwalk sets the wielder down. */
	static final double RIFTWALK_STEP_BEHIND = 0.8;

	public record ImbuedSpell(Identifier spell) implements TooltipProvider {
		public static final Codec<ImbuedSpell> CODEC = Identifier.CODEC.xmap(ImbuedSpell::new, ImbuedSpell::spell);

		@Override
		public void addToTooltip(final Item.TooltipContext context, final Consumer<Component> lines, final TooltipFlag flag, final DataComponentGetter components) {
			lines.accept(Component.translatable(
				"spellbook." + Spellbooks.MOD_ID + ".imbued",
				Component.translatable("item." + this.spell.getNamespace() + "." + this.spell.getPath())
			).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	public static final DataComponentType<ImbuedSpell> IMBUED = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "imbued_spell"),
		DataComponentType.<ImbuedSpell>builder()
			.persistent(ImbuedSpell.CODEC)
			.networkSynchronized(Identifier.STREAM_CODEC.map(ImbuedSpell::new, ImbuedSpell::spell))
			.build()
	);

	public interface Recorder {
		void cast(ServerPlayer player, Identifier spell, LivingEntity victim);
	}

	private static final Recorder SILENT = (player, spell, victim) -> {
	};
	private static volatile Recorder recorder = SILENT;
	/** Server tick at which each player's imbued spell is ready again. */
	private static final Map<UUID, Map<Identifier, Integer>> READY_AT = new ConcurrentHashMap<>();

	private SpellImbue() {
	}

	public static void register() {
		ItemComponentTooltipProviderRegistry.addAfter(DataComponents.LORE, IMBUED);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SpellImbue::afterDamage);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	// A weapon is anything whose own attack damage the game adds to the hand holding it.
	public static boolean canImbue(final ItemStack stack) {
		return !stack.isEmpty() && stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID));
	}

	/** The weapon with the book's spell and nothing else of the book; empty when it already holds that spell. */
	public static ItemStack imbue(final ItemStack weapon, final SpellDefinition spell) {
		ImbuedSpell current = weapon.get(IMBUED);
		if (current != null && current.spell().equals(spell.id())) {
			return ItemStack.EMPTY;
		}

		ItemStack result = weapon.copy();
		result.set(IMBUED, new ImbuedSpell(spell.id()));
		return result;
	}

	// Only a melee hit from the wielder's own hand casts, so an arrow or a spell's own damage never does.
	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked
	) {
		if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || blocked || victim == player) {
			return;
		}

		ImbuedSpell imbued = player.getMainHandItem().get(IMBUED);
		SpellDefinition definition = imbued == null ? null : SpellbookCatalog.get(imbued.spell());
		if (definition == null) {
			return;
		}

		int now = player.level().getServer().getTickCount();
		Map<Identifier, Integer> ready = READY_AT.computeIfAbsent(player.getUUID(), key -> new ConcurrentHashMap<>());
		if (now < ready.getOrDefault(definition.id(), Integer.MIN_VALUE)) {
			return;
		}

		SpellModifiers.CastModifiers modifiers = new SpellModifiers.CastModifiers(
			IMBUE_POWER, 1.0, IMBUE_DURATION, IMBUE_DURATION, SpellModifiers.BASE_TARGET_RANGE_BLOCKS, definition.baseCooldownTicks()
		);
		Mob target = null;
		Vec3 point = null;
		switch (definition.targetMode()) {
			case HOSTILE -> target = victim instanceof Mob mob && mob.isAlive() ? mob : null;
			case BLOCK -> point = victim.position();
			case SELF -> point = player.position();
			case SAFE_TELEPORT -> point = behind(player, victim);
		}
		if ((definition.targetMode() == TargetMode.HOSTILE && target == null) || (definition.targetMode() != TargetMode.HOSTILE && point == null)) {
			return;
		}

		// The melee hit just started the victim's hurt cooldown, which would swallow the spell's own damage.
		victim.damageCooldownTime = 0;
		Vec3 departure = player.position();
		if (!definition.effect().apply(player, target, point, modifiers)) {
			return;
		}

		ready.put(definition.id(), now + definition.baseCooldownTicks());
		SpellVisuals.play(player, definition, target, point, departure, modifiers);
		Spellbooks.LOGGER.info(
			"[SPELL] imbued_cast player={} spell={} victim={} power={} cooldown={}",
			player.getUUID(), definition.id(), victim.getUUID(), IMBUE_POWER, definition.baseCooldownTicks()
		);
		recorder.cast(player, definition.id(), victim);
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
