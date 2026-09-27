package com.stella.equips.enchant;

import com.stella.equips.StellaEquips;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The chestplate auras. Mist Armor wraps its wearer in a freezing mist that chills and slows the enemies
 * close around; Blaze Armor burns them for a heart a second, now and then sets one alight, and keeps its
 * wearer fire resistant, as a blaze's own heat does. Both pulse once a second for every player wearing one.
 */
public final class Auras {
	public static final Identifier MIST_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "armor/mist_armor");
	public static final Identifier BLAZE_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "armor/blaze_armor");
	public static final int PULSE_TICKS = 20;
	public static final double MIST_RADIUS = 4.0;
	/** Freeze damage each pulse, per level. */
	public static final float MIST_DAMAGE_PER_LEVEL = 1.0F;
	public static final int MIST_SLOW_TICKS = 30;
	/** How much colder each pulse leaves a foe, in frozen ticks, so the frost shows on it. */
	public static final int MIST_FROST_TICKS = 40;
	public static final double BLAZE_RADIUS = 3.0;
	/** One heart a second, at every level. */
	public static final float BLAZE_DAMAGE = 2.0F;
	/** The chance, per level, that a pulse sets a foe alight. */
	public static final float BLAZE_IGNITE_CHANCE_PER_LEVEL = 0.1F;
	public static final int BLAZE_IGNITE_TICKS = 60;
	/** Fire Resistance, refreshed each pulse, lasting past the next one. */
	public static final int BLAZE_FIRE_RESISTANCE_TICKS = 60;

	private Auras() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Auras::tick);
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % PULSE_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !player.isSpectator()) {
				pulse(player);
			}
		}
	}

	/** One pulse of whatever auras a wearer's chestplate carries. */
	public static void pulse(final LivingEntity wearer) {
		if (!(wearer.level() instanceof ServerLevel level)) {
			return;
		}
		int mist = level(level, wearer, MIST_ID);
		if (mist > 0) {
			mist(level, wearer, mist);
		}
		int blaze = level(level, wearer, BLAZE_ID);
		if (blaze > 0) {
			blaze(level, wearer, blaze);
		}
	}

	private static void mist(final ServerLevel level, final LivingEntity wearer, final int rank) {
		DamageSource frost = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.FREEZE), null, wearer);
		for (LivingEntity foe : foes(level, wearer, MIST_RADIUS)) {
			foe.setTicksFrozen(Math.max(foe.getTicksFrozen(), foe.getTicksRequiredToFreeze()) + MIST_FROST_TICKS);
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MIST_SLOW_TICKS, rank - 1), wearer);
			foe.hurtServer(level, frost, MIST_DAMAGE_PER_LEVEL * rank);
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, wearer.getX(), wearer.getY(0.5), wearer.getZ(), 24, MIST_RADIUS * 0.5, 0.6, MIST_RADIUS * 0.5, 0.01);
	}

	private static void blaze(final ServerLevel level, final LivingEntity wearer, final int rank) {
		wearer.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, BLAZE_FIRE_RESISTANCE_TICKS, 0, true, false, true));
		DamageSource heat = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.IN_FIRE), null, wearer);
		for (LivingEntity foe : foes(level, wearer, BLAZE_RADIUS)) {
			foe.hurtServer(level, heat, BLAZE_DAMAGE);
			if (wearer.getRandom().nextFloat() < BLAZE_IGNITE_CHANCE_PER_LEVEL * rank) {
				foe.igniteForTicks(BLAZE_IGNITE_TICKS);
			}
		}
		level.sendParticles(ParticleTypes.FLAME, wearer.getX(), wearer.getY(0.5), wearer.getZ(), 16, BLAZE_RADIUS * 0.5, 0.6, BLAZE_RADIUS * 0.5, 0.01);
	}

	private static List<LivingEntity> foes(final ServerLevel level, final LivingEntity wearer, final double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, wearer.getBoundingBox().inflate(radius),
			other -> Foes.isFoe(wearer, other) && other.distanceToSqr(wearer) <= radius * radius);
	}

	private static int level(final ServerLevel level, final LivingEntity wearer, final Identifier id) {
		Optional<Holder.Reference<Enchantment>> found = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(id);
		return found.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, wearer.getItemBySlot(EquipmentSlot.CHEST))).orElse(0);
	}
}
