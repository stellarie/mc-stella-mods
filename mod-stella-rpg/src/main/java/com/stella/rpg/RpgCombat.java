package com.stella.rpg;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

public final class RpgCombat {
	@FunctionalInterface
	public interface Roller {
		boolean roll(LivingEntity attacker, RandomSource random, double chance);
	}

	private static final Roller FAIR = (attacker, random, chance) -> chance > 0.0 && random.nextDouble() < chance;
	private static volatile Roller roller = FAIR;

	private RpgCombat() {
	}

	public static void setRoller(final Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static float scaleOutgoing(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!(source.getEntity() instanceof ServerPlayer attacker)) {
			return amount;
		}
		double scaled = amount;
		Entity direct = source.getDirectEntity();
		if (direct == attacker) {
			scaled *= RpgCurve.meleeMultiplier(StatBonuses.effective(attacker, Stat.STR));
		} else if (direct instanceof Projectile projectile && projectile.getOwner() == attacker) {
			scaled *= RpgCurve.rangedMultiplier(StatBonuses.effective(attacker, Stat.DEX));
		} else {
			return amount;
		}
		double chance = RpgCurve.critChance(StatBonuses.effective(attacker, Stat.LUK));
		if (roller.roll(attacker, attacker.getRandom(), chance)) {
			scaled *= RpgCurve.critMultiplier();
		}
		return (float) scaled;
	}

	public static float scaleIncoming(final LivingEntity entity, final DamageSource source, final float damage) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		return (float) (damage * RpgCurve.damageTakenMultiplier(StatBonuses.effective(player, Stat.VIT)));
	}
}
