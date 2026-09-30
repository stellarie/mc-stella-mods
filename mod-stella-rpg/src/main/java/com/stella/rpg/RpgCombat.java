package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

public final class RpgCombat {
	@FunctionalInterface
	public interface Roller {
		boolean roll(LivingEntity attacker, RandomSource random, double chance);
	}

	@FunctionalInterface
	public interface DiceRoller {
		int roll(LivingEntity attacker, RandomSource random, RpgDice.Dice dice);
	}

	private static final int STATUS_STEP = 10;
	private static final double EFFECT_PERCENT = 0.10;
	private static final double MOB_SPELL_PERCENT = 100.0;
	private static final double MOB_SPELL_BASE = 3.0;
	private static final DiceRoller FAIR_DICE = (attacker, random, dice) -> dice.roll(random);
	private static volatile DiceRoller diceRoller = FAIR_DICE;
	private static Player swinger;
	private static float swingStrength = 1.0F;

	private static final Roller FAIR = (attacker, random, chance) -> chance > 0.0 && random.nextDouble() < chance;
	private static volatile Roller roller = FAIR;

	private RpgCombat() {
	}

	public static void setRoller(final Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static void setDiceRoller(final DiceRoller newRoller) {
		diceRoller = newRoller == null ? FAIR_DICE : newRoller;
	}

	public static void beginAttack(final Player player, final float strength) {
		swinger = player;
		swingStrength = strength;
	}

	public static void endAttack(final Player player) {
		if (swinger == player) {
			swinger = null;
			swingStrength = 1.0F;
		}
	}

	public static double chargeFactor(final float strength) {
		return 0.2 + strength * strength * 0.8;
	}

	public static float scaleOutgoing(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!(source.getEntity() instanceof LivingEntity attacker)
			|| !(attacker instanceof ServerPlayer || attacker instanceof Mob)) {
			return amount;
		}
		double scaled = amount;
		Entity direct = source.getDirectEntity();
		if (isMelee(source)) {
			double charge = attacker instanceof Player player && swinger == player ? chargeFactor(swingStrength) : 1.0;
			scaled += roll(attacker, Stat.STR) * charge + statusAtk(StatBonuses.effective(attacker, Stat.STR));
		} else if (source.is(DamageTypeTags.IS_PROJECTILE) && direct instanceof Projectile projectile
			&& projectile.getOwner() == attacker) {
			scaled += roll(attacker, Stat.DEX) + statusAtk(StatBonuses.effective(attacker, Stat.DEX));
		} else if (attacker instanceof Mob && source.is(DamageTypes.INDIRECT_MAGIC)) {
			return (float) RpgSpells.spellDamage(attacker, MOB_SPELL_PERCENT * amount / MOB_SPELL_BASE);
		} else {
			return amount;
		}
		scaled += Affixes.total(attacker, Affix.DAMAGE_FLAT) / RpgScale.k();
		scaled *= damageFactor(attacker);
		double chance = RpgCurve.critChance(StatBonuses.effective(attacker, Stat.LUK));
		if (roller.roll(attacker, attacker.getRandom(), chance)) {
			scaled *= RpgCurve.critMultiplier();
		}
		return (float) scaled;
	}

	/** Ragnarok status ATK, in vanilla units: the stat plus the square of a tenth of it, divided by K. */
	public static double statusAtk(final int stat) {
		int step = stat / STATUS_STEP;
		return (stat + step * step) / RpgScale.k();
	}

	/** Damage affixes and Strength add. Weakness subtracts. The result is never below 0. */
	public static double damageFactor(final LivingEntity attacker) {
		double percent = Affixes.total(attacker, Affix.DAMAGE_PCT) + EFFECT_PERCENT * (level(attacker, MobEffects.STRENGTH)
			- level(attacker, MobEffects.WEAKNESS));
		return Math.max(0.0, 1.0 + percent);
	}

	private static int level(final LivingEntity entity, final Holder<MobEffect> effect) {
		MobEffectInstance instance = entity.getEffect(effect);
		return instance == null ? 0 : instance.getAmplifier() + 1;
	}

	private static boolean isMelee(final DamageSource source) {
		return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
			|| source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
	}

	private static int roll(final LivingEntity attacker, final Stat stat) {
		RpgDice.Dice dice = RpgDice.of(StatBonuses.effective(attacker, stat));
		return diceRoller.roll(attacker, attacker.getRandom(), dice);
	}

	public static float scaleIncoming(final LivingEntity entity, final DamageSource source, final float damage) {
		if (!(entity instanceof ServerPlayer || entity instanceof Mob)
			|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		double reduced = damage * RpgCurve.damageTakenMultiplier(StatBonuses.effective(entity, Stat.VIT));
		reduced *= 1.0 - Math.min(1.0, Affixes.total(entity, Affix.REDUCTION_PCT));
		double flat = DefenseBonuses.flat(entity) + Affixes.total(entity, Affix.REDUCTION_FLAT) / RpgScale.k();
		return (float) Math.max(0.0, reduced - flat);
	}
}
