package com.stella.rpg;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Every skill and spell cooldown falls by 0.8 * s / (s + 200), with s = INT + DEX. */
public final class CooldownReductionGameTest {
	private static final double EPSILON = 1e-6;

	@GameTest
	public void reductionFollowsIntAndDex(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		int[][] stats = {{0, 0}, {200, 0}, {0, 200}, {100, 100}, {500, 500}, {1024, 1024}};
		double[] reduction = {0.0, 0.40, 0.40, 0.40, 2.0 / 3.0, 0.728826};
		StringBuilder row = new StringBuilder();
		for (int i = 0; i < stats.length; i++) {
			RpgTestSupport.setStat(player, Stat.INT, stats[i][0]);
			RpgTestSupport.setStat(player, Stat.DEX, stats[i][1]);
			double cut = 1.0 - RpgSpells.cooldown(player);
			row.append(" int").append(stats[i][0]).append("+dex").append(stats[i][1]).append("=").append(cut);
			helper.assertTrue(Math.abs(cut - reduction[i]) < EPSILON,
				"INT " + stats[i][0] + " DEX " + stats[i][1] + " cut the cooldown by " + cut + " expected " + reduction[i]);
		}
		RpgTestSupport.result("reductionFollowsIntAndDex", row.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void reductionNeverPassesEightyPercent(final GameTestHelper helper) {
		double far = 1.0 - RpgCurve.cooldownMultiplier(Integer.MAX_VALUE / 2);
		double negative = RpgCurve.cooldownMultiplier(-50);
		RpgTestSupport.result("reductionNeverPassesEightyPercent", "far=" + far + " negative=" + negative);
		helper.assertTrue(far < 0.8 && far > 0.79, "a huge s cut the cooldown by " + far);
		helper.assertTrue(negative == 1.0, "a negative s gave a multiplier of " + negative);
		helper.succeed();
	}

	@GameTest
	public void aStaleConfigKeyCannotChangeTheCurve(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig config = RpgConfigStore.parse("{\"cooldown_reduction\":{\"max\":0.5,\"half\":400}}", errors::add);
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.INT, 100);
		RpgTestSupport.setStat(player, Stat.DEX, 100);
		double cooldown;
		try {
			RpgConfigStore.set(config);
			cooldown = RpgSpells.cooldown(player);
		} finally {
			RpgConfigStore.set(null);
		}
		RpgTestSupport.result("aStaleConfigKeyCannotChangeTheCurve", "errors=" + errors + " cooldown=" + cooldown);
		helper.assertTrue(errors.size() == 1 && errors.get(0).contains("cooldown_reduction"), "errors=" + errors);
		helper.assertTrue(config.equals(RpgConfig.defaults()), "config=" + config);
		helper.assertTrue(Math.abs(cooldown - 0.6) < EPSILON, "the old key moved the cooldown to " + cooldown);
		helper.succeed();
	}
}
