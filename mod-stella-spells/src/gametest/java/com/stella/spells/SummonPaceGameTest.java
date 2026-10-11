package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A servant paced to its master covers ground as fast as a walking player (about 0.216 blocks a tick), and as fast
 * as a sprinting one (about 0.281) while its master sprints, sprinting itself.
 */
public class SummonPaceGameTest {
	private static final double WALK = 0.216;
	private static final double SPRINT = 0.281;
	private static final int LENGTH = 48;

	@GameTest(maxTicks = 200)
	public void aServantWalksAndSprintsAtItsMastersPace(final GameTestHelper helper) {
		ServerPlayer master = FighterBooksGameTest.caster(helper);
		for (int x = 0; x < LENGTH; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR.defaultBlockState());
				helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR.defaultBlockState());
			}
		}
		Skeleton walker = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 2, 1));
		walker.getGoalSelector().removeAllGoals(goal -> true);
		double[] measured = new double[2];
		Vec3 far = helper.absoluteVec(new Vec3(LENGTH - 1.5, 2.0, 1.5));
		Vec3[] start = new Vec3[1];
		helper.onEachTick(() -> {
			SummonSkeleton.pace(walker, master);
			walker.getNavigation().moveTo(far.x, far.y, far.z, SummonSkeleton.FOLLOW_SPEED);
		});
		helper.runAfterDelay(20, () -> start[0] = walker.position());
		helper.runAfterDelay(60, () -> {
			measured[0] = horizontal(walker.position(), start[0]) / 40.0;
			walker.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
			walker.getNavigation().stop();
			master.setSprinting(true);
		});
		helper.runAfterDelay(80, () -> start[0] = walker.position());
		helper.runAfterDelay(120, () -> {
			measured[1] = horizontal(walker.position(), start[0]) / 40.0;
			boolean sprinting = walker.isSprinting();
			Spellbooks.LOGGER.info("[BEHAVIOR] test=summonPace RESULT walk={} sprint={} servantSprinting={}", measured[0], measured[1], sprinting);
			helper.assertTrue(Math.abs(measured[0] - WALK) <= WALK * 0.2, "the servant walked " + measured[0] + " a tick, not near " + WALK);
			helper.assertTrue(Math.abs(measured[1] - SPRINT) <= SPRINT * 0.2, "the servant sprinted " + measured[1] + " a tick, not near " + SPRINT);
			helper.assertTrue(sprinting, "the servant did not sprint with its master");
			helper.succeed();
		});
	}

	private static double horizontal(final Vec3 a, final Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}
}
