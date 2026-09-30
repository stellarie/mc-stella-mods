package com.stella.rpg;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
			scaled += roll(attacker, Stat.STR) * charge;
		} else if (source.is(DamageTypeTags.IS_PROJECTILE) && direct instanceof Projectile projectile
			&& projectile.getOwner() == attacker) {
			scaled += roll(attacker, Stat.DEX);
		} else if (attacker instanceof Mob && source.is(DamageTypes.INDIRECT_MAGIC)) {
			return (float) (scaled * RpgCurve.spellPowerMultiplier(StatBonuses.effective(attacker, Stat.INT)));
		} else {
			return amount;
		}
		double chance = RpgCurve.critChance(StatBonuses.effective(attacker, Stat.LUK));
		if (roller.roll(attacker, attacker.getRandom(), chance)) {
			scaled *= RpgCurve.critMultiplier();
		}
		return (float) scaled;
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
		return (float) Math.max(0.0, reduced - DefenseBonuses.flat(entity));
	}
}
