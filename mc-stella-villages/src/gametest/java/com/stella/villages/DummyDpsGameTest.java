package com.stella.villages;

import com.stella.villages.dummy.TrainingDummy;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The dummy's damage per second sums the hits of the last 5 seconds only. */
public class DummyDpsGameTest {
	@GameTest
	public void dpsCountsTheLastFiveSeconds(final GameTestHelper helper) {
		List<TrainingDummy.Hit> hits = List.of(
			new TrainingDummy.Hit(0L, 100.0F),
			new TrainingDummy.Hit(150L, 30.0F),
			new TrainingDummy.Hit(199L, 20.0F)
		);
		double dps = TrainingDummy.dps(hits, 200L);
		McStellaVillages.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "dummyDps", "dps=" + dps);
		helper.assertTrue(Math.abs(dps - 10.0) < 1.0E-6, "dps was " + dps + ", expected 10 from 50 damage in 5 seconds");
		helper.succeed();
	}
}
