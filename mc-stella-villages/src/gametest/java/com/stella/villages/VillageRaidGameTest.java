package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.mixin.RaidAccessor;
import com.stella.villages.raid.VillageRaids;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.phys.Vec3;

/** V3: the daily roll's rate and bookkeeping, the one-wave cap, and the attendance rule. */
public final class VillageRaidGameTest {
	private static final int TRIALS = 100_000;
	// 3.29 standard deviations of a binomial rate at the trial count: a 99.9% band around the shipped chance.
	private static final double BAND = 3.29 * Math.sqrt(VillageRaids.RAID_CHANCE * (1.0 - VillageRaids.RAID_CHANCE) / TRIALS);

	@GameTest
	public void raidRollRateOverOneHundredThousandSeededDays(final GameTestHelper helper) {
		int[] raids = { 0, 0 };
		int differing = 0;
		for (long day = 0; day < TRIALS; day++) {
			boolean first = VillageRaids.rolls(20260926L, day);
			boolean second = VillageRaids.rolls(20260927L, day);
			raids[0] += first ? 1 : 0;
			raids[1] += second ? 1 : 0;
			differing += first != second ? 1 : 0;
		}

		double rate = (double) raids[0] / TRIALS;
		PatrolTestSupport.result("raidRollRateOverOneHundredThousandSeededDays",
			"rate=" + rate + " chance=" + VillageRaids.RAID_CHANCE + " band=" + BAND + " otherSeedRate=" + (double) raids[1] / TRIALS + " daysThatDiffer=" + differing);
		helper.assertTrue(Math.abs(rate - VillageRaids.RAID_CHANCE) <= BAND, "the measured raid rate " + rate + " is outside the band around " + VillageRaids.RAID_CHANCE);
		// A second world must raid on other days, or every world would share one raid calendar.
		helper.assertTrue(differing > 0, "two world seeds rolled the same raid days");
		helper.succeed();
	}

	@GameTest
	public void dailyDecisionFollowsTheDayAndTheRoll(final GameTestHelper helper) {
		VillageWorldData data = VillageWorldData.empty();
		List<VillageRaids.Outcome> outcomes = new ArrayList<>();
		// The first day a world is seen only records it; then each new day rolls once, and the roll decides.
		outcomes.add(VillageRaids.decide(data, 3L, true));
		outcomes.add(VillageRaids.decide(data, 3L, true));
		outcomes.add(VillageRaids.decide(data, 4L, true));
		outcomes.add(VillageRaids.decide(data, 4L, true));
		outcomes.add(VillageRaids.decide(data, 5L, false));
		List<VillageRaids.Outcome> expected = List.of(
			VillageRaids.Outcome.FIRST_DAY, VillageRaids.Outcome.ALREADY_ROLLED, VillageRaids.Outcome.RAID,
			VillageRaids.Outcome.ALREADY_ROLLED, VillageRaids.Outcome.NO_RAID
		);
		PatrolTestSupport.result("dailyDecisionFollowsTheDayAndTheRoll", "outcomes=" + outcomes);
		helper.assertValueEqual(outcomes, expected, "decisions for days 3, 3, 4, 4, 5");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void modRaidHasExactlyOneWave(final GameTestHelper helper) {
		String test = "modRaidHasExactlyOneWave";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		VillageWorldData.VillageRecord village = village(helper);
		Raid raid = VillageRaids.start(helper.getLevel(), village);
		int waves = raid == null ? -1 : ((RaidAccessor) raid).mcStellaVillages$numGroups();
		if (raid != null) {
			raid.stop();
		}
		PatrolTestSupport.result(test, "started=" + (raid != null) + " waves=" + waves);
		helper.assertTrue(raid != null && waves == 1, "the village raid did not start with exactly one wave: started=" + (raid != null) + " waves=" + waves);
		helper.succeed();
	}

	@GameTest(padding = 64)
	public void vanillaRaidKeepsItsWaves(final GameTestHelper helper) {
		// The same construction outside a mod raid must keep vanilla's count, so the cap cannot leak into omen raids.
		Raid raid = new Raid(helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3)), helper.getLevel().getDifficulty());
		int waves = ((RaidAccessor) raid).mcStellaVillages$numGroups();
		PatrolTestSupport.result("vanillaRaidKeepsItsWaves", "waves=" + waves + " vanilla=" + raid.getNumGroups(helper.getLevel().getDifficulty()));
		helper.assertValueEqual(Integer.valueOf(waves), Integer.valueOf(raid.getNumGroups(helper.getLevel().getDifficulty())), "vanilla wave count");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void attendanceFollowsPlayerDistance(final GameTestHelper helper) {
		String test = "attendanceFollowsPlayerDistance";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		VillageWorldData.VillageRecord village = village(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 anchor = Vec3.atBottomCenterOf(village.anchor());
		player.snapTo(anchor.x + 4.0, anchor.y, anchor.z, 0.0F, 0.0F);
		boolean near = VillageRaids.attendant(helper.getLevel(), village) == player;
		player.snapTo(anchor.x + VillageRaids.ATTENDANCE_RANGE + 8.0, anchor.y, anchor.z, 0.0F, 0.0F);
		ServerPlayer far = VillageRaids.attendant(helper.getLevel(), village);
		boolean farIsThisPlayer = far == player;
		player.discard();
		PatrolTestSupport.result(test, "near=" + near + " farIsThisPlayer=" + farIsThisPlayer);
		helper.assertTrue(near && !farIsThisPlayer, "attendance did not follow the player's distance: near=" + near + " far=" + farIsThisPlayer);
		helper.succeed();
	}

	private static VillageWorldData.VillageRecord village(final GameTestHelper helper) {
		UUID id = PatrolTestSupport.villageId(helper);
		return VillageWorldData.get(helper.getLevel()).village(id).orElseThrow();
	}
}
