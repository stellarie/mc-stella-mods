package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.gametest.framework.GameTestHelper;

/** The Sovereign's health cap follows the live attribute cap and the rpg health scale. */
public class SovereignHealthCapGameTest {
	private static final String SHARE = "mod-stella-rpg:health-scale";

	@GameTest
	public void capFollowsShare(final GameTestHelper helper) {
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		Object previous = share.get(SHARE);
		double withShare;
		double without;
		try {
			share.put(SHARE, 15.0);
			withShare = BlazeSovereign.healthCap();
			share.remove(SHARE);
			without = BlazeSovereign.healthCap();
		} finally {
			if (previous == null) {
				share.remove(SHARE);
			} else {
				share.put(SHARE, previous);
			}
		}
		StellaNether.LOGGER.info("[BEHAVIOR] test=capFollowsShare RESULT withShare={} without={}", withShare, without);
		helper.assertTrue(Math.abs(withShare - 1024.0 / 15.0) < 1.0E-6, "cap with K=15 was " + withShare);
		helper.assertTrue(Math.abs(without - 1024.0) < 1.0E-6, "cap without the share was " + without);
		helper.succeed();
	}
}
