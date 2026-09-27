package com.stella.villages;

import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.VillageCoverage;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;

/**
 * A patrol's life around the server's own: on Peaceful its members leave as any illager does (the game
 * itself refuses a new patrol there), and a stopping server takes the head-missing clocks with it. Each runs alone in its
 * batch, because each changes something the whole server shares.
 */
public class PatrolLifecycleGameTest {
	@GameTest(environment = "mc-stella-villages-test:alone", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void patrolMembersLeaveOnPeaceful(final GameTestHelper helper) {
		String test = "patrolMembersLeaveOnPeaceful";
		Difficulty before = helper.getLevel().getDifficulty();
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			helper.getLevel().getServer().setDifficulty(before, true);
			return;
		}
		List<Mob> members = PatrolTestSupport.patrols(helper);
		helper.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
		helper.runAfterDelay(10L, () -> {
			long left = members.stream().filter(Mob::isRemoved).count();
			helper.getLevel().getServer().setDifficulty(before, true);
			PatrolTestSupport.result(test, "members=" + members.size() + " left=" + left);
			helper.assertTrue(!members.isEmpty() && left == members.size(), left + " of " + members.size() + " members left on Peaceful");
			helper.succeed();
		});
	}

	@GameTest(environment = "mc-stella-villages-test:alone")
	public void stoppingTheServerForgetsMissingHeads(final GameTestHelper helper) {
		VillageCoverage.noteMissing(UUID.randomUUID(), 5L);
		int before = VillageCoverage.missingHeadCount();
		PatrolService.onServerStopping(helper.getLevel().getServer());
		int after = VillageCoverage.missingHeadCount();
		PatrolTestSupport.result("stoppingTheServerForgetsMissingHeads", "before=" + before + " after=" + after);
		helper.assertTrue(before > 0 && after == 0, "missing-head clocks outlived the server: " + before + " -> " + after);
		helper.succeed();
	}

	/**
	 * A member whose chunk is ticking but whose entities are still loading must not be counted dead and
	 * replaced. The harness cannot hold a chunk in that window, so the check is manual: walk away from a
	 * village with a missing head until it unloads, return, and count that the patrol holds ten members.
	 */
	@GameTest(maxTicks = 5, required = false)
	public void refillWaitsForMembersStillLoadingRequiresManualTest(final GameTestHelper helper) {
		helper.fail("manual acceptance: a village's patrol never grows past ten after returning to it");
	}
}
