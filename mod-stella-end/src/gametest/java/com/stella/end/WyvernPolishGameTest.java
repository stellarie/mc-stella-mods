package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.item.Items;

/** The polished wyverns: riders with bows and a larger frame, and a cruise low enough for those bows to reach the deck. */
public class WyvernPolishGameTest {
	/** The name, never the class, of the mobs mod's volley goal: it takes the place of the vanilla bow goal when that mod is present. */
	static final String VOLLEY_GOAL_OF_MOBS = "SkeletonVolleyGoal";

	/** Whether a mob has a goal that draws a bow: the vanilla one, or the mobs mod's that replaces it. */
	static boolean shoots(final Mob mob) {
		return mob.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(goal -> goal.getGoal() instanceof RangedBowAttackGoal<?> || goal.getGoal().getClass().getSimpleName().equals(VOLLEY_GOAL_OF_MOBS));
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_bows", maxTicks = 200)
	public void wyvernRidersHoldBowsStandOneAndAHalfTimesTheSizeAndCanShoot(final GameTestHelper helper) {
		WyvernGameTest.Stand stand = WyvernGameTest.stand(helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(WyvernGameTest.escorts(stand.end(), stand.state()).size() == DragonRules.WYVERNS, "the pairs have not arrived"))
			.thenExecute(() -> {
				List<String> bad = new ArrayList<>();
				List<WyvernGameTest.Escort> pairs = WyvernGameTest.escorts(stand.end(), stand.state());
				for (WyvernGameTest.Escort pair : pairs) {
					if (!pair.rider().getMainHandItem().is(Items.BOW)) {
						bad.add("rider holds " + pair.rider().getMainHandItem());
					}
					double scale = pair.rider().getAttributeValue(Attributes.SCALE);
					if (scale != DragonRules.RIDER_SCALE || scale <= 1.0) {
						bad.add("rider scale " + scale);
					}
					if (!shoots(pair.rider())) {
						bad.add("rider has no bow goal");
					}
					if (!pair.rider().getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)) {
						bad.add("rider lost its helmet");
					}
				}
				EndTestSupport.result("wyvernBows", "pairs=" + pairs.size() + " scale=" + DragonRules.RIDER_SCALE + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(DragonRules.RIDER_SCALE == 1.5, "the rider scale constant is " + DragonRules.RIDER_SCALE);
				helper.assertTrue(pairs.size() == DragonRules.WYVERNS, pairs.size() + " pairs");
				helper.assertTrue(bad.isEmpty(), "rider defects: " + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_band", maxTicks = 500)
	public void wyvernsCruiseEightToFourteenBlocksOverTheDeckWithinTheRooftopDisc(final GameTestHelper helper) {
		WyvernGameTest.Stand stand = WyvernGameTest.stand(helper);
		int[] watched = new int[1];
		int[] strayed = new int[1];
		int[] tall = new int[1];
		int[] far = new int[1];
		double[] farthest = new double[1];
		double[] lowest = {Double.MAX_VALUE};
		double[] highest = {-Double.MAX_VALUE};
		boolean[] started = new boolean[1];
		helper.onEachTick(() -> {
			if (!started[0]) {
				return;
			}
			for (WyvernGameTest.Escort pair : WyvernGameTest.escorts(stand.end(), stand.state())) {
				double height = pair.wyvern().getY() - Rooftop.DECK_Y;
				double reach = Math.hypot(pair.wyvern().getX(), pair.wyvern().getZ());
				watched[0]++;
				lowest[0] = Math.min(lowest[0], height);
				highest[0] = Math.max(highest[0], height);
				// The dragon's wings throw back what comes near them, so a rare stray is the game's, not the cruise's.
				farthest[0] = Math.max(farthest[0], reach);
				if (height < DragonRules.WYVERN_BAND_LOW - 1.0 || height > DragonRules.WYVERN_BAND_HIGH + 1.0 || reach > EndCastle.DISC_RADIUS) {
					strayed[0]++;
					tall[0] += height < DragonRules.WYVERN_BAND_LOW - 1.0 || height > DragonRules.WYVERN_BAND_HIGH + 1.0 ? 1 : 0;
					far[0] += reach > EndCastle.DISC_RADIUS ? 1 : 0;
				}
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(WyvernGameTest.escorts(stand.end(), stand.state()).size() == DragonRules.WYVERNS, "the pairs have not arrived"))
			.thenExecute(() -> started[0] = true)
			.thenIdle(300)
			.thenExecute(() -> {
				EndTestSupport.result("wyvernBand", "watched=" + watched[0] + " strayed=" + strayed[0] + " badHeight=" + tall[0] + " badReach=" + far[0] + " farthest=" + farthest[0] + " lowest=" + lowest[0] + " highest=" + highest[0]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(DragonRules.WYVERN_BAND_LOW == 8.0 && DragonRules.WYVERN_BAND_HIGH == 14.0, "the band constants are off");
				helper.assertTrue(DragonRules.WYVERN_BAND_HIGH <= DragonRules.BOW_RANGE, "the band lies beyond the bow range " + DragonRules.BOW_RANGE);
				helper.assertTrue(watched[0] > 1000, "only " + watched[0] + " wyvern ticks were watched");
				helper.assertTrue(strayed[0] * 50 <= watched[0], strayed[0] + " of " + watched[0] + " wyvern ticks lay outside the band or the disc");
			})
			.thenSucceed();
	}
}
