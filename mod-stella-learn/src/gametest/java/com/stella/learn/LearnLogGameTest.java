package com.stella.learn;

import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/** The mod writes a `[BEHAVIOR]` line for each thing it learns and each change it makes, so a server log can show both. */
public class LearnLogGameTest {
	private static final int LONG = 1200;
	private static final Pattern NEAR = Pattern.compile("near=(\\d+\\.\\d\\d)->(\\d+\\.\\d\\d)");
	private static final String ARCHER_CONFIG = """
		{
			"version": 1,
			"entities": { "minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } } },
			"roles": { "minecraft:skeleton": { "role": "ranged", "near": 5.0, "far": 15.0 } },
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	static {
		Rally.register();
		CalloutKits.register();
	}

	private static void record(final ServerPlayer player, final Habit habit, final double times) {
		for (int i = 0; i < times; i++) {
			Profiles.record(player, habit);
		}
	}

	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
		mob.setNoAi(true);
		return mob;
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void recordingAHabitLogsItWithItsNewValue(final GameTestHelper helper) {
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		record(player, Habit.BLOCK, 2);
		List<String> lines = LearnTestInit.linesWith(player.getUUID().toString(), "learnHabit RECORD", "habit=BLOCK");
		StellaLearn.LOGGER.info("[BEHAVIOR] test=recordingAHabitLogsItWithItsNewValue RESULT lines={}", lines.size());
		helper.assertTrue(lines.size() == 2, "two blocks wrote " + lines.size() + " lines");
		helper.assertTrue(lines.get(1).contains("value=2.00"), "the second line reads " + lines.get(1));
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aBandShiftLogsWhatTheBandWasAndWhatItBecame(final GameTestHelper helper) {
		LearnArena.floor(helper);
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(ARCHER_CONFIG));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		ServerPlayer player = LearnArena.player(helper, 32.5, 24.5);
		record(player, Habit.MELEE, LearnConfigStore.get().rushSaturation());
		Mob archer = helper.spawnMob(EntityTypes.SKELETON, 24, LearnArena.SPAWN_Y, 24).spawn();
		LearnArena.keepTargeting(helper, archer, player);
		String id = archer.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(
			helper, () -> !LearnTestInit.linesWith(id, "learnBand SHIFT").isEmpty(), 200, () -> {
				String line = LearnTestInit.linesWith(id, "learnBand SHIFT").get(0);
				Matcher matcher = NEAR.matcher(line);
				helper.assertTrue(matcher.find(), "no near band in " + line);
				double before = Double.parseDouble(matcher.group(1));
				double after = Double.parseDouble(matcher.group(2));
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aBandShiftLogsWhatTheBandWasAndWhatItBecame RESULT before={} after={}", before, after);
				helper.assertTrue(after > before + 1.0, "the line reads " + line);
				helper.assertTrue(line.contains(player.getUUID().toString()), "the line does not name the player: " + line);
				helper.succeed();
			}, () -> "no band shift was logged"
		));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aShieldWaitLogsBothEnds(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 30.5, 24.5);
		record(player, Habit.BLOCK, LearnConfigStore.get().shieldSaturation());
		LearnArena.raiseShield(player);
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 24).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		String id = zombie.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, zombie), () -> LearnArena.pollUntil(
			helper, () -> !LearnTestInit.linesWith(id, "learnShield WAIT").isEmpty(), 200, () -> {
				LearnArena.lowerShield(player);
				LearnArena.pollUntil(helper, () -> !LearnTestInit.linesWith(id, "learnShield RELEASE").isEmpty(), 200, () -> {
					String release = LearnTestInit.linesWith(id, "learnShield RELEASE").get(0);
					StellaLearn.LOGGER.info("[BEHAVIOR] test=aShieldWaitLogsBothEnds RESULT release={}", release);
					helper.assertTrue(release.contains("reason=dropped"), "the release reads " + release);
					helper.succeed();
				}, () -> "the wait's end was never logged");
			}, () -> "the wait's start was never logged"
		));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void takingCoverLogsTheWalkAndTheArrival(final GameTestHelper helper) {
		LearnArena.floor(helper);
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(16 + dx, y, 28 + dz, Blocks.STONE);
				}
			}
		}

		ServerPlayer player = LearnArena.player(helper, 24.5, 20.5);
		record(player, Habit.RANGED, LearnConfigStore.get().rangedSaturation());
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 40).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		String id = zombie.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, zombie), () -> LearnArena.pollUntil(
			helper, () -> !LearnTestInit.linesWith(id, "learnCover ARRIVE").isEmpty(), 800, () -> {
				helper.assertTrue(!LearnTestInit.linesWith(id, "learnCover WALK").isEmpty(), "an arrival was logged with no walk");
				StellaLearn.LOGGER.info("[BEHAVIOR] test=takingCoverLogsTheWalkAndTheArrival RESULT walks={}", LearnTestInit.linesWith(id, "learnCover WALK").size());
				helper.succeed();
			}, () -> "no cover arrival was logged"
		));
	}

	@GameTest(maxTicks = 1500, padding = LearnArena.PADDING)
	public void holdingAtAChokeLogsTheHoldAndWhyItWasLifted(final GameTestHelper helper) {
		LearnArena.floor(helper);
		for (int x = 0; x < 48; x++) {
			if (x == 24) {
				continue;
			}

			for (int y = 1; y <= 3; y++) {
				helper.setBlock(x, y, 18, Blocks.STONE);
			}
		}

		ServerPlayer player = LearnArena.player(helper, 24.5, 10.5);
		record(player, Habit.RETREAT, LearnConfigStore.get().retreatSaturation());
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 25).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		String id = zombie.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, zombie), () -> LearnArena.pollUntil(
			helper, () -> !LearnTestInit.linesWith(id, "learnChoke GO").isEmpty(), 600, () -> {
				String hold = LearnTestInit.linesWith(id, "learnChoke HOLD").get(0);
				String go = LearnTestInit.linesWith(id, "learnChoke GO").get(0);
				StellaLearn.LOGGER.info("[BEHAVIOR] test=holdingAtAChokeLogsTheHoldAndWhyItWasLifted RESULT hold={} go={}", hold, go);
				helper.assertTrue(hold.contains("group=" + LearnConfigStore.get().chokeGroup()), "the hold reads " + hold);
				helper.assertTrue(go.contains("reason=patience"), "the release reads " + go);
				helper.succeed();
			}, () -> "the hold's release was never logged"
		));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aRallyLogsItsSlotAndItsArrival(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 20.5);
		Mob caller = zombie(helper, 20, 16);
		Mob receiver = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 36).spawn();
		String id = receiver.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, new Callout(Rally.TYPE, caller.getUUID(), player.getUUID(), 1, 300, null), LearnConfig.defaults());
			helper.assertTrue(!LearnTestInit.linesWith(id, "learnRally SLOT").isEmpty(), "the slot was not logged");
			String slot = LearnTestInit.linesWith(id, "learnRally SLOT").get(0);
			helper.assertTrue(slot.contains("index=0") && slot.contains("slot="), "the slot line reads " + slot);
			LearnArena.pollUntil(helper, () -> !LearnTestInit.linesWith(id, "learnRally ARRIVE").isEmpty(), 500, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aRallyLogsItsSlotAndItsArrival RESULT slot={}", slot);
				helper.succeed();
			}, () -> "the arrival was never logged");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aCalloutLogsItsSendAndEveryAnswer(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob first = zombie(helper, 28, 20);
		Mob second = zombie(helper, 28, 24);
		Mob third = zombie(helper, 28, 28);
		String callerId = caller.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, caller, first, second, third), () -> {
			LearnConfig config = LearnConfig.defaults().withMaxResponders(1);
			CalloutBoard.send(caller, player, new Callout(Rally.TYPE, caller.getUUID(), player.getUUID(), 1, 100, null), config);
			List<String> sends = LearnTestInit.linesWith(callerId, "learnCallout SEND");
			List<String> answers = LearnTestInit.linesWith(callerId, "learnCallout ANSWER");
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aCalloutLogsItsSendAndEveryAnswer RESULT sends={} answers={}", sends.size(), answers.size());
			helper.assertTrue(sends.size() == 1, "the call wrote " + sends.size() + " send lines");
			helper.assertTrue(sends.get(0).contains("asked=3 accepted=1") && sends.get(0).contains("cap:2"), "the send line reads " + sends.get(0));
			helper.assertTrue(answers.size() == 3, "the call wrote " + answers.size() + " answer lines");
			helper.succeed();
		});
	}

	private static void expectChoice(final GameTestHelper helper, final String test, final String why, final Habit habit, final double times, final boolean hurt) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		if (habit != null) {
			record(player, habit, times);
		}

		Mob caller = zombie(helper, 20, 20);
		LearnArena.whenReady(helper, List.of(player, caller), () -> {
			caller.setHealth(hurt ? caller.getMaxHealth() * 0.3F : caller.getMaxHealth());
			CalloutSender.choose((net.minecraft.world.entity.monster.Monster) caller, player);
			List<String> lines = LearnTestInit.linesWith(caller.getUUID().toString(), "learnCallout CHOOSE");
			StellaLearn.LOGGER.info("[BEHAVIOR] test={} RESULT lines={}", test, lines.size());
			helper.assertTrue(lines.size() == 1 && lines.get(0).contains("why=" + why), "the choice lines were " + lines);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSenderLogsWhyItAskedForAHeal(final GameTestHelper helper) {
		expectChoice(helper, "theSenderLogsWhyItAskedForAHeal", "hurt", null, 0, true);
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSenderLogsWhyItAskedForADebuff(final GameTestHelper helper) {
		expectChoice(helper, "theSenderLogsWhyItAskedForADebuff", "turtle", Habit.BLOCK, LearnConfigStore.get().shieldSaturation(), false);
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSenderLogsWhyItAskedForABuff(final GameTestHelper helper) {
		expectChoice(helper, "theSenderLogsWhyItAskedForABuff", "rusher", Habit.MELEE, LearnConfigStore.get().rushSaturation(), false);
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSenderLogsWhyItJustRallied(final GameTestHelper helper) {
		expectChoice(helper, "theSenderLogsWhyItJustRallied", "default", null, 0, false);
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aKitEffectLogsWhatItDid(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob witch = helper.spawnMob(EntityTypes.WITCH, 28, LearnArena.SPAWN_Y, 20).spawn();
		witch.setNoAi(true);
		String witchId = witch.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, caller, witch), () -> {
			caller.setHealth(caller.getMaxHealth() / 2.0F);
			CalloutBoard.send(caller, player, new Callout(CalloutKits.HEAL, caller.getUUID(), player.getUUID(), 1, 100, null), LearnConfig.defaults());
			List<String> lines = LearnTestInit.linesWith(witchId, "learnCallout EFFECT", "heal");
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aKitEffectLogsWhatItDid RESULT lines={}", lines);
			helper.assertTrue(lines.size() == 1 && lines.get(0).contains("amount=" + LearnLog.num(CalloutKits.HEAL_AMOUNT)), "the effect lines were " + lines);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSummaryNamesEveryCounterAndPressure(final GameTestHelper helper) {
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		record(player, Habit.BLOCK, LearnConfigStore.get().shieldSaturation());
		record(player, Habit.MELEE, 3);
		String line = ProfileReport.line(player);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theSummaryNamesEveryCounterAndPressure RESULT line={}", line);
		for (String key : List.of("block=", "melee=", "ranged=", "retreat=", "rush=", "turtle=", "shooter=", "retreater=")) {
			helper.assertTrue(line.contains(key), "the summary lacks " + key + ": " + line);
		}

		helper.assertTrue(line.contains("turtle=1.00"), "a full turtle was not read as 1.00: " + line);
		helper.assertTrue(line.contains(player.getUUID().toString()), "the summary does not name the player: " + line);
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theCommandReportListsEachCounterOnItsOwnLine(final GameTestHelper helper) {
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		record(player, Habit.RANGED, 2);
		List<String> lines = ProfileReport.describe(player);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theCommandReportListsEachCounterOnItsOwnLine RESULT lines={}", lines.size());
		for (Habit habit : Habit.values()) {
			helper.assertTrue(lines.stream().anyMatch(l -> l.contains(habit.name())), "the report lacks " + habit + ": " + lines);
		}

		helper.assertTrue(lines.stream().anyMatch(l -> l.contains("RANGED") && l.contains("2.00")), "the ranged count is wrong: " + lines);
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSummaryTickLogsOnlyForAPlayerWhoHasLearnedSomething(final GameTestHelper helper) {
		ServerPlayer learned = LearnArena.player(helper, 24.5, 24.5);
		ServerPlayer fresh = LearnArena.player(helper, 20.5, 24.5);
		record(learned, Habit.MELEE, 1);
		boolean loggedLearned = LearnSummary.emit(learned);
		boolean loggedFresh = LearnSummary.emit(fresh);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theSummaryTickLogsOnlyForAPlayerWhoHasLearnedSomething RESULT learned={} fresh={}", loggedLearned, loggedFresh);
		helper.assertTrue(loggedLearned && !LearnTestInit.linesWith(learned.getUUID().toString(), "learnProfile SUMMARY").isEmpty(), "no summary for the player who fought");
		helper.assertTrue(!loggedFresh && LearnTestInit.linesWith(fresh.getUUID().toString(), "learnProfile SUMMARY").isEmpty(), "a summary was written for a player with an empty profile");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void loggingCanBeSwitchedOff(final GameTestHelper helper) {
		String marker = "marker=" + UUID.randomUUID();
		LearnLog.info(LearnConfig.defaults().withBehaviorLog(false), "learnTest", "MARK", marker);
		int silent = LearnTestInit.linesWith(marker).size();
		LearnLog.info(LearnConfig.defaults(), "learnTest", "MARK", marker);
		int loud = LearnTestInit.linesWith(marker).size();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=loggingCanBeSwitchedOff RESULT silent={} loud={}", silent, loud);
		helper.assertTrue(silent == 0, "a line was written with the log switched off");
		helper.assertTrue(loud == 1, "no line was written with the log on");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theConfigLoadLogsTheValuesInForce(final GameTestHelper helper) {
		LearnConfig before = LearnConfigStore.get();
		LearnConfigStore.load();
		LearnConfigStore.set(before);
		List<String> lines = LearnTestInit.linesWith("learnConfig LOAD", "callout_radius=" + LearnLog.num(before.calloutRadius()));
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theConfigLoadLogsTheValuesInForce RESULT lines={}", lines.size());
		helper.assertTrue(!lines.isEmpty(), "loading the config logged nothing");
		helper.succeed();
	}
}
