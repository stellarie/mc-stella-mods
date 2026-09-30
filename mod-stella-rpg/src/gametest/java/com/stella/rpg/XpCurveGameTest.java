package com.stella.rpg;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** The XP needed per level follows 10 + 2.5 x L^1.5, from the config, on the real player. */
public final class XpCurveGameTest {
	@GameTest
	public void table(final GameTestHelper helper) {
		int[] levels = {0, 1, 10, 100, 1000};
		int[] expected = {10, 13, 89, 2510, 79067};
		StringBuilder seen = new StringBuilder();
		boolean valid = true;
		for (int i = 0; i < levels.length; i++) {
			int need = XpCurve.DEFAULT.need(levels[i]);
			seen.append(levels[i]).append("->").append(need).append(' ');
			valid &= need == expected[i];
		}
		RpgTestSupport.result("table", seen.toString());
		helper.assertTrue(valid, "curve was " + seen);
		helper.succeed();
	}

	@GameTest
	public void playerUsesCurve(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.setExperienceLevels(100);
		int need = player.getXpNeededForNextLevel();
		RpgTestSupport.result("playerUsesCurve", "level100=" + need);
		helper.assertTrue(need == 2510, "level 100 needs " + need);
		helper.succeed();
	}

	@GameTest
	public void oneLevelPerNeed(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.setExperienceLevels(50);
		player.experienceProgress = 0.0F;
		player.giveExperiencePoints(XpCurve.DEFAULT.need(50) - 1);
		int almost = player.experienceLevel;
		player.giveExperiencePoints(1);
		int after = player.experienceLevel;
		RpgTestSupport.result("oneLevelPerNeed", "almost=" + almost + " after=" + after + " progress=" + player.experienceProgress);
		helper.assertTrue(almost == 50 && after == 51, "levels went 50 -> " + almost + " -> " + after);
		helper.succeed();
	}

	@GameTest
	public void config(final GameTestHelper helper) {
		RpgConfig before = RpgConfigStore.current();
		List<String> errors = new ArrayList<>();
		try {
			RpgConfig custom = RpgConfigStore.parse("{\"xp_curve\":{\"base\":20,\"exponent\":2}}", errors::add);
			RpgConfig bad = RpgConfigStore.parse("{\"xp_curve\":{\"factor\":-1}}", errors::add);
			RpgConfig missing = RpgConfigStore.parse("{}", errors::add);
			RpgConfigStore.set(custom);
			ServerPlayer player = RpgTestSupport.player(helper);
			player.setExperienceLevels(10);
			int live = player.getXpNeededForNextLevel();
			RpgTestSupport.result("config", "custom10=" + custom.xpCurve().need(10) + " live=" + live
				+ " bad=" + bad.xpCurve() + " missing=" + missing.xpCurve() + " errors=" + errors);
			helper.assertTrue(custom.xpCurve().need(10) == 270 && live == 270, "custom curve gave " + live);
			helper.assertTrue(bad.xpCurve().equals(XpCurve.DEFAULT) && errors.size() == 1, "bad factor: " + errors);
			helper.assertTrue(missing.xpCurve().equals(XpCurve.DEFAULT), "missing key: " + missing.xpCurve());
		} finally {
			RpgConfigStore.set(before);
		}
		helper.succeed();
	}
}
