package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerTracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
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
	private static final int CONTROL_LEVEL = 20;
	private static final int VANILLA_TOTAL = 550;
	private static final float VANILLA_PROGRESS = 0.4F;
	private static final Identifier MAX_HEALTH_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_max_health");
	private static final Identifier ARMOR_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_armor");
	private static final Identifier ATTACK_DAMAGE_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_attack_damage");
	private static final Identifier KNOCKBACK_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_knockback_resistance");
	private static final Identifier ATTACK_SPEED_MODIFIER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_attack_speed");
	private static final String LEVEL_LINE = "power level";
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
	public void theRealServerTickAppliesTheVanillaLevel(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.experienceLevel = config.levelCap();
		helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
			double damage = modifierAmount(player, ATTACK_DAMAGE_MODIFIER);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=tickAppliesVanillaLevel RESULT level={} damage={} line={}",
				player.experienceLevel, damage, PowerRecorder.last(LEVEL_LINE)
			);
			helper.assertTrue(
				Math.abs(damage - config.attributes().attackDamageAtCap()) < TOLERANCE,
				"the tick scan left the attack damage at " + damage + " for vanilla level " + player.experienceLevel
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
		player.experienceLevel = config.levelCap();
		boolean atCap = PowerTracker.scanPlayer(player, config);
		int linesAtCap = PowerRecorder.count(LEVEL_LINE);
		boolean repeated = PowerTracker.scanPlayer(player, config);
		int linesRepeated = PowerRecorder.count(LEVEL_LINE);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=scanLogsLevelChange RESULT changedAtZero={} changedAtCap={} changedRepeated={} lines={}/{}/{} lastLine={}",
			atZero, atCap, repeated, linesAtZero - linesBefore, linesAtCap - linesAtZero, linesRepeated - linesAtCap,
			PowerRecorder.last(LEVEL_LINE)
		);
		helper.assertTrue(
			!atZero && atCap && !repeated
				&& linesAtZero == linesBefore && linesAtCap == linesAtZero + 1 && linesRepeated == linesAtCap,
			"three scans reported changes " + atZero + "/" + atCap + "/" + repeated + " and wrote "
				+ (linesAtZero - linesBefore) + ", " + (linesAtCap - linesAtZero) + " and "
				+ (linesRepeated - linesAtCap) + " lines instead of one per level change"
		);
		helper.succeed();
	}

	@GameTest
	public void theScanFollowsTheVanillaLevelAndStopsAtTheCap(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int cap = config.levelCap();
		int[] levels = {0, 1, cap / 2, cap, cap + 1};
		int wrong = 0;
		int xpMoved = 0;
		StringBuilder seen = new StringBuilder();
		for (int level : levels) {
			ServerPlayer player = mockPlayer(helper);
			player.experienceLevel = level;
			player.totalExperience = VANILLA_TOTAL;
			player.experienceProgress = VANILLA_PROGRESS;
			PowerTracker.scanPlayer(player, config);
			double[] actual = allModifiers(player);
			double[] expected = expectedModifiers(Math.min(level, cap), config);
			for (int index = 0; index < actual.length; index++) {
				wrong += Math.abs(actual[index] - expected[index]) > TOLERANCE ? 1 : 0;
			}

			xpMoved += player.experienceLevel != level || player.totalExperience != VANILLA_TOTAL
				|| player.experienceProgress != VANILLA_PROGRESS ? 1 : 0;
			seen.append(level).append("->health=").append(actual[0]).append(",damage=").append(actual[3]).append(' ');
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=scanFollowsVanillaLevel RESULT wrongModifiers={} xpMoved={} seen={}", wrong, xpMoved, seen
		);
		helper.assertTrue(
			wrong == 0 && xpMoved == 0,
			wrong + " modifiers missed their vanilla-level value and " + xpMoved + " scans moved vanilla XP: " + seen
		);
		helper.succeed();
	}

	@GameTest
	public void aKillLeavesVanillaXpAndModifiersLikeAControlWithoutTheKill(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer killer = playerAtLevel(helper, CONTROL_LEVEL);
		ServerPlayer control = playerAtLevel(helper, CONTROL_LEVEL);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), 1000.0F);
		PowerTracker.scanPlayer(killer, config);
		PowerTracker.scanPlayer(control, config);
		String difference = difference(killer, control);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=killMatchesVanilla RESULT zombieDead={} difference={} killer={} control={}",
			!zombie.isAlive(), difference, describe(killer), describe(control)
		);
		helper.assertTrue(!zombie.isAlive(), "the zombie survived, so the kill scenario did not run");
		helper.assertTrue(difference.isEmpty(), "a kill moved the killer away from the control: " + difference);
		helper.succeed();
	}

	@GameTest
	public void aDeathLeavesVanillaXpAndModifiersLikeAControlWithoutTheDeath(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer dead = playerAtLevel(helper, CONTROL_LEVEL);
		ServerPlayer control = playerAtLevel(helper, CONTROL_LEVEL);
		PowerTracker.scanPlayer(dead, config);
		PowerTracker.scanPlayer(control, config);
		ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(dead, helper.getLevel().damageSources().genericKill());
		PowerTracker.scanPlayer(dead, config);
		String difference = difference(dead, control);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=deathMatchesVanilla RESULT difference={} dead={} control={}",
			difference, describe(dead), describe(control)
		);
		helper.assertTrue(difference.isEmpty(), "a death moved the player away from the control: " + difference);
		helper.succeed();
	}

	@GameTest
	public void distanceAndDepthBuyNoLevels(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer home = playerAtLevel(helper, CONTROL_LEVEL);
		ServerPlayer travelled = playerAtLevel(helper, CONTROL_LEVEL);
		placeAtDistance(home, helper, 0);
		placeAtDistance(travelled, helper, FAR_DISTANCE);
		PowerTracker.scanPlayer(home, config);
		PowerTracker.scanPlayer(travelled, config);
		String difference = difference(home, travelled);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=positionFree RESULT difference={} home={} travelled={}",
			difference, describe(home), describe(travelled)
		);
		helper.assertTrue(difference.isEmpty(), "travelling changed the player: " + difference);
		helper.succeed();
	}

	@GameTest
	public void theModifiersDoNotFollowThePlayerAcrossAThreshold(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = playerAtLevel(helper, CONTROL_LEVEL);
		placeAtDistance(player, helper, 0);
		PowerTracker.scanPlayer(player, config);
		double atHome = modifierAmount(player, MAX_HEALTH_MODIFIER);
		placeAtDistance(player, helper, FAR_DISTANCE);
		PowerTracker.scanPlayer(player, config);
		double farOut = modifierAmount(player, MAX_HEALTH_MODIFIER);
		placeAtDistance(player, helper, 0);
		PowerTracker.scanPlayer(player, config);
		double backHome = modifierAmount(player, MAX_HEALTH_MODIFIER);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=levelIgnoresPosition RESULT level={} healthHome={} healthFar={} healthHomeAgain={}",
			player.experienceLevel, atHome, farOut, backHome
		);
		helper.assertTrue(
			atHome > 0.0 && atHome == farOut && farOut == backHome,
			"the health gain moved with the player: home " + atHome + ", far " + farOut + ", home again " + backHome
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theAttributesComeBackAfterARespawnThatKeepsTheLevel(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.experienceLevel = config.levelCap();
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(
			player, true, Entity.RemovalReason.CHANGED_DIMENSION
		);
		helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
			double health = modifierAmount(reborn, MAX_HEALTH_MODIFIER);
			double damage = modifierAmount(reborn, ATTACK_DAMAGE_MODIFIER);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=respawnReentry RESULT level={} maxHealth={} damage={}",
				reborn.experienceLevel, health, damage
			);
			helper.assertTrue(
				reborn.experienceLevel == config.levelCap()
					&& Math.abs(damage - config.attributes().attackDamageAtCap()) < TOLERANCE
					&& Math.abs(health - expectedModifiers(config.levelCap(), config)[0]) < TOLERANCE,
				"the respawned player at level " + reborn.experienceLevel + " read damage " + damage + " and health " + health
			);
			helper.succeed();
		});
	}

	@GameTest
	public void theAttributesDoNotChangeWhenThePlayerChangesDimension(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer player = playerAtLevel(helper, CONTROL_LEVEL);
		PowerTracker.scanPlayer(player, config);
		double overworldHealth = modifierAmount(player, MAX_HEALTH_MODIFIER);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the test server holds no nether level");
		player.setServerLevel(nether);
		BlockPos netherSpawn = nether.getRespawnData().pos();
		player.setPos(netherSpawn.getX() + 1200.0, 64.0, netherSpawn.getZ());
		PowerTracker.scanPlayer(player, config);
		double netherHealth = modifierAmount(player, MAX_HEALTH_MODIFIER);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=dimensionReentry RESULT level={} healthOverworld={} healthNether={}",
			player.experienceLevel, overworldHealth, netherHealth
		);
		helper.assertTrue(
			overworldHealth > 0.0 && Math.abs(netherHealth - overworldHealth) < TOLERANCE,
			"the nether changed the maximum health from " + overworldHealth + " to " + netherHealth
		);
		helper.succeed();
	}

	@GameTest
	public void theAttributesFollowAConfigReload(final GameTestHelper helper) {
		PowerConfig before = bundledConfig(helper);
		ServerPlayer player = mockPlayer(helper);
		player.experienceLevel = before.levelCap();
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

		player.experienceLevel = after.levelCap();
		PowerTracker.scanPlayer(player, after);
		double afterReload = modifierAmount(player, ARMOR_MODIFIER);
		PowerConfigStore.load();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=reloadReentry RESULT armorAtCapBefore={} armorAtCapAfter={} modifierBefore={} modifierAfter={}",
			before.attributes().armorAtCap(), after.attributes().armorAtCap(), beforeReload, afterReload
		);
		helper.assertTrue(
			Math.abs(beforeReload - before.attributes().armorAtCap()) < TOLERANCE
				&& Math.abs(afterReload - after.attributes().armorAtCap()) < TOLERANCE,
			"the armor modifier read " + beforeReload + " then " + afterReload + " across the reload"
		);
		helper.succeed();
	}

	@GameTest
	public void theAttributesComeBackAfterARelogin(final GameTestHelper helper) {
		PowerConfig config = bundledConfig(helper);
		ServerPlayer original = playerAtLevel(helper, CONTROL_LEVEL);
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
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=reloginReentry RESULT level={} damageAfterLoad={} damageAfterScan={} originalDamage={}",
			rejoined.experienceLevel, afterLoad, afterScan, originalDamage
		);
		helper.assertTrue(
			originalDamage > 0.0 && afterLoad == originalDamage && afterScan == originalDamage
				&& rejoined.experienceLevel == CONTROL_LEVEL,
			"the re-login carried damage " + originalDamage + " -> " + afterLoad + " -> " + afterScan
				+ " at level " + rejoined.experienceLevel
		);
		helper.succeed();
	}

	private static ServerPlayer playerAtLevel(final GameTestHelper helper, final int level) {
		ServerPlayer player = mockPlayer(helper);
		player.experienceLevel = level;
		player.totalExperience = VANILLA_TOTAL;
		player.experienceProgress = VANILLA_PROGRESS;
		return player;
	}

	// The five modifier values the configured formula names for a capped level.
	private static double[] expectedModifiers(final int level, final PowerConfig config) {
		PowerConfig.Attributes attributes = config.attributes();
		double fraction = Math.max(0, level - 1) / (double) (config.levelCap() - 1);
		return new double[] {
			attributes.healthPerLevel() * Math.max(0, level - 1) + attributes.healthBase() - 20.0,
			attributes.armorAtCap() * fraction,
			attributes.knockbackResistanceAtCap() * fraction,
			attributes.attackDamageAtCap() * fraction,
			attributes.attackSpeedAtCap() * fraction
		};
	}

	private static double[] allModifiers(final ServerPlayer player) {
		return new double[] {
			modifierAmount(player, MAX_HEALTH_MODIFIER),
			modifierAmount(player, ARMOR_MODIFIER),
			modifierAmount(player, KNOCKBACK_MODIFIER),
			modifierAmount(player, ATTACK_DAMAGE_MODIFIER),
			modifierAmount(player, ATTACK_SPEED_MODIFIER)
		};
	}

	private static String difference(final ServerPlayer first, final ServerPlayer second) {
		StringBuilder found = new StringBuilder();
		if (first.experienceLevel != second.experienceLevel) {
			found.append("level ");
		}

		if (first.totalExperience != second.totalExperience) {
			found.append("total ");
		}

		if (first.experienceProgress != second.experienceProgress) {
			found.append("progress ");
		}

		double[] a = allModifiers(first);
		double[] b = allModifiers(second);
		for (int index = 0; index < a.length; index++) {
			if (Math.abs(a[index] - b[index]) > TOLERANCE) {
				found.append("modifier").append(index).append(' ');
			}
		}

		return found.toString().trim();
	}

	private static String describe(final ServerPlayer player) {
		double[] v = allModifiers(player);
		return "level=" + player.experienceLevel + " total=" + player.totalExperience + " progress="
			+ player.experienceProgress + " health=" + v[0] + " damage=" + v[3];
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
		var attribute = id.equals(ATTACK_DAMAGE_MODIFIER) ? Attributes.ATTACK_DAMAGE
			: id.equals(ARMOR_MODIFIER) ? Attributes.ARMOR
			: id.equals(KNOCKBACK_MODIFIER) ? Attributes.KNOCKBACK_RESISTANCE
			: id.equals(ATTACK_SPEED_MODIFIER) ? Attributes.ATTACK_SPEED
			: Attributes.MAX_HEALTH;
		var instance = player.getAttribute(attribute);
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
