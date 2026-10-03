package com.stella.rpg.job;

/**
 * The numbers of the job items for a refine level. A tier at +7, +10, +12, or +15 replaces or adds to the base,
 * and each higher tier keeps the lower ones.
 */
public final class JobPerks {
	public static final int TIER_1 = 7;
	public static final int TIER_2 = 10;
	public static final int TIER_3 = 12;
	public static final int TIER_4 = 15;

	/** Vanilla draws a bow in 20 ticks. */
	public static final int VANILLA_DRAW_TICKS = 20;

	private JobPerks() {
	}

	public static int bowDrawTicks(final int refine) {
		return refine >= TIER_3 ? 7 : 10;
	}

	public static double bowFreeArrow(final int refine) {
		return refine >= TIER_4 ? 0.50 : refine >= TIER_1 ? 0.35 : 0.25;
	}

	public static int bowExtraPierce(final int refine) {
		return refine >= TIER_2 ? 1 : 0;
	}

	/** Every n-th shot fires 3 arrows. Zero means never. */
	public static int bowTripleEvery(final int refine) {
		return refine >= TIER_4 ? 5 : 0;
	}

	public static double swordArmorPen(final int refine) {
		return refine >= TIER_2 ? 0.20 : 0.10;
	}

	public static double swordBleedChance(final int refine) {
		return refine >= TIER_1 ? 0.15 : 0.10;
	}

	/** The share of current health lost each second. */
	public static double swordBleedShare(final int refine) {
		return refine >= TIER_3 ? 0.07 : 0.05;
	}

	/** The extra damage share against a bleeding target. */
	public static double swordBleedingBonus(final int refine) {
		return refine >= TIER_4 ? 0.20 : 0.0;
	}

	public static double hatSpellDamage(final int refine) {
		return refine >= TIER_4 ? 0.40 : refine >= TIER_1 ? 0.30 : 0.25;
	}

	public static double hatMagicPenetration(final int refine) {
		return refine >= TIER_2 ? 0.10 : 0.0;
	}

	/** The multiplier on spell cooldowns. */
	public static double hatCooldown(final int refine) {
		return refine >= TIER_3 ? 0.90 : 1.0;
	}

	public static double hatSpellCrit(final int refine) {
		return refine >= TIER_4 ? 0.10 : 0.0;
	}

	public static double garbsHealing(final int refine) {
		return refine >= TIER_4 ? 0.75 : refine >= TIER_1 ? 0.60 : 0.50;
	}

	/** The share of damage from undead that the garbs remove. */
	public static double garbsUndeadReduction(final int refine) {
		return refine >= TIER_2 ? 0.30 : 0.20;
	}

	public static boolean garbsCleanse(final int refine) {
		return refine >= TIER_3;
	}

	public static boolean garbsRegeneration(final int refine) {
		return refine >= TIER_4;
	}
}
