package com.stella.spells;

import net.minecraft.world.entity.LivingEntity;

/**
 * The shape of the RPG mod {@code RpgSkills.stat} and {@code AttackPreview} range calls, for the bridge to bind
 * to in a harness that lacks the mod. One class answers for both, so a case names it twice.
 */
public final class RpgAttackStandIn {
	public static final double STR = 10.0;
	public static final double DEX = 20.0;
	/** What a stand-in melee range and ranged range give per point of their stat: low, then high. */
	public static final double MELEE_LOW_PER_STR = 1.0;
	public static final double RANGED_LOW_PER_DEX = 10.0;

	private RpgAttackStandIn() {
	}

	public static double stat(final LivingEntity entity, final String key) {
		return key.equals("dex") ? DEX : key.equals("str") ? STR : 0.0;
	}

	public static double[] range(final LivingEntity player, final int str) {
		return new double[] {str * MELEE_LOW_PER_STR, str * 2.0};
	}

	public static double[] ranged(final LivingEntity player, final int dex) {
		return new double[] {dex * RANGED_LOW_PER_DEX, dex * 20.0};
	}
}
