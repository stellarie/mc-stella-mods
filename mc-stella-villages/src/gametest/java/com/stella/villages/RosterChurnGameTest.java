package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;

/**
 * A village with an incomplete roster fills it once. A chunk that loads in the middle of a fill, or loads again soon
 * after a fill found no place, adds no member and starts no second fill until the back-off ends.
 */
public class RosterChurnGameTest {
	private static final int FULL_ROSTER = PatrolService.PATROL_MEMBER_COUNT;
	/** The anchor whose first ranged guard makes the hook load the chunk again, or null when the hook is idle. */
	private static volatile BlockPos hookAnchor;
	private static volatile int hookFired;

	static {
		// A chunk load in the middle of a fill is what the live log shows, so the test stages it on the first ranged guard.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			BlockPos anchor = hookAnchor;
			if (anchor != null && entity instanceof Mob mob && PatrolState.role(mob) == PatrolState.Role.RANGED_GUARD) {
				hookAnchor = null;
				hookFired++;
				PatrolService.onChunkLoad(level, level.getChunkAt(anchor));
			}
		});
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void chunkLoadInsideAFillAddsNoExtraMembers(final GameTestHelper helper) {
		String test = "chunkLoadInsideAFillAddsNoExtraMembers";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		emptyRoster(helper);
		BlockPos anchor = PatrolTestSupport.anchor(helper);
		int firedBefore = hookFired;
		hookAnchor = anchor;
		try {
			PatrolService.onChunkLoad(helper.getLevel(), helper.getLevel().getChunkAt(anchor));
		} finally {
			hookAnchor = null;
		}

		int records = records(helper).size();
		int live = PatrolTestSupport.patrols(helper).size();
		PatrolTestSupport.result(test, "hookFired=" + (hookFired - firedBefore) + " records=" + records + " live=" + live);
		helper.assertValueEqual(Integer.valueOf(hookFired - firedBefore), Integer.valueOf(1), "chunk loads staged inside the fill");
		helper.assertValueEqual(Integer.valueOf(records), Integer.valueOf(FULL_ROSTER), "member records after the nested chunk load");
		helper.assertValueEqual(Integer.valueOf(live), Integer.valueOf(FULL_ROSTER), "live members after the nested chunk load");
		helper.succeed();
	}

	@GameTest(maxTicks = 900, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void failedFillWaitsOutTheBackoff(final GameTestHelper helper) {
		String test = "failedFillWaitsOutTheBackoff";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		emptyRoster(helper);
		BlockPos anchor = PatrolTestSupport.anchor(helper);
		// The filter holds only this village shut, so tests that run beside it keep their own places.
		PatrolService.setPositionFilter(position -> position.distSqr(anchor) > 96 * 96);
		UUID villageId = PatrolTestSupport.villageId(helper);
		int attemptsBefore = PatrolService.fillAttempts(villageId);
		for (int scan = 0; scan < 5; scan++) {
			PatrolService.onChunkLoad(helper.getLevel(), helper.getLevel().getChunkAt(anchor));
		}
		int attempts = PatrolService.fillAttempts(villageId) - attemptsBefore;
		PatrolTestSupport.result(test, "fillsInFiveScans=" + attempts + " records=" + records(helper).size());
		if (attempts != 1 || !records(helper).isEmpty()) {
			PatrolService.setPositionFilter(position -> true);
		}
		helper.assertValueEqual(Integer.valueOf(attempts), Integer.valueOf(1), "fills started by five chunk loads with no place to stand");
		helper.assertValueEqual(Integer.valueOf(records(helper).size()), Integer.valueOf(0), "member records while no place exists");

		// Places exist again, but the back-off has not ended.
		PatrolService.setPositionFilter(position -> true);
		PatrolService.onChunkLoad(helper.getLevel(), helper.getLevel().getChunkAt(anchor));
		int insideBackoff = PatrolService.fillAttempts(villageId) - attemptsBefore;
		PatrolTestSupport.result(test, "fillsInsideBackoff=" + insideBackoff + " records=" + records(helper).size());
		helper.assertValueEqual(Integer.valueOf(insideBackoff), Integer.valueOf(1), "fills started inside the back-off");
		helper.assertValueEqual(Integer.valueOf(records(helper).size()), Integer.valueOf(0), "member records inside the back-off");

		helper.runAfterDelay(PatrolService.SPAWN_BACKOFF_TICKS + 40, () -> {
			PatrolService.onChunkLoad(helper.getLevel(), helper.getLevel().getChunkAt(anchor));
			int afterBackoff = PatrolService.fillAttempts(villageId) - attemptsBefore;
			PatrolTestSupport.result(test, "fillsAfterBackoff=" + afterBackoff + " records=" + records(helper).size());
			helper.assertValueEqual(Integer.valueOf(afterBackoff), Integer.valueOf(2), "fills started once the back-off ended");
			helper.assertValueEqual(Integer.valueOf(records(helper).size()), Integer.valueOf(FULL_ROSTER), "member records after the back-off");
			helper.succeed();
		});
	}

	/** Leaves the village with no members and its roster open, as a village reopened by a migration with none left. */
	private static void emptyRoster(final GameTestHelper helper) {
		VillageWorldData data = VillageWorldData.get(helper.getLevel());
		for (Mob member : List.copyOf(PatrolTestSupport.patrols(helper))) {
			data.removeMember(member.getUUID());
			member.discard();
		}
		PatrolTestSupport.setRosterStarted(helper, "emptyRoster", false);
	}

	private static List<VillageWorldData.MemberRecord> records(final GameTestHelper helper) {
		UUID villageId = PatrolTestSupport.villageId(helper);
		return VillageWorldData.get(helper.getLevel()).village(villageId).orElseThrow().members();
	}
}
