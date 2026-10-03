package com.stella.learn;

import com.stella.mobs.behavior.Stance;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/** A mob that pursues a hurt player does not hold outside a gap, even for a player who falls back. */
public class HoldAtChokeGameTest {
	private static final int MAX_TICKS = 600;
	private static final int WALL_Z = 18;
	/** Behind the wall, so the way in is the gap. */
	private static final double PLAYER_Z = WALL_Z - 7.5;
	/** Near enough to be through well inside patience. */
	private static final int NEAR_Z = WALL_Z + 7;
	/** Below this relative z, a mob is through. */
	private static final double THROUGH = WALL_Z - 0.5;
	private static final int PURSUED_GAP = 8;
	private static final int FOUGHT_GAP = 40;

	/**
	 * A player who falls back, and the zombie after it.
	 * `lowestZ` is the zombie's lowest relative z.
	 */
	private record Chase(ServerPlayer player, Mob zombie, double[] lowestZ) {
	}

	/** A wall with a one block gap at each x in `gaps`. */
	private static void wall(final GameTestHelper helper, final int... gaps) {
		for (int x = 0; x < 48; x++) {
			if (isGap(x, gaps)) {
				continue;
			}

			for (int y = 1; y <= 3; y++) {
				helper.setBlock(x, y, WALL_Z, Blocks.STONE);
			}
		}
	}

	private static boolean isGap(final int x, final int[] gaps) {
		for (int gap : gaps) {
			if (gap == x) {
				return true;
			}
		}

		return false;
	}

	private static Chase retreaterChase(final GameTestHelper helper, final int gapX, final double share) {
		ServerPlayer player = LearnArena.player(helper, gapX + 0.5, PLAYER_Z);
		for (int i = 0; i < LearnConfigStore.get().retreatSaturation(); i++) {
			Profiles.record(player, Habit.RETREAT);
		}

		StanceFixtures.holdHealth(helper, player, share);
		Mob zombie = StanceFixtures.unscaled(helper, EntityTypes.ZOMBIE, gapX, NEAR_Z);
		LearnArena.keepTargeting(helper, zombie, player);
		StanceFixtures.readStance(helper, zombie, player);
		double[] lowestZ = {Double.MAX_VALUE};
		helper.onEachTick(() -> lowestZ[0] = Math.min(lowestZ[0], helper.relativeVec(zombie.position()).z));
		return new Chase(player, zombie, lowestZ);
	}

	/**
	 * A mob that pursues a hurt player goes straight through the gap. Two players fall back alike, one at 10 percent health
	 * and one at full health, each behind a gap of its own in one wall. The zombie after the hurt player is in PURSUE and
	 * comes through well inside the patience. The zombie after the healthy player is in FIGHT and still holds outside its
	 * gap for the whole of that time, as before (decision D10). Each zombie reads its stance before its first AI pass, as in
	 * `WaitOutShieldGameTest.pursueSkipsWait`.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = LearnArena.PADDING)
	public void pursueSkipsHold(final GameTestHelper helper) {
		LearnArena.floor(helper);
		wall(helper, PURSUED_GAP, FOUGHT_GAP);
		Chase pursued = retreaterChase(helper, PURSUED_GAP, StanceFixtures.HURT);
		Chase fought = retreaterChase(helper, FOUGHT_GAP, StanceFixtures.HEALTHY);
		LearnArena.whenReady(helper, List.of(pursued.player(), pursued.zombie(), fought.player(), fought.zombie()),
			() -> helper.runAfterDelay(LearnConfigStore.get().chokePatienceTicks() - 40L, () -> {
				StellaLearn.LOGGER.info(LearnLog.line(
					"pursueSkipsHold", "RESULT", "pursuedLowestZ=" + LearnLog.num(pursued.lowestZ()[0]) + " pursuedStance=" + Stance.of(pursued.zombie())
						+ " foughtLowestZ=" + LearnLog.num(fought.lowestZ()[0]) + " foughtStance=" + Stance.of(fought.zombie())
				));
				helper.assertTrue(
					Stance.of(pursued.zombie()) == Stance.PURSUE && Stance.of(fought.zombie()) == Stance.FIGHT,
					"the stances were " + Stance.of(pursued.zombie()) + " and " + Stance.of(fought.zombie()) + ", not PURSUE and FIGHT"
				);
				helper.assertTrue(
					Choke.retreatPressure(pursued.player()) >= LearnConfigStore.get().chokeThreshold(),
					"the hurt player is no retreater, so a hold would not start and the case proves nothing"
				);
				helper.assertTrue(pursued.lowestZ()[0] < THROUGH, "the zombie in PURSUE held outside the gap, at z " + pursued.lowestZ()[0]);
				helper.assertTrue(fought.lowestZ()[0] > WALL_Z + 1.0, "the zombie in FIGHT came to z " + fought.lowestZ()[0] + " at the gap, so the case proves nothing");
				helper.succeed();
			}));
	}
}
