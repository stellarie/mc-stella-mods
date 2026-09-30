package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Spell numbers other mods read. {@code mod-stella-spells} finds this class by name, so
 * the class name and every public method signature are a public contract.
 * MATK is in K units. A damage or heal result is in vanilla units, so the health scale multiplies it once.
 */
public final class RpgSpells {
	public static final int MIN_DIVISOR = 7;
	public static final int MAX_DIVISOR = 5;
	private static final double PERCENT = 100.0;

	private RpgSpells() {
	}

	/** The INT spell power curve is retired. MATK replaces it. */
	public static double power(final Player player) {
		return 1.0;
	}

	public static double cooldown(final Player player) {
		return RpgCurve.cooldownMultiplier(StatBonuses.effective(player, Stat.INT));
	}

	public static double matkMin(final LivingEntity entity) {
		return matk(entity, MIN_DIVISOR);
	}

	public static double matkMax(final LivingEntity entity) {
		return matk(entity, MAX_DIVISOR);
	}

	/** MATK for a given INT, flat bonus, and percent bonus. */
	public static double matk(final int intel, final int divisor, final double flat, final double percent) {
		int step = intel / divisor;
		return (intel + step * step + flat) * (1.0 + percent);
	}

	/** A roll between the MATK minimum and maximum, times the skill percent (150 is 150%), in vanilla units. */
	public static double spellDamage(final LivingEntity caster, final double skillPercent) {
		return spellDamage(caster, skillPercent, caster.getRandom());
	}

	/** The roll is the MATK maximum with the INT chance, and uniform between the minimum and maximum otherwise. */
	public static double spellDamage(final LivingEntity caster, final double skillPercent, final RandomSource random) {
		double min = matkMin(caster);
		double max = matkMax(caster);
		double chance = RpgCurve.intMaxRollChance(StatBonuses.effective(caster, Stat.INT));
		double roll = chance > 0.0 && random.nextDouble() < chance ? max : min + random.nextDouble() * (max - min);
		return roll * skillPercent / PERCENT / RpgScale.k();
	}

	/** Level plus INT times the coefficient, in vanilla units. */
	public static double spellHeal(final LivingEntity caster, final double coefficient) {
		return (level(caster) + StatBonuses.effective(caster, Stat.INT)) * coefficient / RpgScale.k();
	}

	private static double matk(final LivingEntity entity, final int divisor) {
		return matk(
			StatBonuses.effective(entity, Stat.INT), divisor, Affixes.matkFlat(entity), Affixes.total(entity, Affix.MATK_PCT)
		);
	}

	private static int level(final LivingEntity caster) {
		if (caster instanceof Player player) {
			return player.experienceLevel;
		}
		return caster instanceof Mob mob ? MobsBridge.threat(mob) : 0;
	}
}
