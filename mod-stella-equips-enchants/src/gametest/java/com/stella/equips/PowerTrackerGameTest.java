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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class PowerTrackerGameTest {
	private static final int FAR_DISTANCE = 10000;
	private static final int LEVEL_AT_TRAVEL_CEILING = 35;
	private static final int MOB_LEVEL_AT_TRAVEL_CEILING = 32;
	private static final double TOLERANCE = 1.0E-9;
	private static final int TICK_WINDOW = 11;
	private static final int HEARTBEAT_TICKS = 5;
	private static final int SCAN_WINDOW_TICKS = 45;
	private static final double COMBAT_POINTS_FOR_A_LEVEL = 400.0;
	private static final int INSIDE_LEVEL_BLOCKS = 500;
	private static final int NEXT_LEVEL_BLOCKS = 700;
	private static final float STARTING_HEALTH = 10.0F;
	private static final float VANILLA_MAX_HEALTH = 20.0F;
	private static final int SAVED_LEVEL = 30;
	private static final double SAVED_COMBAT_POINTS = 123.5;
	private static final Identifier MAX_HEALTH_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_max_health");
	private static final Identifier ARMOR_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_armor");
	private static final Identifier KNOCKBACK_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_knockback_resistance");
	private static final Identifier ATTACK_DAMAGE_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_attack_damage");
	private static final Identifier ATTACK_SPEED_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_attack_speed");
	private static final int THRESHOLD_BLOCKS = 500;
	private static final int BELOW_THRESHOLD_BLOCKS = 490;
	private static final int OSCILLATION_CROSSINGS = 8;
	private static final double PARTIAL_COMBAT_POINTS = 200.0;
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

	@GameTest
	public void walkingWithinOneLevelDoesNotHeal(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		placeAtDistance(player, helper, INSIDE_LEVEL_BLOCKS);
		PowerTracker.scanPlayer(player, config);
		int level = PowerState.get(player).level();
		player.setHealth(STARTING_HEALTH);
		float maxBefore = player.getMaxHealth();
		placeAtDistance(player, helper, INSIDE_LEVEL_BLOCKS + 100);
		boolean changed = PowerTracker.scanPlayer(player, config);
		float maxGained = player.getMaxHealth() - maxBefore;
		float healthNow = player.getHealth();
		helper.assertTrue(
			!changed,
			"walking 100 blocks inside level " + level + " reported a level change"
		);
		helper.assertTrue(
			level == PowerState.get(player).level(),
			"the level moved from " + level + " to " + PowerState.get(player).level()
		);
		helper.assertTrue(maxGained > 0.0F, "the maximum health did not rise at all");
		helper.assertTrue(
			healthNow == STARTING_HEALTH,
			"walking inside one level healed " + (healthNow - STARTING_HEALTH) + " health"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=noWalkHeal RESULT level={} maxGained={} healthBefore={} healthAfter={} changed={}",
			level, maxGained, STARTING_HEALTH, healthNow, changed
		);
		helper.succeed();
	}

	@GameTest
	public void aMaxHealthIncreaseRaisesTheCurrentHealthByTheSameAmount(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		placeAtDistance(player, helper, INSIDE_LEVEL_BLOCKS);
		PowerTracker.scanPlayer(player, config);
		player.setHealth(STARTING_HEALTH);
		float maxBefore = player.getMaxHealth();
		placeAtDistance(player, helper, NEXT_LEVEL_BLOCKS);
		boolean changed = PowerTracker.scanPlayer(player, config);
		float maxGained = player.getMaxHealth() - maxBefore;
		float healthGained = player.getHealth() - STARTING_HEALTH;
		helper.assertTrue(changed, "the level did not change at " + NEXT_LEVEL_BLOCKS + " blocks");
		helper.assertTrue(maxGained > 0.0F, "the maximum health did not rise at all");
		helper.assertTrue(
			Math.abs(healthGained - maxGained) < TOLERANCE,
			"the maximum rose by " + maxGained + " but the current health rose by " + healthGained
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=levelUpHeals RESULT level={} startingHealth={} healthAfter={} maxGained={} healthGained={}",
			PowerState.get(player).level(), STARTING_HEALTH, player.getHealth(), maxGained, healthGained
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theAttributesComeBackAfterARespawn(final GameTestHelper helper) {
		bundledConfig(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PowerState.set(player, new PowerState(SAVED_COMBAT_POINTS, SAVED_LEVEL));
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(
			player, false, Entity.RemovalReason.KILLED
		);
		helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
			double maxHealthModifier = modifierAmount(reborn, MAX_HEALTH_MODIFIER);
			double damageModifier = modifierAmount(reborn, ATTACK_DAMAGE_MODIFIER);
			double maxHealth = reborn.getMaxHealth();
			helper.assertTrue(
				maxHealthModifier > 0.0,
				"the scan did not re-apply the max health modifier after a respawn"
			);
			helper.assertTrue(
				damageModifier > 0.0,
				"the scan did not re-apply the attack damage modifier after a respawn"
			);
			helper.assertTrue(maxHealth > VANILLA_MAX_HEALTH, "the respawned player kept " + maxHealth + " maximum health");
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=respawnReentry RESULT maxHealth={} maxHealthModifier={} damageModifier={} level={}",
				maxHealth, maxHealthModifier, damageModifier, PowerState.get(reborn).level()
			);
			helper.succeed();
		});
	}

	@GameTest
	public void theAttributesFollowThePlayerIntoTheNether(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the test server holds no nether level");
		player.setServerLevel(nether);
		BlockPos netherSpawn = nether.getRespawnData().pos();
		player.setPos(netherSpawn.getX() + 100.0, 64.0, netherSpawn.getZ());
		boolean changed = PowerTracker.scanPlayer(player, config);
		double scaled = PowerCurve.scaledDistance(
			player.getX(), player.getZ(), netherSpawn.getX(), netherSpawn.getZ(), Level.NETHER.identifier(), config
		);
		long expectedPoints = PowerCurve.distancePoints(scaled, config) + PowerCurve.depthPoints(player.getY(), config);
		int expectedLevel = PowerCurve.levelFor(expectedPoints, config);
		double expectedHealth = config.attributes().distance().maxHealthPerPoint() * expectedPoints;
		double healthModifier = modifierAmount(player, MAX_HEALTH_MODIFIER);
		helper.assertTrue(changed, "the first nether scan reported no level change");
		helper.assertTrue(
			scaled == 800.0,
			"100 nether blocks scaled to " + scaled + " instead of 800"
		);
		helper.assertTrue(
			PowerState.get(player).level() == expectedLevel,
			"the nether level was " + PowerState.get(player).level() + " instead of " + expectedLevel
		);
		helper.assertTrue(
			Math.abs(healthModifier - expectedHealth) < TOLERANCE,
			"the nether max health modifier was " + healthModifier + " instead of " + expectedHealth
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=netherReentry RESULT dimension={} blocks=100 scaledDistance={} points={} level={} maxHealthModifier={}",
			Level.NETHER.identifier(), scaled, expectedPoints, expectedLevel, healthModifier
		);
		helper.succeed();
	}

	@GameTest
	public void theAttributesFollowAConfigReload(final GameTestHelper helper) {
		PowerConfig before = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		placeAtDistance(player, helper, INSIDE_LEVEL_BLOCKS);
		PowerTracker.scanPlayer(player, before);
		double beforeReload = modifierAmount(player, MAX_HEALTH_MODIFIER);
		Path file;
		PowerConfig after;
		try {
			file = Files.createTempDirectory("power-reload-config").resolve(PowerConfigStore.FILE_NAME);
			Files.writeString(
				file,
				PowerConfigStore.DEFAULT_JSON.replace("\"max_health_per_point\": 0.5", "\"max_health_per_point\": 1.0"),
				StandardCharsets.UTF_8
			);
			after = PowerConfigStore.loadFrom(file);
		} catch (IOException e) {
			helper.assertTrue(false, "could not stage the reload config: " + e.getMessage());
			return;
		}

		PowerTracker.scanPlayer(player, after);
		double afterReload = modifierAmount(player, MAX_HEALTH_MODIFIER);
		PowerConfigStore.load();
		helper.assertTrue(beforeReload > 0.0, "the first scan applied no max health modifier");
		helper.assertTrue(
			Math.abs(afterReload - beforeReload * 2.0) < TOLERANCE,
			"the reload changed the modifier from " + beforeReload + " to " + afterReload + " instead of doubling it"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=reloadReentry RESULT rateBefore={} rateAfter={} modifierBefore={} modifierAfter={}",
			before.attributes().distance().maxHealthPerPoint(), after.attributes().distance().maxHealthPerPoint(),
			beforeReload, afterReload
		);
		helper.succeed();
	}

	@GameTest
	public void theAttributesComeBackAfterARelogin(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer original = mockPlayer(helper);
		placeAtDistance(original, helper, INSIDE_LEVEL_BLOCKS);
		PowerState.set(original, new PowerState(SAVED_COMBAT_POINTS, 1));
		PowerTracker.scanPlayer(original, config);
		double originalModifier = modifierAmount(original, MAX_HEALTH_MODIFIER);
		TagValueOutput output = TagValueOutput.createWithContext(
			ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
		);
		original.saveWithoutId(output);
		CompoundTag tag = output.buildResult();
		ServerPlayer rejoined = mockPlayer(helper);
		rejoined.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		double beforeScan = modifierAmount(rejoined, MAX_HEALTH_MODIFIER);
		int levelBeforeScan = PowerState.get(rejoined).level();
		PowerTracker.scanPlayer(rejoined, config);
		double afterScan = modifierAmount(rejoined, MAX_HEALTH_MODIFIER);
		helper.assertTrue(originalModifier > 0.0, "the original scan applied no max health modifier");
		helper.assertTrue(
			Math.abs(beforeScan - originalModifier) < TOLERANCE,
			"the re-login carried a max health modifier of " + beforeScan + " instead of " + originalModifier
		);
		helper.assertTrue(
			Math.abs(afterScan - beforeScan) < TOLERANCE,
			"the scan after the re-login moved the modifier from " + beforeScan + " to " + afterScan
		);
		helper.assertTrue(
			PowerState.get(rejoined).level() == PowerState.get(original).level(),
			"the reloaded player read level " + PowerState.get(rejoined).level()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=reloginReentry RESULT levelAfterLoad={} modifierAfterLoad={} modifierAfterScan={} originalModifier={} savedPoints={}",
			levelBeforeScan, beforeScan, afterScan, originalModifier, PowerState.get(rejoined).combatPoints()
		);
		helper.succeed();
	}

	@GameTest
	public void aTravelOnlyPlayerGetsSurvivabilityAndNoOutput(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		placeAtDistance(player, helper, FAR_DISTANCE);
		PowerTracker.scanPlayer(player, config);
		double health = modifierAmount(player, MAX_HEALTH_MODIFIER);
		double armor = modifierAmount(player, ARMOR_MODIFIER);
		double knockback = modifierAmount(player, KNOCKBACK_MODIFIER);
		double damage = modifierAmount(player, ATTACK_DAMAGE_MODIFIER);
		double speed = modifierAmount(player, ATTACK_SPEED_MODIFIER);
		helper.assertTrue(
			health > 0.0 && armor > 0.0 && knockback > 0.0,
			"a travelled player got health " + health + ", armor " + armor + ", knockback " + knockback
		);
		helper.assertTrue(
			damage == 0.0 && speed == 0.0,
			"travel alone paid damage " + damage + " and speed " + speed + " with no kills"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=travelOnly RESULT blocks={} health={} armor={} knockback={} damage={} speed={} level={}",
			FAR_DISTANCE, health, armor, knockback, damage, speed, PowerState.get(player).level()
		);
		helper.succeed();
	}

	@GameTest
	public void theSameCombatPaysTheSameOutputWhereverThePlayerStands(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer home = mockPlayer(helper);
		ServerPlayer travelled = mockPlayer(helper);
		PowerState.set(home, new PowerState(PARTIAL_COMBAT_POINTS, 1));
		PowerState.set(travelled, new PowerState(PARTIAL_COMBAT_POINTS, 1));
		placeAtDistance(home, helper, 0);
		placeAtDistance(travelled, helper, FAR_DISTANCE);
		PowerTracker.scanPlayer(home, config);
		PowerTracker.scanPlayer(travelled, config);
		double homeDamage = modifierAmount(home, ATTACK_DAMAGE_MODIFIER);
		double travelledDamage = modifierAmount(travelled, ATTACK_DAMAGE_MODIFIER);
		double homeSpeed = modifierAmount(home, ATTACK_SPEED_MODIFIER);
		double travelledSpeed = modifierAmount(travelled, ATTACK_SPEED_MODIFIER);
		double homeHealth = modifierAmount(home, MAX_HEALTH_MODIFIER);
		double travelledHealth = modifierAmount(travelled, MAX_HEALTH_MODIFIER);
		helper.assertTrue(homeDamage > 0.0, "the combat points paid no damage at home");
		helper.assertTrue(
			Math.abs(travelledDamage - homeDamage) < TOLERANCE && Math.abs(travelledSpeed - homeSpeed) < TOLERANCE,
			"travel changed the output: damage " + homeDamage + " to " + travelledDamage
				+ ", speed " + homeSpeed + " to " + travelledSpeed
		);
		helper.assertTrue(
			travelledHealth > homeHealth,
			"travel did not raise survivability: health " + homeHealth + " to " + travelledHealth
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=outPutIsPositionFree RESULT combatPoints={} damageHome={} damageTravelled={} speedHome={} speedTravelled={} healthHome={} healthTravelled={}",
			PARTIAL_COMBAT_POINTS, homeDamage, travelledDamage, homeSpeed, travelledSpeed, homeHealth, travelledHealth
		);
		helper.succeed();
	}

	@GameTest
	public void walkingBackAndForthAcrossAThresholdHealsNothing(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		placeAtDistance(player, helper, THRESHOLD_BLOCKS);
		PowerTracker.scanPlayer(player, config);
		int level = PowerState.get(player).level();
		player.setHealth(STARTING_HEALTH);
		int crossings = 0;
		for (int round = 0; round < OSCILLATION_CROSSINGS / 2; round++) {
			placeAtDistance(player, helper, BELOW_THRESHOLD_BLOCKS);
			if (PowerTracker.scanPlayer(player, config)) {
				crossings++;
			}

			placeAtDistance(player, helper, THRESHOLD_BLOCKS);
			if (PowerTracker.scanPlayer(player, config)) {
				crossings++;
			}
		}

		float healthAfter = player.getHealth();
		helper.assertTrue(
			crossings == OSCILLATION_CROSSINGS,
			"the walk crossed the threshold " + crossings + " times instead of " + OSCILLATION_CROSSINGS
		);
		helper.assertTrue(
			healthAfter == STARTING_HEALTH,
			"walking back and forth across level " + level + " healed " + (healthAfter - STARTING_HEALTH) + " health"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=noOscillationHeal RESULT level={} crossings={} healthBefore={} healthAfter={}",
			level, crossings, STARTING_HEALTH, healthAfter
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

	private static void placeAtDistance(final ServerPlayer player, final GameTestHelper helper, final int blocks) {
		BlockPos spawn = spawn(helper);
		player.setPos(spawn.getX() + blocks, 64.0, spawn.getZ());
	}

	private static double modifierAmount(final ServerPlayer player, final Identifier id) {
		AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
		if (id.equals(ATTACK_DAMAGE_MODIFIER)) {
			instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
		} else if (id.equals(ATTACK_SPEED_MODIFIER)) {
			instance = player.getAttribute(Attributes.ATTACK_SPEED);
		} else if (id.equals(ARMOR_MODIFIER)) {
			instance = player.getAttribute(Attributes.ARMOR);
		} else if (id.equals(KNOCKBACK_MODIFIER)) {
			instance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		}

		if (instance == null) {
			return 0.0;
		}

		AttributeModifier modifier = instance.getModifier(id);
		return modifier == null ? 0.0 : modifier.amount();
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
