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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class PowerTrackerGameTest {
	private static final int FAR_DISTANCE = 10000;
	private static final double TOLERANCE = 1.0E-9;
	private static final int TICK_WINDOW = 11;
	private static final int HEARTBEAT_TICKS = 5;
	private static final int SCAN_WINDOW_TICKS = 45;
	private static final double POINTS_AT_CAP = 6275.0;
	private static final double BANKED_POINTS = 1000.0;
	private static final double DEATH_LOSS = 0.05;
	private static final int SAVED_LEVEL = 30;
	private static final double SAVED_COMBAT_POINTS = 123.5;
	private static final Identifier MAX_HEALTH_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_max_health");
	private static final Identifier ARMOR_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_armor");
	private static final Identifier ATTACK_DAMAGE_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_attack_damage");
	private static final String LEVEL_LINE = "power level";
	private static final String KILL_LINE = "power kill";
	private static final String DEATH_LINE = "power death";
	private static final String HEARTBEAT_LINE = "power heartbeat";

	@GameTest
	public void theTrackerIsRegisteredAndRunsOnTheRealServerTick(final GameTestHelper helper) {
		boolean registered = PowerTracker.isRegistered();
		int before = PowerTracker.serverTicks();
		helper.runAfterDelay(10, () -> {
			int after = PowerTracker.serverTicks();
			helper.assertTrue(registered, "the tracker did not report that it is registered");
			helper.assertTrue(after > before, "the real server tick advanced the tracker by " + (after - before));
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=trackerRegistered RESULT registered={} ticksBefore={} ticksAfter={} advanced={}",
				registered, before, after, after - before
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void theRealServerTickAppliesTheLevelFromBankedPoints(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PowerState.set(player, new PowerState(POINTS_AT_CAP, PowerState.FIRST_LEVEL));
		helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
			int level = PowerState.get(player).level();
			double damage = modifierAmount(player, ATTACK_DAMAGE_MODIFIER);
			helper.assertTrue(
				level == config.levelCap(),
				POINTS_AT_CAP + " banked points reached level " + level + " instead of " + config.levelCap()
			);
			helper.assertTrue(
				Math.abs(damage - PowerFixtures.ATTACK_DAMAGE_AT_CAP) < TOLERANCE,
				"the tick scan left the attack damage at " + damage
			);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=tickAppliesPoints RESULT points={} level={} damage={} line={}",
				POINTS_AT_CAP, level, damage, PowerRecorder.last(LEVEL_LINE)
			);
			helper.succeed();
		});
	}

	@GameTest
	public void theScanLogsOneLinePerLevelChangeAndNothingWhenTheLevelHolds(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		int linesBefore = PowerRecorder.count(LEVEL_LINE);
		boolean atZero = PowerTracker.scanPlayer(player, config);
		int linesAtZero = PowerRecorder.count(LEVEL_LINE);
		PowerState.set(player, new PowerState(POINTS_AT_CAP, PowerState.get(player).level()));
		boolean atCap = PowerTracker.scanPlayer(player, config);
		int linesAtCap = PowerRecorder.count(LEVEL_LINE);
		boolean repeated = PowerTracker.scanPlayer(player, config);
		int linesRepeated = PowerRecorder.count(LEVEL_LINE);
		helper.assertTrue(!atZero, "a scan at the starting level reported a change");
		helper.assertTrue(atCap, "the scan at the cap reported no level change");
		helper.assertTrue(!repeated, "a repeated scan reported a level change with nothing changed");
		helper.assertTrue(
			linesAtZero == linesBefore && linesAtCap == linesAtZero + 1 && linesRepeated == linesAtCap,
			"three scans wrote " + (linesAtZero - linesBefore) + ", " + (linesAtCap - linesAtZero) + " and "
				+ (linesRepeated - linesAtCap) + " lines instead of one per level change"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=scanLogsLevelChange RESULT linesAtZero={} linesAtCap={} linesRepeated={} lastLine={}",
			linesAtZero - linesBefore, linesAtCap - linesAtZero, linesRepeated - linesAtCap,
			PowerRecorder.last(LEVEL_LINE)
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
				TICK_WINDOW + " ticks carried " + heartbeats + " heartbeats, below the cadence of " + expected + " by more than one"
			);
			helper.assertTrue(offCadence == 0, offCadence + " heartbeats landed outside the 5 tick cadence");
			helper.assertTrue(duplicated == 0, duplicated + " heartbeats shared a tick");
			helper.assertTrue(heartbeats < TICK_WINDOW, TICK_WINDOW + " ticks wrote " + heartbeats + " heartbeats");
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=heartbeatCadence RESULT ticks={} firstTick={} lastTick={} heartbeats={} expected={} offCadence={} duplicated={}",
				TICK_WINDOW, firstTick, lastTick, heartbeats, expected, offCadence, duplicated
			);
			helper.succeed();
		});
	}

	@GameTest
	public void aKillPaysTheAwardOfItsMobLevel(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		int mobLevel = PowerTracker.mobLevel(zombie, config);
		int expected = expectedAward(mobLevel, config);
		PowerRecorder.install();
		int linesBefore = PowerRecorder.count(KILL_LINE);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		double points = PowerState.get(player).combatPoints();
		String line = PowerRecorder.last(KILL_LINE);
		helper.assertTrue(
			Math.abs(points - expected) < TOLERANCE,
			"the kill paid " + points + " points instead of " + expected + " for mob level " + mobLevel
		);
		helper.assertTrue(
			PowerRecorder.count(KILL_LINE) - linesBefore == 1, "one kill wrote no kill line or more than one"
		);
		helper.assertTrue(
			line.contains("mobLevel=" + mobLevel) && line.contains("award=" + expected),
			"the kill line does not name the mob level and the award: " + line
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=killAward RESULT mobLevel={} award={} points={} killLine={}",
			mobLevel, expected, points, line
		);
		helper.succeed();
	}

	@GameTest
	public void distanceAndDepthBuyNoPointsOrLevels(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer home = mockPlayer(helper);
		ServerPlayer travelled = mockPlayer(helper);
		PowerState.set(home, new PowerState(BANKED_POINTS, PowerState.FIRST_LEVEL));
		PowerState.set(travelled, new PowerState(BANKED_POINTS, PowerState.FIRST_LEVEL));
		placeAtDistance(home, helper, 0);
		placeAtDistance(travelled, helper, FAR_DISTANCE);
		PowerTracker.scanPlayer(home, config);
		PowerTracker.scanPlayer(travelled, config);
		PowerState homeState = PowerState.get(home);
		PowerState travelledState = PowerState.get(travelled);
		double homeHealth = modifierAmount(home, MAX_HEALTH_MODIFIER);
		double travelledHealth = modifierAmount(travelled, MAX_HEALTH_MODIFIER);
		helper.assertTrue(
			Math.abs(homeState.combatPoints() - travelledState.combatPoints()) < TOLERANCE,
			"the points differed: home " + homeState.combatPoints() + ", travelled " + travelledState.combatPoints()
		);
		helper.assertTrue(
			homeState.level() == travelledState.level(),
			"the level differed: home " + homeState.level() + ", travelled " + travelledState.level()
		);
		helper.assertTrue(
			Math.abs(homeHealth - travelledHealth) < TOLERANCE,
			"the maximum health differed: home " + homeHealth + ", travelled " + travelledHealth
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=positionFree RESULT pointsHome={} pointsTravelled={} levelHome={} levelTravelled={} healthHome={} healthTravelled={}",
			homeState.combatPoints(), travelledState.combatPoints(),
			homeState.level(), travelledState.level(), homeHealth, travelledHealth
		);
		helper.succeed();
	}

	@GameTest
	public void theLevelDoesNotFollowThePlayerAcrossAThreshold(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		PowerState.set(player, new PowerState(BANKED_POINTS, PowerState.FIRST_LEVEL));
		placeAtDistance(player, helper, 0);
		PowerTracker.scanPlayer(player, config);
		int atHome = PowerState.get(player).level();
		placeAtDistance(player, helper, FAR_DISTANCE);
		PowerTracker.scanPlayer(player, config);
		int farOut = PowerState.get(player).level();
		placeAtDistance(player, helper, 0);
		PowerTracker.scanPlayer(player, config);
		int backHome = PowerState.get(player).level();
		helper.assertTrue(
			atHome == farOut && farOut == backHome,
			"the level moved with the player: home " + atHome + ", far " + farOut + ", home again " + backHome
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=levelIgnoresPosition RESULT points={} levelHome={} levelFar={} levelHomeAgain={}",
			BANKED_POINTS, atHome, farOut, backHome
		);
		helper.succeed();
	}

	@GameTest
	public void aDeathRemovesFivePercentOfTheBankedPointsAndZeroStaysZero(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = bundledConfig(helper);
		ServerPlayer rich = mockPlayer(helper);
		PowerState.set(rich, new PowerState(BANKED_POINTS, PowerState.FIRST_LEVEL));
		int linesBefore = PowerRecorder.count(DEATH_LINE);
		PowerTracker.onDeath(rich, helper.getLevel().damageSources().genericKill());
		double afterDeath = PowerState.get(rich).combatPoints();
		double expected = BANKED_POINTS * (1.0 - DEATH_LOSS);
		helper.assertTrue(
			Math.abs(afterDeath - expected) < TOLERANCE,
			"a death left " + afterDeath + " points instead of " + expected
		);
		helper.assertTrue(
			PowerRecorder.count(DEATH_LINE) - linesBefore == 1, "one death wrote the wrong number of lines"
		);
		helper.assertTrue(
			PowerState.get(rich).level() == PowerCurve.levelFor((long) expected, config),
			"the level after the death was " + PowerState.get(rich).level() + " instead of the level of the reduced total"
		);
		ServerPlayer broke = mockPlayer(helper);
		PowerState.set(broke, new PowerState(0.0, PowerState.FIRST_LEVEL));
		PowerTracker.onDeath(broke, helper.getLevel().damageSources().genericKill());
		double afterBroke = PowerState.get(broke).combatPoints();
		helper.assertTrue(afterBroke == 0.0, "a death at zero points left " + afterBroke);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=deathLoss RESULT pointsBefore={} pointsAfter={} expected={} brokeAfter={} levelAfter={} line={}",
			BANKED_POINTS, afterDeath, expected, afterBroke, PowerState.get(rich).level(),
			PowerRecorder.last(DEATH_LINE)
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theAttributesComeBackAfterARespawn(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PowerState.set(player, new PowerState(POINTS_AT_CAP, SAVED_LEVEL));
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(
			player, false, Entity.RemovalReason.KILLED
		);
		helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
			double health = modifierAmount(reborn, MAX_HEALTH_MODIFIER);
			double damage = modifierAmount(reborn, ATTACK_DAMAGE_MODIFIER);
			int level = PowerState.get(reborn).level();
			helper.assertTrue(
				Math.abs(damage - PowerFixtures.ATTACK_DAMAGE_AT_CAP) < TOLERANCE,
				"the respawned player read attack damage " + damage
			);
			helper.assertTrue(
				Math.abs(health - PowerFixtures.HEALTH_GAIN_AT_CAP) < TOLERANCE,
				"the respawned player read maximum health gain " + health
			);
			helper.assertTrue(level == config.levelCap(), "the respawned player read level " + level);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=respawnReentry RESULT points={} level={} maxHealth={} damage={}",
				PowerState.get(reborn).combatPoints(), level, health, damage
			);
			helper.succeed();
		});
	}

	@GameTest
	public void theAttributesDoNotChangeWhenThePlayerChangesDimension(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		PowerState.set(player, new PowerState(BANKED_POINTS, PowerState.FIRST_LEVEL));
		PowerTracker.scanPlayer(player, config);
		double overworldHealth = modifierAmount(player, MAX_HEALTH_MODIFIER);
		int overworldLevel = PowerState.get(player).level();
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the test server holds no nether level");
		player.setServerLevel(nether);
		BlockPos netherSpawn = nether.getRespawnData().pos();
		player.setPos(netherSpawn.getX() + 1200.0, 64.0, netherSpawn.getZ());
		PowerTracker.scanPlayer(player, config);
		double netherHealth = modifierAmount(player, MAX_HEALTH_MODIFIER);
		int netherLevel = PowerState.get(player).level();
		helper.assertTrue(
			Math.abs(netherHealth - overworldHealth) < TOLERANCE,
			"the nether changed the maximum health from " + overworldHealth + " to " + netherHealth
		);
		helper.assertTrue(
			netherLevel == overworldLevel,
			"the nether changed the level from " + overworldLevel + " to " + netherLevel
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=dimensionReentry RESULT scaledNetherDistance={} healthOverworld={} healthNether={} levelOverworld={} levelNether={}",
			PowerCurve.scaledDistance(
				player.getX(), player.getZ(), netherSpawn.getX(), netherSpawn.getZ(), Level.NETHER.identifier(), config
			), overworldHealth, netherHealth, overworldLevel, netherLevel
		);
		helper.succeed();
	}

	@GameTest
	public void theAttributesFollowAConfigReload(final GameTestHelper helper) {
		PowerConfig before = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		PowerState.set(player, new PowerState(POINTS_AT_CAP, PowerState.FIRST_LEVEL));
		PowerTracker.scanPlayer(player, before);
		double beforeReload = modifierAmount(player, ARMOR_MODIFIER);
		PowerConfig after;
		try {
			Path file = Files.createTempDirectory("power-reload-config").resolve(PowerConfigStore.FILE_NAME);
			Files.writeString(
				file,
				PowerConfigStore.DEFAULT_JSON.replace("\"armor_at_cap\": 11.76", "\"armor_at_cap\": 23.52"),
				StandardCharsets.UTF_8
			);
			after = PowerConfigStore.loadFrom(file);
		} catch (IOException e) {
			helper.assertTrue(false, "could not stage the reload config: " + e.getMessage());
			return;
		}

		PowerTracker.scanPlayer(player, after);
		double afterReload = modifierAmount(player, ARMOR_MODIFIER);
		PowerConfigStore.load();
		helper.assertTrue(beforeReload > 0.0, "the first scan applied no armor modifier");
		helper.assertTrue(
			afterReload > beforeReload,
			"the reload left the modifier at " + afterReload + " instead of raising it above " + beforeReload
		);
		helper.assertTrue(
			Math.abs(afterReload - PowerFixtures.ARMOR_AT_CAP * 2.0) < TOLERANCE,
			"the reloaded modifier was " + afterReload
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=reloadReentry RESULT armorAtCapBefore={} armorAtCapAfter={} modifierBefore={} modifierAfter={}",
			before.attributes().armorAtCap(), after.attributes().armorAtCap(), beforeReload, afterReload
		);
		helper.succeed();
	}

	@GameTest
	public void theAttributesComeBackAfterARelogin(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer original = mockPlayer(helper);
		PowerState.set(original, new PowerState(BANKED_POINTS, PowerState.FIRST_LEVEL));
		PowerTracker.scanPlayer(original, config);
		double originalDamage = modifierAmount(original, ATTACK_DAMAGE_MODIFIER);
		TagValueOutput output = TagValueOutput.createWithContext(
			ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
		);
		original.saveWithoutId(output);
		CompoundTag tag = output.buildResult();
		ServerPlayer rejoined = mockPlayer(helper);
		rejoined.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		double afterLoad = modifierAmount(rejoined, ATTACK_DAMAGE_MODIFIER);
		PowerTracker.scanPlayer(rejoined, config);
		double afterScan = modifierAmount(rejoined, ATTACK_DAMAGE_MODIFIER);
		helper.assertTrue(originalDamage > 0.0, "the original scan applied no attack damage modifier");
		helper.assertTrue(
			Math.abs(afterLoad - originalDamage) < TOLERANCE,
			"the re-login carried " + afterLoad + " instead of " + originalDamage
		);
		helper.assertTrue(
			Math.abs(afterScan - afterLoad) < TOLERANCE,
			"the scan after the re-login moved the modifier from " + afterLoad + " to " + afterScan
		);
		helper.assertTrue(
			PowerState.get(rejoined).level() == PowerState.get(original).level(),
			"the reloaded player read level " + PowerState.get(rejoined).level()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=reloginReentry RESULT pointsAfterLoad={} damageAfterLoad={} damageAfterScan={} originalDamage={}",
			PowerState.get(rejoined).combatPoints(), afterLoad, afterScan, originalDamage
		);
		helper.succeed();
	}

	private static int expectedAward(final int mobLevel, final PowerConfig config) {
		return Math.max(config.award().minimum(), (int) Math.ceil(mobLevel * config.award().divisor()));
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

	private static void placeAtDistance(final ServerPlayer player, final GameTestHelper helper, final int blocks) {
		BlockPos spawn = helper.getLevel().getRespawnData().pos();
		player.setPos(spawn.getX() + blocks, 64.0, spawn.getZ());
	}

	private static double modifierAmount(final ServerPlayer player, final Identifier id) {
		var instance = id.equals(ATTACK_DAMAGE_MODIFIER)
			? player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
			: id.equals(ARMOR_MODIFIER)
				? player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
				: player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (instance == null) {
			return 0.0;
		}

		var modifier = instance.getModifier(id);
		return modifier == null ? 0.0 : modifier.amount();
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
