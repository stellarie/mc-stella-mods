package com.stella.mobs.behavior;

import com.stella.mobs.gear.GearTier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * The elite witch: 1.5 times health from the mob's own base, a 16 block weakness aura, and the shared
 * marker. She reads and writes the one elite answer, so the framework and the witch cannot disagree.
 */
public final class WitchElite {
	public static final double HEALTH_MULTIPLIER = 1.5;
	public static final double DEFAULT_CHANCE = 0.10;
	public static final double AURA_RADIUS = EliteAuraGoal.RADIUS;
	/** An elite or boss witch never blinds, so her aura weakens instead. */
	public static final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> AURA_EFFECT = net.minecraft.world.effect.MobEffects.WEAKNESS;
	public static final int AURA_DURATION_TICKS = EliteAuraGoal.DURATION_TICKS;
	public static final int AURA_INTERVAL_TICKS = EliteAuraGoal.INTERVAL_TICKS;

	private WitchElite() {
	}

	public static boolean roll(final RandomSource random, final double chance) {
		return random.nextFloat() < (float) chance;
	}

	public static double eliteHealth(final double baseHealth) {
		return baseHealth * HEALTH_MULTIPLIER;
	}

	/** Marks the witch elite through the shared marker, then applies her own health multiplier. */
	public static void mark(final Mob witch, final GearTier tier) {
		Elite.mark(witch, tier, HEALTH_MULTIPLIER);
	}

	public static boolean isElite(final Mob witch) {
		return Elite.isElite(witch);
	}
}
