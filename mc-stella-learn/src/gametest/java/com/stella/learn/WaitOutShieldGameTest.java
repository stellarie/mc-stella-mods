package com.stella.learn;

import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** Mobs wait out the raised shield of a player who turtles, and only that player's. */
public class WaitOutShieldGameTest {
	private static final int MAX_TICKS = 400;
	private static final double SWING_REACH = 3.0;
	private static final String CONFIG = """
		{
			"version": 1,
			"entities": { "minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } } },
			"roles": { "minecraft:skeleton": { "role": "ranged", "near": 5.0, "far": 15.0 } },
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	private static void turtle(final ServerPlayer player) {
		for (int i = 0; i < LearnConfigStore.get().shieldSaturation(); i++) {
			Profiles.record(player, Habit.BLOCK);
		}
	}

	private static Mob zombie(final GameTestHelper helper, final ServerPlayer player) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 24).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		return zombie;
	}

	private static Mob skeleton(final GameTestHelper helper, final ServerPlayer player) {
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(CONFIG));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 24, LearnArena.SPAWN_Y, 24).spawn();
		LearnArena.keepTargeting(helper, skeleton, player);
		return skeleton;
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void aZombieKeepsOutOfReachOfATurtlesShield(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 30.5, 24.5);
		turtle(player);
		LearnArena.raiseShield(player);
		Mob zombie = zombie(helper, player);
		double[] nearest = {Double.MAX_VALUE};
		helper.onEachTick(() -> nearest[0] = Math.min(nearest[0], zombie.distanceTo(player)));
		// Under the patience the zombie is given, so the wait has not yet run out.
		helper.runAfterDelay(LearnConfigStore.get().shieldPatienceTicks() - 20L, () -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aZombieKeepsOutOfReachOfATurtlesShield RESULT nearest={}", nearest[0]);
			helper.assertTrue(nearest[0] > SWING_REACH, "the zombie came to " + nearest[0] + " of a raised shield");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void aZombieChargesAPlayerWhoDoesNotTurtle(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 30.5, 24.5);
		LearnArena.raiseShield(player);
		Mob zombie = zombie(helper, player);
		helper.succeedWhen(() -> {
			double distance = zombie.distanceTo(player);
			if (distance <= SWING_REACH) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aZombieChargesAPlayerWhoDoesNotTurtle RESULT distance={}", distance);
			}

			helper.assertTrue(distance <= SWING_REACH, "the zombie held at " + distance);
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void aZombieClosesInWhenTheShieldDrops(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 30.5, 24.5);
		turtle(player);
		LearnArena.raiseShield(player);
		Mob zombie = zombie(helper, player);
		helper.runAfterDelay(40L, () -> LearnArena.lowerShield(player));
		helper.succeedWhen(() -> {
			double distance = zombie.distanceTo(player);
			if (distance <= SWING_REACH) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aZombieClosesInWhenTheShieldDrops RESULT distance={} tick={}", distance, helper.getTick());
			}

			helper.assertTrue(helper.getTick() > 40L && distance <= SWING_REACH, "the zombie held at " + distance);
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void aZombieRunsOutOfPatience(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 30.5, 24.5);
		turtle(player);
		LearnArena.raiseShield(player);
		Mob zombie = zombie(helper, player);
		helper.succeedWhen(() -> {
			double distance = zombie.distanceTo(player);
			if (distance <= SWING_REACH) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aZombieRunsOutOfPatience RESULT distance={} tick={}", distance, helper.getTick());
			}

			helper.assertTrue(distance <= SWING_REACH, "the zombie is still waiting at " + distance);
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void anArcherHoldsFireAtATurtlesShield(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 32.5, 24.5);
		turtle(player);
		LearnArena.raiseShield(player);
		Mob skeleton = skeleton(helper, player);
		helper.runAfterDelay(LearnConfigStore.get().shieldPatienceTicks() - 20L, () -> {
			int loosed = LearnTestInit.loosedBy(skeleton.getUUID());
			StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherHoldsFireAtATurtlesShield RESULT loosed={}", loosed);
			helper.assertTrue(loosed == 0, "the archer loosed " + loosed + " at a raised shield");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void anArcherShootsAPlayerWhoDoesNotTurtle(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 32.5, 24.5);
		LearnArena.raiseShield(player);
		Mob skeleton = skeleton(helper, player);
		helper.succeedWhen(() -> {
			int loosed = LearnTestInit.loosedBy(skeleton.getUUID());
			if (loosed > 0) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherShootsAPlayerWhoDoesNotTurtle RESULT loosed={}", loosed);
			}

			helper.assertTrue(loosed > 0, "the archer never shot");
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void anArcherShootsWhenTheShieldDrops(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 32.5, 24.5);
		turtle(player);
		LearnArena.raiseShield(player);
		Mob skeleton = skeleton(helper, player);
		helper.runAfterDelay(40L, () -> LearnArena.lowerShield(player));
		helper.succeedWhen(() -> {
			int loosed = LearnTestInit.loosedBy(skeleton.getUUID());
			if (loosed > 0) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherShootsWhenTheShieldDrops RESULT loosed={} tick={}", loosed, helper.getTick());
			}

			helper.assertTrue(helper.getTick() > 40L && loosed > 0, "the archer never shot after the shield dropped");
		});
	}
}
