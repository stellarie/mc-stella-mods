package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.PiglinSmash;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * R24, the elite piglin brute: a smash of about six times vanilla's shove, along the line from the brute to
 * the victim, with the grounded pop capped at 0.4, and thirty percent more movement as an attribute. The
 * vanilla baseline the ratio is read against is vanilla's own knockback call at vanilla's own power, so the
 * test measures the engine rather than a number written into the fixture.
 */
public final class PiglinBruteGameTest {
	private static final String TEST = "bruteSmash";
	private static final double EPSILON = 1.0e-4;
	private static final double RATIO_TOLERANCE = 0.05;
	private static final String NO_ELITE = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"entities": { "minecraft:piglin_brute": { "elite_chance": 0.0 } }
			}
		}""";
	/** The brute's arrival pinned on, so the speed clause is proven through the load path it ships on. */
	private static final String ALWAYS_ELITE_BRUTE = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"entities": { "minecraft:piglin_brute": { "elite_chance": 1.0 } }
			}
		}""";

	/** G08's shape for R24: one clause per test, and every count by relationship. */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void anEliteBrutesHitThrowsTheVictimAboutSixTimesAsFar(final GameTestHelper helper) {
		prepare(helper);
		PiglinBrute brute = helper.spawnMob(EntityTypes.PIGLIN_BRUTE, 3, 1, 3).spawn();
		Zombie victim = helper.spawnMob(EntityTypes.ZOMBIE, 3, 1, 8).withNoFreeWill().spawn();
		victim.setPersistenceRequired();
		brute.setPersistenceRequired();

		// A few ticks first, so the victim has landed: the grounded pop rule cannot apply in mid air.
		helper.runAfterDelay(5L, () -> {
			// The vanilla baseline, measured on this same victim with vanilla's own call at vanilla's power.
			Vec3 baseline = pushOf(helper, victim, PiglinSmash.VANILLA_PUSH, brute, false);
			Elite.mark(brute, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			PiglinSmash.install(brute);
			Vec3 smashed = pushOf(helper, victim, PiglinSmash.SMASH_POWER, brute, true);
			double baselineFlat = flat(baseline);
			double smashedFlat = flat(smashed);
			// A real hit carries vanilla's own shove as well as the smash, and the smash halves what was
			// already moving, so the clause is read as the whole push against the vanilla push.
			double added = smashedFlat - baselineFlat;
			double ratio = baselineFlat == 0.0 ? 0.0 : smashedFlat / baselineFlat;

			result(
				"smashRatio",
				"vanillaFlat=" + baselineFlat + " smashedFlat=" + smashedFlat + " added=" + added
					+ " totalRatio=" + ratio
					+ " multiplier=" + PiglinSmash.SMASH_MULTIPLIER + " pop=" + smashed.y
					+ " ceiling=" + PiglinSmash.POP_CEILING + " grounded=" + victim.onGround()
			);
			helper.assertTrue(baselineFlat > 0.0, "the vanilla baseline shove measured zero");
			helper.assertTrue(
				Math.abs(ratio - PiglinSmash.SMASH_MULTIPLIER) < PiglinSmash.SMASH_MULTIPLIER * RATIO_TOLERANCE,
				"the smash pushed " + ratio + " times the vanilla shove, not " + PiglinSmash.SMASH_MULTIPLIER
			);
			helper.assertTrue(
				smashed.y <= PiglinSmash.POP_CEILING + EPSILON,
				"the smash launched the victim " + smashed.y + " upward, past the ceiling " + PiglinSmash.POP_CEILING
			);
			helper.assertTrue(smashed.y > 0.0, "the smash did not pop the victim off the ground");
			helper.succeed();
		});
	}

	/** The direction clause: the victim leaves along the line from the brute to the victim. */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void theSmashPushesAlongTheLineFromTheBruteToTheVictim(final GameTestHelper helper) {
		prepare(helper);
		PiglinBrute brute = helper.spawnMob(EntityTypes.PIGLIN_BRUTE, 3, 3, 3).spawn();
		Zombie victim = helper.spawnMob(EntityTypes.ZOMBIE, 3, 3, 8).withNoFreeWill().spawn();
		brute.setPersistenceRequired();
		victim.setPersistenceRequired();
		Elite.mark(brute, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);

		double alongBrute = brute.getX() - victim.getX();
		double alongVictim = brute.getZ() - victim.getZ();
		Vec3 pushed = pushOf(helper, victim, PiglinSmash.SMASH_POWER, brute, true);
		// The push and the line from the brute to the victim must point the same way.
		double dot = pushed.x * -alongBrute + pushed.z * -alongVictim;
		double cross = Math.abs(pushed.x * -alongVictim - pushed.z * -alongBrute);

		result(
			"smashDirection",
			"pushed=" + pushed + " dot=" + dot + " cross=" + cross + " line=" + alongBrute + "," + alongVictim
		);
		helper.assertTrue(dot > 0.0, "the smash pushed the victim back toward the brute");
		helper.assertTrue(cross < 1.0e-9, "the smash pushed across the line rather than along it");
		helper.succeed();
	}

	/** The movement clause: thirty percent of base, permanent, installed once. */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void anEliteBruteMovesThirtyPercentFasterAndNeverTwice(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		// The arrival is pinned on and the brute is spawned through the load path, so this proves the wiring
		// in BehaviorInstaller rather than a direct call: the mod marks it and adds the movement itself.
		GearConfigStore.set(GearConfigParser.parse(ALWAYS_ELITE_BRUTE));
		PiglinBrute brute = helper.spawnMob(EntityTypes.PIGLIN_BRUTE, 3, 1, 3).spawn();
		brute.setPersistenceRequired();
		AttributeInstance instance = brute.getAttribute(Attributes.MOVEMENT_SPEED);
		double base = instance.getBaseValue();
		double once = brute.getAttributeValue(Attributes.MOVEMENT_SPEED);
		PiglinSmash.install(brute);
		double twice = brute.getAttributeValue(Attributes.MOVEMENT_SPEED);
		int modifiers = instance.hasModifier(PiglinSmash.SPEED_ID) ? 1 : 0;

		result(
			"bruteSpeed",
			"arrivedElite=" + Elite.isElite(brute) + " base=" + base + " elite=" + once
				+ " afterSecondInstall=" + twice + " ownedModifiers=" + modifiers
				+ " bonus=" + PiglinSmash.SPEED_BONUS
		);
		helper.assertTrue(Elite.isElite(brute), "the pinned chance did not make the spawned brute elite");
		helper.assertTrue(
			Math.abs(once - base * (1.0 + PiglinSmash.SPEED_BONUS)) < EPSILON,
			"the elite brute's speed is " + once + " where thirty percent over its base " + base + " is expected"
		);
		helper.assertValueEqual(
			Double.valueOf(twice), Double.valueOf(once), "the speed after a second install"
		);
		helper.assertValueEqual(Integer.valueOf(modifiers), Integer.valueOf(1), "speed modifiers owned");
		helper.succeed();
	}

	private static void prepare(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(NO_ELITE));
		Arena.buildFloor(helper);
	}

	/**
	 * The vanilla baseline is vanilla's own knockback call at vanilla's own power, measured on this same
	 * victim; the subject's push is a real hit through the brute's own attack, so the hook under test runs.
	 * The victim is reset between the two so the second push is read from rest.
	 */
	private static Vec3 pushOf(
		final GameTestHelper helper, final Zombie victim, final double power, final PiglinBrute brute,
		final boolean realHit
	) {
		victim.setHealth(victim.getMaxHealth());
		victim.setDeltaMovement(Vec3.ZERO);

		if (realHit) {
			brute.doHurtTarget(helper.getLevel(), victim);
		} else {
			victim.knockback(
				power, brute.getX() - victim.getX(), brute.getZ() - victim.getZ(),
				brute.damageSources().mobAttack(brute), 1.0F
			);
		}

		return victim.getDeltaMovement();
	}

	private static double flat(final Vec3 movement) {
		return Math.sqrt(movement.x * movement.x + movement.z * movement.z);
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {}", TEST, test, detail);
	}
}
