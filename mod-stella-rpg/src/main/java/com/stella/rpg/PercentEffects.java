package com.stella.rpg;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Status effects that scale with the target's max health. Every result is in vanilla units.
 * The heal and damage paths multiply it by K, so a floor of 1 vanilla point is K health.
 */
public final class PercentEffects {
	public static final double TICK_SHARE = 0.03;
	public static final double HEAL_SHARE = 0.15;
	public static final double HARM_SHARE = 0.20;
	public static final double ABSORPTION_SHARE = 0.10;
	public static final double TICK_FLOOR = 1.0;
	public static final double HEAL_FLOOR = 4.0;
	public static final double HARM_FLOOR = 6.0;
	public static final double ABSORPTION_FLOOR = 4.0;
	public static final double ELITE_SHARE = 0.25;

	/** True while a percent hit is applied; the attacker's rpg scaling skips it. */
	private static final ThreadLocal<Boolean> FIXED = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private PercentEffects() {
	}

	/** Runs a percent hit; it already scales with the victim's max health. */
	public static boolean fixedHit(final java.util.function.BooleanSupplier hit) {
		boolean outer = FIXED.get();
		FIXED.set(Boolean.TRUE);
		try {
			return hit.getAsBoolean();
		} finally {
			FIXED.set(outer);
		}
	}

	public static boolean inFixedHit() {
		return FIXED.get();
	}

	/** Poison, Wither, and Regeneration, per tick. */
	public static float tick(final LivingEntity entity) {
		return amount(entity, TICK_FLOOR, TICK_SHARE);
	}

	/** A tick that never takes the target below 1 health. */
	public static float survivableTick(final LivingEntity entity) {
		float room = Math.max(0.0F, (entity.getHealth() - 1.0F) / RpgScale.kf());
		return Math.min(tick(entity), room);
	}

	public static float instantHeal(final LivingEntity entity, final int amplifier) {
		return amount(entity, HEAL_FLOOR, HEAL_SHARE) * (1 << amplifier);
	}

	public static float instantHarm(final LivingEntity entity, final int amplifier) {
		return amount(entity, HARM_FLOOR, HARM_SHARE) * (1 << amplifier);
	}

	public static float absorption(final LivingEntity entity, final int amplifier) {
		return amount(entity, ABSORPTION_FLOOR, ABSORPTION_SHARE) * (1 + amplifier);
	}

	static double eliteShare(final LivingEntity entity) {
		return entity instanceof Mob mob && MobsBridge.eliteOrBoss(mob) ? ELITE_SHARE : 1.0;
	}

	private static float amount(final LivingEntity entity, final double floor, final double share) {
		double percent = share * eliteShare(entity) * entity.getMaxHealth() / RpgScale.k();
		return (float) Math.max(floor, percent);
	}
}
