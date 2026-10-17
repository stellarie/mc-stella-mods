package com.stella.end;

import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Candles are the only light inside the six floors. */
public class CastleLightGameTest {
	@GameTest(environment = "mod-stella-end-test:light_scan", maxTicks = 20)
	public void noBlockButACandleGivesLightInsideTheSixFloors(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Map<String, Integer> lit = new TreeMap<>();
		String first = "";
		int scanned = 0;
		int candles = 0;
		int top = EndCastle.slabY(EndCastle.FLOORS) + EndCastle.FLOOR_HEIGHT - 1;
		for (int x = EndCastle.ZONE_MIN_X; x < EndCastle.ZONE_MIN_X + EndCastle.ZONE_SIZE; x++) {
			for (int z = EndCastle.ZONE_MIN_Z; z < EndCastle.ZONE_MIN_Z + EndCastle.ZONE_SIZE; z++) {
				for (int y = EndCastle.BASE_Y; y <= top; y++) {
					BlockState state = end.getBlockState(new BlockPos(x, y, z));
					scanned++;
					if (state.getBlock() instanceof CandleBlock) {
						candles++;
					} else if (state.getLightEmission() > 0) {
						String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
						lit.merge(name, 1, Integer::sum);
						if (first.isEmpty()) {
							first = name + " at " + x + "," + y + "," + z;
						}
					}
				}
			}
		}
		EndTestSupport.result("lightScan", "scanned=" + scanned + " candles=" + candles + " otherLights=" + lit);
		helper.assertTrue(lit.isEmpty(), "light sources besides candles: " + lit + ", the first " + first);
		helper.succeed();
	}
}
