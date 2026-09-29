package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Mobs after a player who falls back gather outside a narrow gap and go in together, not one at a time. */
public class ChokepointGameTest {
	private static final int LONG = 1400;
	private static final int WALL_Z = 18;
	/** Behind the wall, so the way in runs through the gap. */
	private static final double PLAYER_Z = WALL_Z - 7.5;
	private static final int GAP_X = 24;
	/** A mob whose relative z is below this has come through the gap. */
	private static final double THROUGH = WALL_Z - 0.5;
	/** Close enough that an unimpeded zombie is through the gap well inside the patience, so a hold shows. */
	private static final int NEAR_Z = WALL_Z + 7;

	/** A wall across the arena at `WALL_Z`, three blocks high, with a one block gap. */
	private static void wall(final GameTestHelper helper) {
		for (int x = 0; x < 48; x++) {
			if (x == GAP_X) {
				continue;
			}

			for (int y = 1; y <= 3; y++) {
				helper.setBlock(x, y, WALL_Z, Blocks.STONE);
			}
		}
	}

	private static void retreater(final ServerPlayer player) {
		for (int i = 0; i < LearnConfigStore.get().retreatSaturation(); i++) {
			Profiles.record(player, Habit.RETREAT);
		}
	}

	private static Mob zombie(final GameTestHelper helper, final ServerPlayer player, final int x, final int z) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		return zombie;
	}

	private static double relativeZ(final GameTestHelper helper, final Mob mob) {
		return helper.relativeVec(mob.position()).z;
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theMouthIsTheNarrowGapAndTheStagingPointIsBeforeIt(final GameTestHelper helper) {
		LearnArena.floor(helper);
		wall(helper);
		ServerPlayer player = LearnArena.player(helper, GAP_X + 0.5, PLAYER_Z);
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, GAP_X, LearnArena.SPAWN_Y, WALL_Z + 20).spawn();
		LearnArena.whenReady(helper, List.of(player, mob), () -> LearnArena.pollUntil(helper, mob::onGround, 60, () -> {
			Choke.Mouth mouth = Choke.findMouth(mob, player);
			helper.assertTrue(mouth != null, "no gap was found in a wall with one gap");
			Vec3 gap = helper.relativeVec(Vec3.atCenterOf(mouth.gap()));
			double stagingZ = helper.relativeVec(mouth.staging()).z;
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theMouthIsTheNarrowGapAndTheStagingPointIsBeforeIt RESULT gap={} stagingZ={}", gap, stagingZ);
			helper.assertTrue((int) Math.floor(gap.x) == GAP_X && (int) Math.floor(gap.z) == WALL_Z, "the gap was " + gap);
			helper.assertTrue(stagingZ > WALL_Z + 2.0 && stagingZ < relativeZ(helper, mob), "the staging point is at z " + stagingZ);
			helper.succeed();
		}, () -> "the zombie never landed"));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void openGroundHasNoMouth(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, GAP_X + 0.5, PLAYER_Z);
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, GAP_X, LearnArena.SPAWN_Y, WALL_Z + 20).spawn();
		LearnArena.whenReady(helper, List.of(player, mob), () -> LearnArena.pollUntil(helper, mob::onGround, 60, () -> {
			Choke.Mouth mouth = Choke.findMouth(mob, player);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=openGroundHasNoMouth RESULT mouth={}", mouth);
			helper.assertTrue(mouth == null, "a gap was found on open ground: " + mouth);
			helper.succeed();
		}, () -> "the zombie never landed"));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aLoneMobHoldsOutsideTheGapForARetreater(final GameTestHelper helper) {
		LearnArena.floor(helper);
		wall(helper);
		ServerPlayer player = LearnArena.player(helper, GAP_X + 0.5, PLAYER_Z);
		retreater(player);
		Mob mob = zombie(helper, player, GAP_X, NEAR_Z);
		double[] nearest = {Double.MAX_VALUE};
		long[] began = {-1L};
		helper.onEachTick(() -> {
			if (began[0] >= 0L) {
				nearest[0] = Math.min(nearest[0], relativeZ(helper, mob));
				long waited = helper.getTick() - began[0];
				// Well inside the patience the mob is given, so the wait has not yet run out.
				if (waited == LearnConfigStore.get().chokePatienceTicks() - 40L) {
					StellaLearn.LOGGER.info("[BEHAVIOR] test=aLoneMobHoldsOutsideTheGapForARetreater RESULT nearestZ={} waited={}", nearest[0], waited);
					helper.assertTrue(nearest[0] > WALL_Z + 1.0, "the lone mob came to z " + nearest[0] + " at the gap");
					helper.succeed();
				}
			}
		});
		LearnArena.whenReady(helper, List.of(player, mob), () -> began[0] = helper.getTick());
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobAfterAPlayerWhoNeverFallsBackGoesStraightThrough(final GameTestHelper helper) {
		LearnArena.floor(helper);
		wall(helper);
		ServerPlayer player = LearnArena.player(helper, GAP_X + 0.5, PLAYER_Z);
		Mob mob = zombie(helper, player, GAP_X, NEAR_Z);
		long[] began = {-1L};
		LearnArena.whenReady(helper, List.of(player, mob), () -> {
			began[0] = helper.getTick();
			LearnArena.pollUntil(helper, () -> relativeZ(helper, mob) < THROUGH, 300, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobAfterAPlayerWhoNeverFallsBackGoesStraightThrough RESULT ticks={}", helper.getTick() - began[0]);
				helper.assertTrue(helper.getTick() - began[0] < LearnConfigStore.get().chokePatienceTicks(), "it took as long as a wait");
				helper.succeed();
			}, () -> "the mob never came through the gap");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aLoneMobGivesUpWaitingAfterItsPatience(final GameTestHelper helper) {
		LearnArena.floor(helper);
		wall(helper);
		ServerPlayer player = LearnArena.player(helper, GAP_X + 0.5, PLAYER_Z);
		retreater(player);
		Mob mob = zombie(helper, player, GAP_X, NEAR_Z);
		long[] began = {-1L};
		LearnArena.whenReady(helper, List.of(player, mob), () -> {
			began[0] = helper.getTick();
			LearnArena.pollUntil(helper, () -> relativeZ(helper, mob) < THROUGH, 600, () -> {
				long waited = helper.getTick() - began[0];
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aLoneMobGivesUpWaitingAfterItsPatience RESULT waited={}", waited);
				helper.assertTrue(waited >= LearnConfigStore.get().chokePatienceTicks() - 40L, "it went in after only " + waited + " ticks");
				helper.succeed();
			}, () -> "the mob waited for ever");
		});
	}

	/** Strung out from 7 to 21 blocks off: unimpeded, the first is through long before the last is near the gap. The gap jams two mobs abreast, so the test reads the first through and not all three. */
	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aGroupGoesInTogether(final GameTestHelper helper) {
		LearnArena.floor(helper);
		wall(helper);
		ServerPlayer player = LearnArena.player(helper, GAP_X + 0.5, PLAYER_Z);
		retreater(player);
		List<Mob> group = List.of(zombie(helper, player, GAP_X - 3, NEAR_Z), zombie(helper, player, GAP_X, NEAR_Z + 7), zombie(helper, player, GAP_X + 3, NEAR_Z + 14));
		long[] began = {-1L};
		double[] farthestAtFirst = {-1.0};
		helper.onEachTick(() -> {
			if (began[0] < 0L || farthestAtFirst[0] >= 0.0) {
				return;
			}

			if (group.stream().anyMatch(mob -> relativeZ(helper, mob) < THROUGH)) {
				Vec3 gap = new Vec3(GAP_X + 0.5, 1.0, WALL_Z + 0.5);
				farthestAtFirst[0] = group.stream().mapToDouble(mob -> helper.relativeVec(mob.position()).distanceTo(gap)).max().orElse(-1.0);
			}
		});
		LearnArena.whenReady(helper, group, () -> {
			began[0] = helper.getTick();
			LearnArena.pollUntil(helper, () -> farthestAtFirst[0] >= 0.0, 700, () -> {
				long waited = helper.getTick() - began[0];
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aGroupGoesInTogether RESULT waited={} farthestAtFirst={}", waited, farthestAtFirst[0]);
				helper.assertTrue(farthestAtFirst[0] >= 0.0 && farthestAtFirst[0] < Choke.GATHER_RADIUS + 2.0, "the first went in while another was " + farthestAtFirst[0] + " from the gap");
				helper.succeed();
			}, () -> "no one in the group came through");
		});
	}
}
