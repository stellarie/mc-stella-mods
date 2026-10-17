package com.stella.end;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** The castle stands on the island: a foundation fills every column of the outer zone down to the ground. */
public class CastleGroundGameTest {
	/** A column well outside the zone, which the build never touches: it shows where the island lies. */
	private static final int PROBE = EndCastle.ZONE_MIN_X + EndCastle.ZONE_SIZE + 12;

	@GameTest(environment = "mod-stella-end-test:ground_columns", maxTicks = 20)
	public void noAirColumnLiesBetweenTheFoundationAndTheIslandUnderTheKeepAndTheYard(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int island = EndCastle.BASE_Y - 1;
		while (island > EndCastle.BASE_Y - EndCastle.MAX_FOUNDATION && end.getBlockState(new BlockPos(PROBE, island, PROBE)).isAir()) {
			island--;
		}
		StringBuilder bad = new StringBuilder();
		int columns = 0;
		int faulty = 0;
		for (int x = EndCastle.ZONE_MIN_X; x < EndCastle.ZONE_MIN_X + EndCastle.ZONE_SIZE; x++) {
			for (int z = EndCastle.ZONE_MIN_Z; z < EndCastle.ZONE_MIN_Z + EndCastle.ZONE_SIZE; z++) {
				columns++;
				for (int y = EndCastle.BASE_Y - 1; y >= island; y--) {
					if (end.getBlockState(new BlockPos(x, y, z)).isAir()) {
						faulty++;
						if (bad.length() < 200) {
							bad.append(" air at ").append(x).append(',').append(y).append(',').append(z);
						}
						break;
					}
				}
			}
		}
		for (int[] corner : new int[][] {{EndCastle.MIN_X, EndCastle.MIN_Z}, {EndCastle.MIN_X + EndCastle.SIZE - 1, EndCastle.MIN_Z},
			{EndCastle.MIN_X, EndCastle.MIN_Z + EndCastle.SIZE - 1}, {EndCastle.MIN_X + EndCastle.SIZE - 1, EndCastle.MIN_Z + EndCastle.SIZE - 1}}) {
			if (end.getBlockState(new BlockPos(corner[0], EndCastle.BASE_Y - 1, corner[1])).isAir()) {
				bad.append(" keep corner ").append(corner[0]).append(',').append(corner[1]).append(" hangs over air");
			}
		}
		EndTestSupport.result("groundColumns", "island=" + island + " columns=" + columns + " faulty=" + faulty);
		helper.assertTrue(island > EndCastle.BASE_Y - EndCastle.MAX_FOUNDATION, "no island was found under the probe column");
		helper.assertTrue(bad.isEmpty(), "air between the foundation and the island in " + faulty + " columns:" + bad);
		helper.succeed();
	}
}
