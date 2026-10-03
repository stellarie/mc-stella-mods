package com.stella.learn;

import com.stella.mobs.behavior.Stance;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.util.List;
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

	/**
	 * A turtle at some health, and the zombie after it.
	 * `nearest` is the zombie's closest approach.
	 */
	private record Duel(ServerPlayer player, Mob zombie, double[] nearest) {
	}

	private static Duel turtleDuel(final GameTestHelper helper, final double playerX, final double share) {
		ServerPlayer player = LearnArena.player(helper, playerX, 24.5);
		turtle(player);
		LearnArena.raiseShield(player);
		StanceFixtures.holdHealth(helper, player, share);
		Mob zombie = StanceFixtures.unscaled(helper, EntityTypes.ZOMBIE, (int) (playerX - 6.0), 24);
		LearnArena.keepTargeting(helper, zombie, player);
		StanceFixtures.readStance(helper, zombie, player);
		double[] nearest = {Double.MAX_VALUE};
		helper.onEachTick(() -> nearest[0] = Math.min(nearest[0], zombie.distanceTo(player)));
		return new Duel(player, zombie, nearest);
	}

	/**
	 * A mob that pursues a hurt player does not wait out that player's shield. Two players turtle alike, one at 10 percent
	 * health and one at full health. The zombie after the hurt player is in PURSUE and closes in on the raised shield. The
	 * zombie after the healthy player is in FIGHT and still waits it out, as before (decision D10). Each zombie reads its
	 * stance before its first AI pass. The mods load in a different order from run to run, and learn's goals can then run
	 * before the stance goal, so a wait would start before the stance is known.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void pursueSkipsWait(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Duel pursued = turtleDuel(helper, 12.5, StanceFixtures.HURT);
		Duel fought = turtleDuel(helper, 36.5, StanceFixtures.HEALTHY);
		LearnArena.whenReady(helper, List.of(pursued.player(), pursued.zombie(), fought.player(), fought.zombie()),
			() -> helper.runAfterDelay(LearnConfigStore.get().shieldPatienceTicks() - 20L, () -> {
				StellaLearn.LOGGER.info(LearnLog.line(
					"pursueSkipsWait", "RESULT", "pursuedNearest=" + LearnLog.num(pursued.nearest()[0]) + " pursuedStance=" + Stance.of(pursued.zombie())
						+ " foughtNearest=" + LearnLog.num(fought.nearest()[0]) + " foughtStance=" + Stance.of(fought.zombie())
				));
				helper.assertTrue(
					Stance.of(pursued.zombie()) == Stance.PURSUE && Stance.of(fought.zombie()) == Stance.FIGHT,
					"the stances were " + Stance.of(pursued.zombie()) + " and " + Stance.of(fought.zombie()) + ", not PURSUE and FIGHT"
				);
				helper.assertTrue(
					WaitOutShieldGoal.shieldRaised(pursued.player()) && WaitOutShieldGoal.shieldPressure(pursued.player()) >= LearnConfigStore.get().shieldThreshold(),
					"the hurt player is no turtle, so a wait would not start and the case proves nothing"
				);
				helper.assertTrue(pursued.nearest()[0] <= SWING_REACH, "the zombie in PURSUE came only to " + pursued.nearest()[0] + " of a raised shield");
				helper.assertTrue(fought.nearest()[0] > SWING_REACH, "the zombie in FIGHT came to " + fought.nearest()[0] + " of a raised shield, so the case proves nothing");
				helper.succeed();
			}));
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
