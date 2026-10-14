package com.stella.rpg;

import com.stella.rpg.job.Job;
import com.stella.rpg.job.Jobs;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** A job is chosen once from level 10, grows two stats for every level, and gets a bonus from each trial. */
public final class JobGameTest {
	@GameTest
	public void chooseOnceAtTen(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		helper.assertTrue(Jobs.of(player) == Job.NONE, "a new player has job " + Jobs.of(player));
		player.experienceLevel = 9;
		boolean early = Jobs.choose(player, Job.ARCHER);
		helper.assertTrue(!early && Jobs.of(player) == Job.NONE, "level 9 chose " + Jobs.of(player));
		player.experienceLevel = 10;
		helper.assertTrue(!Jobs.choose(player, Job.NONE), "NONE counted as a choice");
		helper.assertTrue(Jobs.choose(player, Job.ARCHER), "level 10 was refused");
		helper.assertTrue(Jobs.of(player) == Job.ARCHER, "job is " + Jobs.of(player));
		boolean again = Jobs.choose(player, Job.MAGE);
		RpgTestSupport.result("chooseOnceAtTen", "early=" + early + " again=" + again + " job=" + Jobs.of(player));
		helper.assertTrue(!again && Jobs.of(player) == Job.ARCHER, "a second choice gave " + Jobs.of(player));
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void jobSurvivesDeath(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 45;
		helper.assertTrue(Jobs.choose(player, Job.PRIEST), "choice refused");
		helper.assertTrue(Jobs.grantTrial(player, 40), "trial refused");
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		helper.assertTrue(player.isDeadOrDying(), "the player survived, so the case proves nothing");
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		RpgTestSupport.result("jobSurvivesDeath", "job=" + Jobs.of(reborn) + " trials=" + Jobs.trialsDone(reborn));
		helper.assertTrue(Jobs.of(reborn) == Job.PRIEST, "job after death " + Jobs.of(reborn));
		helper.assertTrue(Jobs.trialsDone(reborn).equals(Set.of(40)), "trials after death " + Jobs.trialsDone(reborn));
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void jobIsSaved(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 45;
		helper.assertTrue(Jobs.choose(player, Job.FIGHTER), "choice refused");
		helper.assertTrue(Jobs.grantTrial(player, 40), "trial refused");
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		player.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		ServerPlayer other = RpgTestSupport.realPlayer(helper);
		helper.assertTrue(Jobs.of(other) == Job.NONE, "the second player started with " + Jobs.of(other));
		other.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
		RpgTestSupport.result("jobIsSaved", "job=" + Jobs.of(other) + " trials=" + Jobs.trialsDone(other));
		helper.assertTrue(Jobs.of(other) == Job.FIGHTER, "loaded job " + Jobs.of(other));
		helper.assertTrue(Jobs.trialsDone(other).equals(Set.of(40)), "loaded trials " + Jobs.trialsDone(other));
		helper.succeed();
	}

	@GameTest
	public void bonusCountsEveryLevel(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 30;
		int dex = StatBonuses.bonus(player, Stat.DEX);
		int agi = StatBonuses.bonus(player, Stat.AGI);
		int str = StatBonuses.bonus(player, Stat.STR);
		player.experienceLevel = 10;
		helper.assertTrue(Jobs.choose(player, Job.ARCHER), "choice refused");
		player.experienceLevel = 30;
		int dexGain = StatBonuses.bonus(player, Stat.DEX) - dex;
		int agiGain = StatBonuses.bonus(player, Stat.AGI) - agi;
		int strGain = StatBonuses.bonus(player, Stat.STR) - str;
		RpgTestSupport.result("bonusCountsEveryLevel", "dex=" + dexGain + " agi=" + agiGain + " str=" + strGain);
		helper.assertTrue(dexGain == 60 + Jobs.CHANGE_BONUS && agiGain == 30 + Jobs.CHANGE_BONUS && strGain == 0, "gains dex=" + dexGain + " agi=" + agiGain + " str=" + strGain);

		player.experienceLevel = 1000;
		int capped = StatBonuses.bonus(player, Stat.DEX);
		int effective = StatBonuses.effective(player, Stat.DEX);
		helper.assertTrue(capped == StatSheet.CEILING, "bonus at level 1000 is " + capped);
		helper.assertTrue(effective <= StatBonuses.EFFECTIVE_CEILING, "effective at level 1000 is " + effective);
		helper.succeed();
	}

	@GameTest
	public void everyJobGrowsItsStats(final GameTestHelper helper) {
		Map<Job, Stat[]> expected = Map.of(
			Job.ARCHER, new Stat[] {Stat.DEX, Stat.AGI},
			Job.FIGHTER, new Stat[] {Stat.STR, Stat.VIT},
			Job.MAGE, new Stat[] {Stat.INT, Stat.DEX},
			Job.PRIEST, new Stat[] {Stat.VIT, Stat.INT}
		);
		for (Map.Entry<Job, Stat[]> entry : expected.entrySet()) {
			ServerPlayer player = RpgTestSupport.player(helper);
			player.experienceLevel = 20;
			int[] before = new int[Stat.values().length];
			for (Stat stat : Stat.values()) {
				before[stat.ordinal()] = StatBonuses.bonus(player, stat);
			}
			helper.assertTrue(Jobs.choose(player, entry.getKey()), entry.getKey() + " refused");
			for (Stat stat : Stat.values()) {
				int gain = StatBonuses.bonus(player, stat) - before[stat.ordinal()];
				int want = (stat == entry.getValue()[0] ? 40 : stat == entry.getValue()[1] ? 20 : 0)
					+ (entry.getKey().bonusStats().contains(stat) ? Jobs.CHANGE_BONUS : 0);
				helper.assertTrue(gain == want, entry.getKey() + " " + stat + " gain " + gain + " want " + want);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void trialRewardOnce(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 10;
		helper.assertTrue(!Jobs.grantTrial(player, 40), "a player without a job got a trial reward");
		helper.assertTrue(Jobs.choose(player, Job.ARCHER), "choice refused");
		player.experienceLevel = 1;
		int[] before = new int[Stat.values().length];
		for (Stat stat : Stat.values()) {
			before[stat.ordinal()] = StatBonuses.bonus(player, stat);
		}
		helper.assertTrue(Jobs.grantTrial(player, 40), "the first grant was refused");
		boolean second = Jobs.grantTrial(player, 40);
		boolean notTrial = Jobs.grantTrial(player, 50);
		boolean below = Jobs.grantTrial(player, 10);
		for (Stat stat : Stat.values()) {
			int gain = StatBonuses.bonus(player, stat) - before[stat.ordinal()];
			int want = stat == Stat.DEX ? 5 : 1;
			helper.assertTrue(gain == want, stat + " gain " + gain + " want " + want);
		}
		RpgTestSupport.result("trialRewardOnce", "second=" + second + " notTrial=" + notTrial + " below=" + below);
		helper.assertTrue(!second && !notTrial && !below, "second=" + second + " notTrial=" + notTrial + " below=" + below);
		helper.assertTrue(Jobs.grantTrial(player, 70) && Jobs.grantTrial(player, 100), "later trials were refused");
		int dexGain = StatBonuses.bonus(player, Stat.DEX) - before[Stat.DEX.ordinal()];
		helper.assertTrue(dexGain == 15, "three trials gave DEX " + dexGain);
		helper.assertTrue(Jobs.trialsDone(player).equals(Set.of(40, 70, 100)), "trials " + Jobs.trialsDone(player));
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("unchecked")
	public void shareWorks(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(Jobs.SHARE_KEY);
		helper.assertTrue(shared instanceof Map, "no map at " + Jobs.SHARE_KEY + ": " + shared);
		Map<String, Object> map = (Map<String, Object>) shared;
		Function<ServerPlayer, String> get = (Function<ServerPlayer, String>) map.get("get");
		BiFunction<ServerPlayer, String, Boolean> choose = (BiFunction<ServerPlayer, String, Boolean>) map.get("choose");
		Function<ServerPlayer, Boolean> grantItems = (Function<ServerPlayer, Boolean>) map.get("grantItems");
		BiFunction<ServerPlayer, Integer, Boolean> grantTrial = (BiFunction<ServerPlayer, Integer, Boolean>) map.get("grantTrial");
		Function<ServerPlayer, Set<Integer>> trialsDone = (Function<ServerPlayer, Set<Integer>>) map.get("trialsDone");
		helper.assertTrue(get != null && choose != null && grantItems != null && grantTrial != null && trialsDone != null,
			"missing functions in " + map.keySet());

		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 10;
		helper.assertTrue(get.apply(player).equals("none"), "get gave " + get.apply(player));
		helper.assertTrue(!grantItems.apply(player), "items went to a player without a job");
		helper.assertTrue(!choose.apply(player, "bard"), "an unknown job was accepted");
		helper.assertTrue(choose.apply(player, "mage"), "choose refused mage");
		helper.assertTrue(get.apply(player).equals("mage"), "get gave " + get.apply(player));
		helper.assertTrue(!choose.apply(player, "priest"), "a second choice was accepted");
		helper.assertTrue(grantTrial.apply(player, 40), "grantTrial refused");
		helper.assertTrue(trialsDone.apply(player).equals(Set.of(40)), "trialsDone " + trialsDone.apply(player));
		Boolean items = grantItems.apply(player);
		RpgTestSupport.result("shareWorks", "job=" + get.apply(player) + " trials=" + trialsDone.apply(player) + " items=" + items);
		helper.assertTrue(Boolean.TRUE.equals(items), "grantItems gave " + items);
		helper.assertTrue(player.getInventory().contains(stack -> stack.is(com.stella.rpg.job.JobItems.MAGE_STAFF)), "the mage got no staff");
		helper.succeed();
	}
}
