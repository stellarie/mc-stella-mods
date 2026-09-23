package com.stella.mobs;

import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.scaling.SpawnScaling;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * R12: a mob's health and damage scale with its distance from the world spawn and, more slowly, with
 * its depth. The curve is pure arithmetic, so the first test needs no world, and the second test
 * recomputes the expected amount from the spawn's own absolute coordinates.
 */
public class SpawnScalingGameTest {
	private static final double EPSILON = 1.0E-6;
	// No gear and no elite, so this test measures the scaling path alone.
	private static final String PLAIN_CONFIG = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	@GameTest
	public void scalingCurveHitsItsCaps(final GameTestHelper helper) {
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.healthMultiplier(0.0, 0.0)), Double.valueOf(1.0), "health multiplier at the spawn point"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.healthMultiplier(1024.0, 0.0)), Double.valueOf(1.5), "health multiplier at 1024 blocks out"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.healthMultiplier(0.0, 256.0)), Double.valueOf(1.25), "health multiplier at 256 blocks deep"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.healthMultiplier(4096.0, 4096.0)), Double.valueOf(1.75), "health multiplier past both caps"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.damageMultiplier(1024.0, 0.0)), Double.valueOf(1.25), "damage multiplier at 1024 blocks out"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.damageMultiplier(0.0, 256.0)), Double.valueOf(1.125), "damage multiplier at 256 blocks deep"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.damageMultiplier(4096.0, 4096.0)), Double.valueOf(1.375), "damage multiplier past both caps"
		);
		result(
			"scalingCurveHitsItsCaps",
			"healthAtSpawn=" + SpawnScaling.healthMultiplier(0.0, 0.0)
				+ " healthAtDistanceCap=" + SpawnScaling.healthMultiplier(1024.0, 0.0)
				+ " healthAtDepthCap=" + SpawnScaling.healthMultiplier(0.0, 256.0)
				+ " healthPastBoth=" + SpawnScaling.healthMultiplier(4096.0, 4096.0)
				+ " damagePastBoth=" + SpawnScaling.damageMultiplier(4096.0, 4096.0)
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void farMobSpawnsScaled(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(PLAIN_CONFIG));

		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		double x = zombie.getX();
		double y = zombie.getY();
		double z = zombie.getZ();
		double distance = Math.hypot(x - helper.getLevel().getRespawnData().pos().getX(), z - helper.getLevel().getRespawnData().pos().getZ());
		double depth = Math.max(0.0, helper.getLevel().getSeaLevel() - y);
		double expected = SpawnScaling.healthMultiplier(distance, depth);

		AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
		helper.assertTrue(health != null, "the zombie has no MAX_HEALTH attribute");
		AttributeModifier modifier = health.getModifier(SpawnScaling.HEALTH_ID);
		helper.assertTrue(modifier != null, "no spawn scaling modifier was applied");
		helper.assertTrue(
			Math.abs(modifier.amount() - (expected - 1.0)) < EPSILON,
			"the scaling amount " + modifier.amount() + " is not the expected " + (expected - 1.0)
				+ " at distance " + distance + " and depth " + depth
		);
		helper.assertValueEqual(
			Float.valueOf(zombie.getHealth()), Float.valueOf(zombie.getMaxHealth()), "the scaled mob healed to its new maximum"
		);
		result(
			"farMobSpawnsScaled",
			"dist=" + distance + " depth=" + depth + " healthMul=" + expected + " amount=" + modifier.amount()
				+ " hp=" + zombie.getHealth() + "/" + zombie.getMaxHealth()
		);
		helper.succeed();
	}

	@GameTest
	public void diskLoadDoesNotScaleTwice(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(PLAIN_CONFIG));

		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		// The spawn path scaled the mob already, so the claim is idempotence, not an empty start.
		int atSpawn = countScalingModifiers(mob);
		double healthAtSpawn = healthAmount(mob);
		double damageAtSpawn = damageAmount(mob);
		float maxAtSpawn = mob.getMaxHealth();

		SpawnScaling.apply(mob);
		SpawnScaling.apply(mob);

		helper.assertValueEqual(Integer.valueOf(countScalingModifiers(mob)), Integer.valueOf(atSpawn), "scaling modifier count after two more applications");
		helper.assertTrue(
			Math.abs(healthAmount(mob) - healthAtSpawn) < EPSILON,
			"the health scaling amount moved from " + healthAtSpawn + " to " + healthAmount(mob)
		);
		helper.assertTrue(
			Math.abs(damageAmount(mob) - damageAtSpawn) < EPSILON,
			"the damage scaling amount moved from " + damageAtSpawn + " to " + damageAmount(mob)
		);
		helper.assertValueEqual(Float.valueOf(mob.getMaxHealth()), Float.valueOf(maxAtSpawn), "the maximum health after two more applications");
		result(
			"diskLoadDoesNotScaleTwice",
			"modifiersAtSpawn=" + atSpawn + " modifiersAfter=" + countScalingModifiers(mob)
				+ " healthAmount=" + healthAtSpawn + " damageAmount=" + damageAtSpawn + " max=" + mob.getMaxHealth()
		);
		helper.succeed();
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}

	private static double healthAmount(final Mob mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		AttributeModifier modifier = health == null ? null : health.getModifier(SpawnScaling.HEALTH_ID);
		return modifier == null ? 0.0 : modifier.amount();
	}

	private static double damageAmount(final Mob mob) {
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		AttributeModifier modifier = damage == null ? null : damage.getModifier(SpawnScaling.DAMAGE_ID);
		return modifier == null ? 0.0 : modifier.amount();
	}

	private static int countScalingModifiers(final Mob mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		int count = 0;

		if (health != null && health.getModifier(SpawnScaling.HEALTH_ID) != null) {
			count++;
		}

		if (damage != null && damage.getModifier(SpawnScaling.DAMAGE_ID) != null) {
			count++;
		}

		return count;
	}
}
