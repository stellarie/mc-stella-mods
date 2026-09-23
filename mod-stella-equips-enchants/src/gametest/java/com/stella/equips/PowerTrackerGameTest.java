package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerCurve;
import com.stella.equips.power.PowerState;
import com.stella.equips.power.PowerTracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;

public class PowerTrackerGameTest {
	private static final int FAR_DISTANCE = 10000;
	private static final int LEVEL_AT_TRAVEL_CEILING = 35;
	private static final int MOB_LEVEL_AT_TRAVEL_CEILING = 32;
	private static final double TOLERANCE = 1.0E-9;
	private static final int TICK_WINDOW = 11;
	private static final int HEARTBEAT_TICKS = 5;
	private static final int SCAN_WINDOW_TICKS = 45;
	private static final double COMBAT_POINTS_FOR_A_LEVEL = 400.0;
	private static final String LEVEL_LINE = "power level";
	private static final String KILL_LINE = "power kill";
	private static final String HEARTBEAT_LINE = "power heartbeat";

	@GameTest
	public void theTrackerIsRegisteredAndRunsOnTheRealServerTick(final GameTestHelper helper) {
		boolean registered = PowerTracker.isRegistered();
		int before = PowerTracker.serverTicks();
		helper.runAfterDelay(10, () -> {
			int after = PowerTracker.serverTicks();
			helper.assertTrue(registered, "the tracker did not report that it is registered");
			helper.assertTrue(
				after > before,
				"the real server tick advanced the tracker by " + (after - before) + " ticks"
			);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=trackerRegistered RESULT registered={} ticksBefore={} ticksAfter={} advanced={}",
				registered, before, after, after - before
			);
			helper.succeed();
		});
	}

	@GameTest
	public void walkingFarRaisesTheLevelAndTheHealthBar(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		float vanillaMax = player.getMaxHealth();
		placeFarFromSpawn(player, helper);
		boolean changed = PowerTracker.scanPlayer(player, config);
		int level = PowerState.get(player).level();
		float max = player.getMaxHealth();
		float health = player.getHealth();
		helper.assertTrue(changed, "walking " + FAR_DISTANCE + " blocks reported no level change");
		helper.assertTrue(
			level == LEVEL_AT_TRAVEL_CEILING,
			"walking " + FAR_DISTANCE + " blocks reached level " + level + " instead of " + LEVEL_AT_TRAVEL_CEILING
		);
		helper.assertTrue(
			max >= vanillaMax + 1000.0F && max <= vanillaMax + 1008.0F,
			"the maximum health after the walk was " + max + " instead of about " + (vanillaMax + 1004.0F)
		);
		helper.assertTrue(health > vanillaMax, "the health bar stayed at " + health);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=walkRaisesPower RESULT blocks={} level={} vanillaMax={} maxHealth={} health={} line={}",
			FAR_DISTANCE, level, vanillaMax, max, health, PowerRecorder.last(LEVEL_LINE)
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theRealServerTickRaisesTheLevelFromCombatPoints(final GameTestHelper helper) {
		PowerRecorder.install();
		bundledConfig(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int levelBefore = PowerState.get(player).level();
		double damageBefore = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getValue();
		PowerState.set(player, new PowerState(COMBAT_POINTS_FOR_A_LEVEL, levelBefore));
		int linesBefore = PowerRecorder.count(LEVEL_LINE);
		helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
			PowerState after = PowerState.get(player);
			double damageAfter = player.getAttribute(
				net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE
			).getValue();
			int linesAfter = PowerRecorder.count(LEVEL_LINE);
			helper.assertTrue(
				after.level() > levelBefore,
				"the server tick left the level at " + after.level() + " after " + COMBAT_POINTS_FOR_A_LEVEL + " combat points"
			);
			helper.assertTrue(
				damageAfter > damageBefore,
				"the scan left the attack damage at " + damageAfter + " instead of above " + damageBefore
			);
			helper.assertTrue(
				linesAfter > linesBefore, "the server tick scan wrote no level line"
			);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=tickRaisesPower RESULT levelBefore={} levelAfter={} damageBefore={} damageAfter={} levelLines={} line={}",
				levelBefore, after.level(), damageBefore, damageAfter, linesAfter - linesBefore,
				PowerRecorder.last(LEVEL_LINE)
			);
			helper.succeed();
		});
	}

	@GameTest
	public void theScanLogsOneLinePerLevelChangeAndNothingWhenTheLevelHolds(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		placeFarFromSpawn(player, helper);
		boolean walkedFar = PowerTracker.scanPlayer(player, config);
		int levelFar = PowerState.get(player).level();
		int linesAfterFirst = PowerRecorder.count(LEVEL_LINE);
		boolean unchanged = PowerTracker.scanPlayer(player, config);
		int linesAfterSecond = PowerRecorder.count(LEVEL_LINE);
		placeAtSpawn(player, helper);
		boolean walkedHome = PowerTracker.scanPlayer(player, config);
		int levelHome = PowerState.get(player).level();
		int linesAfterThird = PowerRecorder.count(LEVEL_LINE);
		helper.assertTrue(walkedFar, "the first scan did not report a level change");
		helper.assertTrue(
			levelFar == LEVEL_AT_TRAVEL_CEILING,
			"the far scan read level " + levelFar + " instead of " + LEVEL_AT_TRAVEL_CEILING
		);
		helper.assertTrue(!unchanged, "the second scan reported a level change with nothing changed");
		helper.assertTrue(walkedHome, "the scan after walking home did not report a level change");
		helper.assertTrue(levelHome == 1, "walking home read level " + levelHome + " instead of 1");
		helper.assertTrue(linesAfterFirst >= 1, "the far scan wrote no level line");
		helper.assertTrue(
			linesAfterSecond == linesAfterFirst,
			"the unchanged scan wrote " + (linesAfterSecond - linesAfterFirst) + " lines"
		);
		helper.assertTrue(
			linesAfterThird == linesAfterSecond + 1,
			"the walk home wrote " + (linesAfterThird - linesAfterSecond) + " lines instead of one"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=scanLogsLevelChange RESULT levelFar={} levelHome={} linesAfterFarScan={} unchangedScanLines={} homeScanLines={} lastLine={}",
			levelFar, levelHome, linesAfterFirst, linesAfterSecond - linesAfterFirst,
			linesAfterThird - linesAfterSecond, PowerRecorder.last(LEVEL_LINE)
		);
		helper.succeed();
	}

	@GameTest
	public void theTrackerHeartbeatsEveryFiveTicksAndNeverEveryTick(final GameTestHelper helper) {
		PowerRecorder.install();
		bundledConfig(helper);
		int firstTick = PowerTracker.serverTicks();
		helper.runAfterDelay(TICK_WINDOW, () -> {
			int lastTick = PowerTracker.serverTicks();
			int heartbeats = 0;
			int offCadence = 0;
			int duplicated = 0;
			int previousTick = Integer.MIN_VALUE;
			for (String line : PowerRecorder.matching(HEARTBEAT_LINE)) {
				int tick = heartbeatTick(line);
				if (tick <= firstTick || tick > lastTick) {
					continue;
				}

				heartbeats++;
				if (tick % HEARTBEAT_TICKS != 0) {
					offCadence++;
				}

				if (tick == previousTick) {
					duplicated++;
				}

				previousTick = tick;
			}

			int expected = (lastTick / HEARTBEAT_TICKS) - (firstTick / HEARTBEAT_TICKS);
			helper.assertTrue(
				heartbeats <= expected,
				TICK_WINDOW + " ticks carried " + heartbeats + " heartbeats, above the cadence of " + expected
			);
			helper.assertTrue(
				heartbeats >= expected - 1,
				TICK_WINDOW + " ticks carried " + heartbeats + " heartbeats, below the cadence of " + expected
					+ " by more than one replaced heartbeat"
			);
			helper.assertTrue(offCadence == 0, offCadence + " heartbeats landed outside the 5 tick cadence");
			helper.assertTrue(duplicated == 0, duplicated + " heartbeats shared a tick");
			helper.assertTrue(
				heartbeats < TICK_WINDOW,
				TICK_WINDOW + " ticks wrote " + heartbeats + " heartbeat lines: the tracker logs every tick"
			);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=heartbeatCadence RESULT ticks={} firstTick={} lastTick={} heartbeats={} expected={} offCadence={} duplicated={}",
				TICK_WINDOW, firstTick, lastTick, heartbeats, expected, offCadence, duplicated
			);
			helper.succeed();
		});
	}

	@GameTest
	public void theKillHookPaysForAKillAgainstTheMobLevel(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		placeFarFromSpawn(player, helper);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.setPos(player.getX(), 64.0, player.getZ());
		int linesBefore = PowerRecorder.count(KILL_LINE);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		double awarded = PowerState.get(player).combatPoints();
		int killLines = PowerRecorder.count(KILL_LINE) - linesBefore;
		String line = PowerRecorder.last(KILL_LINE);
		double expected = config.kills().pointsPerKill() * PowerCurve.xpMultiplier(
			MOB_LEVEL_AT_TRAVEL_CEILING, LEVEL_AT_TRAVEL_CEILING, config
		);
		helper.assertTrue(
			Math.abs(awarded - expected) < TOLERANCE,
			"the kill paid " + awarded + " points instead of " + expected
		);
		helper.assertTrue(killLines == 1, "one kill wrote " + killLines + " kill lines");
		helper.assertTrue(
			line.contains("mobLevel=" + MOB_LEVEL_AT_TRAVEL_CEILING)
				&& line.contains("playerLevel=" + LEVEL_AT_TRAVEL_CEILING)
				&& line.contains("multiplier="),
			"the kill line does not name the levels and the multiplier: " + line
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=killHookPays RESULT awarded={} expected={} killLines={} killLine={}",
			awarded, expected, killLines, line
		);
		helper.succeed();
	}

	@GameTest
	public void aBossPaysFourTimesTheBase(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		placeFarFromSpawn(player, helper);
		int linesBefore = PowerRecorder.count(KILL_LINE);
		WitherBoss wither = new WitherBoss(EntityTypes.WITHER, helper.getLevel());
		wither.setPos(player.getX(), 64.0, player.getZ());
		PowerTracker.onDeath(wither, helper.getLevel().damageSources().playerAttack(player));
		double awarded = PowerState.get(player).combatPoints();
		double expected = config.kills().pointsPerBoss() * PowerCurve.xpMultiplier(
			MOB_LEVEL_AT_TRAVEL_CEILING, LEVEL_AT_TRAVEL_CEILING, config
		);
		helper.assertTrue(
			Math.abs(awarded - expected) < TOLERANCE,
			"the boss paid " + awarded + " points instead of " + expected
		);
		helper.assertTrue(
			config.kills().isBoss(Identifier.fromNamespaceAndPath("minecraft", "wither")),
			"the bundled document does not treat the wither as a boss"
		);
		helper.assertTrue(
			PowerRecorder.count(KILL_LINE) - linesBefore == 1, "the boss kill wrote no line or more than one"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bossHookPays RESULT mob={} awarded={} expected={} killLine={}",
			EntityTypes.WITHER, awarded, expected, PowerRecorder.last(KILL_LINE)
		);
		helper.succeed();
	}

	@GameTest
	public void theKillRewardScalesWithTheMobLevel(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		double farFight = PowerCurve.xpMultiplier(MOB_LEVEL_AT_TRAVEL_CEILING, LEVEL_AT_TRAVEL_CEILING, config);
		double strongMob = PowerCurve.xpMultiplier(MOB_LEVEL_AT_TRAVEL_CEILING, 1, config);
		double weakMob = PowerCurve.xpMultiplier(0, config.levelCap(), config);
		helper.assertTrue(
			strongMob > farFight && farFight > weakMob,
			"the reward scale is not ordered: weak=" + weakMob + " evenFight=" + farFight + " strong=" + strongMob
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=rewardScale RESULT weakMobMultiplier={} evenFightMultiplier={} strongMobMultiplier={}",
			weakMob, farFight, strongMob
		);
		helper.succeed();
	}

	private static PowerConfig bundledConfig(final GameTestHelper helper) {
		try {
			Path file = Files.createTempDirectory("power-live-config").resolve(PowerConfigStore.FILE_NAME);
			Files.writeString(file, PowerConfigStore.DEFAULT_JSON, StandardCharsets.UTF_8);
			return PowerConfigStore.loadFrom(file);
		} catch (IOException e) {
			helper.assertTrue(false, "could not stage the live config: " + e.getMessage());
			return PowerFixtures.acceptanceConfig();
		}
	}

	private static void placeFarFromSpawn(final ServerPlayer player, final GameTestHelper helper) {
		BlockPos spawn = spawn(helper);
		player.setPos(spawn.getX() + FAR_DISTANCE, 64.0, spawn.getZ());
	}

	private static void placeAtSpawn(final ServerPlayer player, final GameTestHelper helper) {
		BlockPos spawn = spawn(helper);
		player.setPos(spawn.getX(), 64.0, spawn.getZ());
	}

	private static BlockPos spawn(final GameTestHelper helper) {
		return helper.getLevel().getRespawnData().pos();
	}

	private static ServerPlayer mockPlayer(final GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	private static int heartbeatTick(final String line) {
		int start = line.indexOf("tick=");
		if (start < 0) {
			return -1;
		}

		int end = start + "tick=".length();
		while (end < line.length() && Character.isDigit(line.charAt(end))) {
			end++;
		}

		return Integer.parseInt(line.substring(start + "tick=".length(), end));
	}
}
