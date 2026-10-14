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

	/**
	 * MATK percent as a fraction: INT times the share per point, plus the MATK percent affixes, plus the MATK a
	 * refined spell book left on its caster.
	 */
	public static double matkPercent(final LivingEntity entity) {
		return matkPercent(StatBonuses.effective(entity, Stat.INT), Affixes.total(entity, Affix.MATK_PCT)) + BookBuffs.matkBonus(entity);
	}

	public static double matkPercent(final int intel, final double affixPercent) {
		return intel * RpgConfigStore.current().magic().matkPerInt() * StatMilestones.factor(intel) + affixPercent;
	}

	/** Flat MATK from affixes, in K units. */
	public static double matkFlat(final LivingEntity entity) {
		net.minecraft.world.item.ItemStack held = entity.getMainHandItem();
		// Damage enchantments that need no target, such as Sharpness, add to a staff as MATK.
		double enchanted = StaffMatk.isStaff(held) ? AttackPreview.withEnchantments(held, 0.0) * RpgScale.k() : 0.0;
		return Affixes.matkFlat(entity) + StaffMatk.heldMatk(entity) + enchanted;
	}

	/** Magic resistance as a fraction: the INT curve plus the resistance affixes, at most the cap. */
	public static double resistance(final LivingEntity entity) {
		return resistance(StatBonuses.effective(entity, Stat.INT), Affixes.total(entity, Affix.MAGIC_RES_PCT));
	}

	public static double resistance(final int intel, final double affixPercent) {
		double cap = RpgConfigStore.current().magic().resistCap();
		return Math.min(cap, RpgCurve.magicResistance(intel) + affixPercent);
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

	/** The skill percent (150 is 150%) of the base, times MATK percent, plus flat MATK. No crit. */
	public static double spellHit(final LivingEntity caster, final double skillPercent) {
		double scaled = base(level(caster)) * skillPercent / PERCENT * (1.0 + matkPercent(caster));
		return scaled + matkFlat(caster) / RpgScale.k();
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
