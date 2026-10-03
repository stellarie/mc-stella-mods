package com.stella.learn;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/**
 * On open ground a gap search finds nothing. After two misses in a row the goal rests, so the next checks inside
 * the rest run no search. Before the rest, every check ran a path search, which cost the server tick.
 */
public class SearchRestGameTest {
	@GameTest(padding = LearnArena.PADDING)
	public void chokeSearchRestsAfterNothing(final GameTestHelper helper) {
		ServerPlayer player = LearnArena.player(helper, 8.5, 4.5);
		for (int i = 0; i < LearnConfigStore.get().retreatSaturation(); i++) {
			Profiles.record(player, Habit.RETREAT);
		}
		Mob zombie = StanceFixtures.unscaled(helper, EntityTypes.ZOMBIE, 8, 16);
		zombie.setTarget(player);
		HoldAtChokeGoal goal = new HoldAtChokeGoal(zombie);
		int before = HoldAtChokeGoal.searches;
		boolean first = goal.canUse();
		boolean second = goal.canUse();
		boolean third = goal.canUse();
		int ran = HoldAtChokeGoal.searches - before;
		LearnLog.info("searchRest", "RESULT", "choke first=" + first + " second=" + second + " third=" + third + " searches=" + ran);
		helper.assertTrue(!first && !second && !third && ran == 2, "three checks on open ground ran " + ran + " gap searches");
		helper.succeed();
	}
}
