package com.stella.nether;

import com.stella.mobs.scaling.ThreatState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/**
 * One raid of each difficulty at a time: a normal and a hard raid run side by side on their own land, a pick
 * with a raid running joins it, and what happens in one raid does not reach the other.
 */
public class RaidPerDifficultyGameTest {
	@GameTest(environment = "mod-stella-nether-test:both_modes", maxTicks = 100)
	public void aNormalAndAHardRaidRunAtTheSameTimeOnTheirOwnLand(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer normalHost = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerPlayer hardHost = RaidModeGameTest.enter(helper, RaidMode.HARD);
		RaidState normal = RaidInstance.state(server, RaidMode.NORMAL);
		RaidState hard = RaidInstance.state(server, RaidMode.HARD);
		boolean inRaid = normalHost.level().dimension().equals(RaidTravel.RAID) && hardHost.level().dimension().equals(RaidTravel.RAID);
		boolean ownLand = RaidInstance.modeAt(normalHost.blockPosition()) == RaidMode.NORMAL && RaidInstance.modeAt(hardHost.blockPosition()) == RaidMode.HARD;
		double apart = normalHost.position().distanceTo(hardHost.position());
		NetherTestSupport.result("bothRaids", "normal=" + normal.active() + "/" + normal.host().isPresent() + " hard=" + hard.active() + "/" + hard.host().isPresent()
			+ " inRaid=" + inRaid + " ownLand=" + ownLand + " apart=" + (int) apart);
		NetherTestSupport.leave(helper, normalHost);
		NetherTestSupport.leave(helper, hardHost);
		helper.assertTrue(normal.active() && normal.mode() == RaidMode.NORMAL && normalHost.getUUID().equals(normal.host().orElse(null)),
			"the normal raid after its host picked it: " + normal);
		helper.assertTrue(hard.active() && hard.mode() == RaidMode.HARD && hardHost.getUUID().equals(hard.host().orElse(null)),
			"the hard raid after its host picked it, with the normal one running: " + hard);
		helper.assertTrue(inRaid && ownLand && apart > RaidInstance.RUN_STRIDE - 2.0 * RaidSites.MAX_SHIFT,
			"both hosts in the raid=" + inRaid + ", each on their own land=" + ownLand + ", " + apart + " blocks apart");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:join_running", maxTicks = 100)
	public void pickingADifficultyWithARaidRunningJoinsIt(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer host = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		RaidState hosted = RaidInstance.state(server, RaidMode.NORMAL);
		ServerPlayer guest = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		RaidState joined = RaidInstance.state(server, RaidMode.NORMAL);
		double apart = guest.position().distanceTo(host.position());
		boolean hardUntouched = !RaidInstance.state(server, RaidMode.HARD).active();
		NetherTestSupport.result("joinRunning", "hostKept=" + host.getUUID().equals(joined.host().orElse(null)) + " sameRun=" + (hosted.run() == joined.run())
			+ " guestInRaid=" + guest.level().dimension().equals(RaidTravel.RAID) + " apart=" + (int) apart + " hardUntouched=" + hardUntouched);
		NetherTestSupport.leave(helper, guest);
		NetherTestSupport.leave(helper, host);
		helper.assertTrue(joined.active() && host.getUUID().equals(joined.host().orElse(null)) && joined.run() == hosted.run() && joined.elapsed() >= hosted.elapsed(),
			"a guest changed the raid from " + hosted + " to " + joined);
		helper.assertTrue(guest.level().dimension().equals(RaidTravel.RAID) && apart < 16.0 && hardUntouched,
			"the guest is " + apart + " blocks from the host, hard raid left alone=" + hardUntouched);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:mob_levels", maxTicks = 400)
	public void aRaidMobTakesTheLevelOfItsOwnRaidWhileTheOtherRuns(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer normalHost = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerPlayer hardHost = RaidModeGameTest.enter(helper, RaidMode.HARD);
		hardHost.experienceLevel = 60;
		ServerLevel raid = (ServerLevel) normalHost.level();
		ZombifiedPiglin inNormal = RaidModeGameTest.spawnInRaid(raid, normalHost.position().add(3.0, 0.0, 0.0));
		ZombifiedPiglin inHard = RaidModeGameTest.spawnInRaid(raid, hardHost.position().add(3.0, 0.0, 0.0));
		inNormal.setNoAi(true);
		inHard.setNoAi(true);
		helper.succeedWhen(() -> {
			helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(inNormal.getUUID()) && NetherGameTestInit.LOADED_IN_RAID.contains(inHard.getUUID()),
				"the mobs have not loaded yet");
			ThreatState normalLevel = ThreatState.get(inNormal);
			ThreatState hardLevel = ThreatState.get(inHard);
			NetherTestSupport.result("mobLevelsApart", "normal=" + (normalLevel == null ? null : normalLevel.areaLevel()) + " hard=" + (hardLevel == null ? null : hardLevel.areaLevel()));
			helper.assertTrue(normalLevel != null && normalLevel.areaLevel() == RaidInstance.MIN_LEVEL_NORMAL,
				"the mob in the normal raid took " + (normalLevel == null ? null : normalLevel.areaLevel()) + " of " + RaidInstance.MIN_LEVEL_NORMAL + " while a level-60 player stood in the hard raid");
			helper.assertTrue(hardLevel != null && hardLevel.areaLevel() == 60, "the mob in the hard raid took " + (hardLevel == null ? null : hardLevel.areaLevel()) + " of 60");
			inNormal.discard();
			inHard.discard();
			NetherTestSupport.leave(helper, normalHost);
			NetherTestSupport.leave(helper, hardHost);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:leave_one", maxTicks = RaidInstance.EMPTY_TICKS + 100)
	public void aRaidThatEmptiesResetsAndTheOtherKeepsRunning(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer normalHost = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerPlayer hardHost = RaidModeGameTest.enter(helper, RaidMode.HARD);
		int normalRun = RaidInstance.state(server, RaidMode.NORMAL).run();
		int hardRun = RaidInstance.state(server, RaidMode.HARD).run();
		NetherTestSupport.leaveByPortal(helper, normalHost, RaidMode.NORMAL);
		helper.succeedWhen(() -> {
			RaidState normal = RaidInstance.state(server, RaidMode.NORMAL);
			RaidState hard = RaidInstance.state(server, RaidMode.HARD);
			helper.assertTrue(!normal.active() && normal.run() == normalRun + 1, "the emptied normal raid is still " + normal);
			NetherTestSupport.result("oneResets", "normalRun=" + normalRun + "->" + normal.run() + " hardActive=" + hard.active() + " hardRun=" + hardRun + "->" + hard.run());
			boolean kept = hard.active() && hard.run() == hardRun && hardHost.level().dimension().equals(RaidTravel.RAID);
			helper.assertTrue(kept, "the hard raid with its host still inside became " + hard);
			NetherTestSupport.leave(helper, normalHost);
			NetherTestSupport.leave(helper, hardHost);
		});
	}
}
