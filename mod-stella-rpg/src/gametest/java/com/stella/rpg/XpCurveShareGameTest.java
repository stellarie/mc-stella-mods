package com.stella.rpg;

import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * A sibling reads the player curve through {@code mod-stella-rpg:xp-curve}: the share answers the same
 * number {@link XpCurve#needFor} does, at the level it is asked about.
 */
public final class XpCurveShareGameTest {
	@GameTest
	@SuppressWarnings("unchecked")
	public void shareAnswersTheSameNeedAsTheCurve(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(com.stella.rpg.api.XpCurveShare.OBJECT_SHARE_KEY);
		helper.assertTrue(shared instanceof IntUnaryOperator, "no curve function at "
			+ com.stella.rpg.api.XpCurveShare.OBJECT_SHARE_KEY + ": " + shared);
		IntUnaryOperator need = (IntUnaryOperator) shared;
		int[] levels = {0, 1, 10, 100};
		StringBuilder seen = new StringBuilder();
		boolean valid = true;
		for (int level : levels) {
			int viaShare = need.applyAsInt(level);
			int direct = XpCurve.needFor(level);
			seen.append(level).append("->").append(viaShare).append('/').append(direct).append(' ');
			valid &= viaShare == direct;
		}
		RpgTestSupport.result("xpCurveShare", seen.toString());
		helper.assertTrue(valid, "the share answered " + seen);
		helper.assertTrue(need.applyAsInt(1) == XpCurve.DEFAULT.need(1),
			"the share at level 1 is " + need.applyAsInt(1) + ", not the shipped " + XpCurve.DEFAULT.need(1));
		helper.succeed();
	}
}
