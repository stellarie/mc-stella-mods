package com.stella.rpg;

import com.stella.rpg.job.JobItems;
import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Spell numbers other mods read. {@code mod-stella-spells} finds this class by name, so
 * the class name and every public method signature are a public contract.
 * A damage or heal result is in vanilla units, so the health scale multiplies it once.
 */
public final class RpgSpells {
	private static final double PERCENT = 100.0;

	private RpgSpells() {
	}

	/** The INT spell power curve is retired. MATK replaces it. */
	public static double power(final Player player) {
		return 1.0;
	}

	/** The cooldown multiplier of every skill and spell. INT and DEX both cut it. */
	public static double cooldown(final Player player) {
		return RpgCurve.cooldownMultiplier(StatBonuses.effective(player, Stat.INT) + StatBonuses.effective(player, Stat.DEX))
			* (1.0 - Math.min(0.9, StaffMatk.heldEffect(player, StaffMatk.CDR)));
	}

	/** The spell base at a level, in vanilla units. A hit of 100 percent deals this at 0 MATK percent. */
	public static double base(final int level) {
		RpgConfig.MagicRules rules = RpgConfigStore.current().magic();
		return rules.spellBase() * (1.0 + rules.spellGrowth() * (Math.max(1, level) - 1));
	}

	/** MATK per INT point, before the INT milestone factor. */
	public static final double MATK_PER_INT = 3.0;
	/** Largest player MRES from INT and VIT. */
	public static final double MRES_MAX = 0.6;
	/** MRES sigmoid scale: weighted stat 50 gives about 0.35 MRES. */
	public static final double MRES_SCALE = 37.5;
	public static final double MRES_INT_WEIGHT = 0.8;
	public static final double MRES_VIT_WEIGHT = 0.2;
	/** Largest total MRES, gear and affixes included. */
	public static final double MRES_CAP = 0.8;

	/** The MATK percent bonus as a fraction: the MATK percent affixes plus the refined spell book buff. */
	public static double matkPercent(final LivingEntity entity) {
		return Affixes.total(entity, Affix.MATK_PCT) + BookBuffs.matkBonus(entity);
	}

	/** The flat MATK that INT gives, in K units. */
	public static double matkFromInt(final int intel) {
		return intel * MATK_PER_INT * StatMilestones.factor(intel);
	}

	/** The INT percent a mob spell adds. A mob keeps the old percent model. */
	public static double mobMatkPercent(final LivingEntity entity) {
		int intel = StatBonuses.effective(entity, Stat.INT);
		return intel * RpgConfigStore.current().magic().matkPerInt() * StatMilestones.factor(intel)
			+ Affixes.total(entity, Affix.MATK_PCT);
	}

	/** Flat MATK from gear and the staff, in K units. INT is not included. */
	public static double matkFlat(final LivingEntity entity) {
		return Affixes.matkFlat(entity) + StaffMatk.withEnchants(entity.getMainHandItem());
	}

	/** Total MATK in K units: (INT part + flat gear) x (1 + percent bonus). */
	public static double matk(final LivingEntity entity) {
		double flat = matkFromInt(StatBonuses.effective(entity, Stat.INT)) + matkFlat(entity);
		return flat * (1.0 + matkPercent(entity));
	}

	/** Magic resistance as a fraction. A player uses INT and VIT; a mob keeps the INT curve. */
	public static double resistance(final LivingEntity entity) {
		double affix = Affixes.total(entity, Affix.MAGIC_RES_PCT);
		if (entity instanceof Player) {
			return resistance(StatBonuses.effective(entity, Stat.INT), StatBonuses.effective(entity, Stat.VIT), affix);
		}
		return resistance(StatBonuses.effective(entity, Stat.INT), affix);
	}

	/** The mob curve: INT only. */
	public static double resistance(final int intel, final double affixPercent) {
		double cap = RpgConfigStore.current().magic().resistCap();
		return Math.min(cap, RpgCurve.magicResistance(intel) + affixPercent);
	}

	/** The player curve: MAX x (2 / (1 + exp(-w / S)) - 1) for w = 0.8 INT + 0.2 VIT, plus affixes, at most the cap. */
	public static double resistance(final int intel, final int vit, final double affixPercent) {
		double weighted = MRES_INT_WEIGHT * intel + MRES_VIT_WEIGHT * vit;
		double base = MRES_MAX * (2.0 / (1.0 + Math.exp(-weighted / MRES_SCALE)) - 1.0);
		return Math.min(MRES_CAP, base + affixPercent);
	}

	/** Magic penetration as a fraction, from the attacker's held book. */
	public static double penetration(final LivingEntity attacker) {
		return Affixes.total(attacker, Affix.MAGIC_PEN) + StaffMatk.heldEffect(attacker, StaffMatk.PEN);
	}

	/** Compatibility: the hit of a 100 percent skill, in K units. */
	public static double matkMin(final LivingEntity entity) {
		return spellHit(entity, PERCENT) * RpgScale.k();
	}

	/** The no-crit hit of a skill percent in K units, as a tooltip shows it. */
	public static double matkHit(final LivingEntity entity, final double skillPercent) {
		return spellHit(entity, skillPercent) * RpgScale.k();
	}

	/** Compatibility: equals {@link #matkMin}, because the model has no roll. */
	public static double matkMax(final LivingEntity entity) {
		return matkMin(entity);
	}

	/** A hit that can crit with the LUK chance and the multiplier of a weapon hit, in vanilla units. */
	public static double spellDamage(final LivingEntity caster, final double skillPercent) {
		double hit = spellHit(caster, skillPercent);
		boolean crit = RpgCombat.rollCrit(caster);
		RpgCombat.markSpellCrit(caster, crit);
		return crit ? hit * CritBonuses.critMultiplier(caster) : hit;
	}

	/** MATK times the skill percent (150 is 150%), in vanilla units. No crit. */
	public static double spellHit(final LivingEntity caster, final double skillPercent) {
		return matk(caster) * skillPercent / PERCENT / RpgScale.k();
	}

	/** Compatibility: the random source is unused. The crit uses the combat roller. */
	public static double spellDamage(final LivingEntity caster, final double skillPercent, final RandomSource random) {
		return spellDamage(caster, skillPercent);
	}

	/** Level plus INT plus VIT times the coefficient, in vanilla units. */
	public static double spellHeal(final LivingEntity caster, final double coefficient) {
		int stats = StatBonuses.effective(caster, Stat.INT) + StatBonuses.effective(caster, Stat.VIT);
		return (level(caster) + stats) * coefficient / RpgScale.k() * JobItems.healingFactor(caster);
	}

	/** The spell duration multiplier from INT. {@code mod-stella-spells} reads it by name. */
	public static double durationMultiplier(final LivingEntity caster) {
		return HpModel.durationMultiplier(StatBonuses.effective(caster, Stat.INT));
	}

	private static int level(final LivingEntity caster) {
		if (caster instanceof Player player) {
			return player.experienceLevel;
		}
		return caster instanceof Mob mob ? MobsBridge.threat(mob) : 0;
	}
}
