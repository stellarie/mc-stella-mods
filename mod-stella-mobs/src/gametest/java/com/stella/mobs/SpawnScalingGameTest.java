package com.stella.mobs;

import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.behavior.SpawnSafety;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatExperience;
import com.stella.mobs.scaling.ThreatState;
import com.stella.mobs.scaling.WorldThreatPeak;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.gear.GearTier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * A mob's health and damage use one threat level plus the retained depth modifier.
 */
public class SpawnScalingGameTest {
	@GameTest
	public void highestThreatPersistsAcrossDimensionsAndNeverFalls(final GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		int before = WorldThreatPeak.highest(server);
		var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
		Mob stronger = EntityTypes.ZOMBIE.create(nether, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		((AttachmentTarget) stronger).setAttached(ThreatState.TYPE, new ThreatState(before + 20, 0));
		int observed = ThreatState.level(stronger);
		int fromOverworld = WorldThreatPeak.highest(server);
		Mob weaker = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		((AttachmentTarget) weaker).setAttached(ThreatState.TYPE, new ThreatState(0, 0));
		ThreatState.level(weaker);
		int afterWeaker = WorldThreatPeak.highest(server);
		result("highestThreatPersistsAcrossDimensionsAndNeverFalls", "before=" + before + " observed=" + observed
			+ " overworld=" + fromOverworld + " afterWeaker=" + afterWeaker);
		helper.assertTrue(observed >= before + 20 && fromOverworld == observed && afterWeaker == observed,
			"the world threat peak did not rise across dimensions or fell again");
		helper.succeed();
	}

	@GameTest
	public void threatVariationIsBoundedAndCentered(final GameTestHelper helper) {
		var random = net.minecraft.util.RandomSource.create(20_260_929L);
		int minimum = Integer.MAX_VALUE;
		int maximum = Integer.MIN_VALUE;
		int nearMean = 0;
		int edges = 0;
		long sum = 0L;
		long squares = 0L;
		for (int sample = 0; sample < 100_000; sample++) {
			int variation = ThreatState.rollVariation(random);
			minimum = Math.min(minimum, variation);
			maximum = Math.max(maximum, variation);
			nearMean += variation == ThreatState.VARIATION_MEAN ? 1 : 0;
			edges += variation == ThreatState.MIN_VARIATION || variation == ThreatState.MAX_VARIATION ? 1 : 0;
			sum += variation;
			squares += (long) variation * variation;
		}
		double mean = sum / 100_000.0;
		double deviation = Math.sqrt(squares / 100_000.0 - mean * mean);
		// The clamp is reached on both sides and never passed, the centre is the shipped mean, and the spread
		// is the shipped one, a little shaved by the clamped tails.
		boolean valid = minimum == ThreatState.MIN_VARIATION && maximum == ThreatState.MAX_VARIATION
			&& Math.abs(mean - ThreatState.VARIATION_MEAN) < 0.2
			&& deviation > ThreatState.VARIATION_STDDEV * 0.95 && deviation < ThreatState.VARIATION_STDDEV * 1.02
			&& nearMean > edges;
		result("threatVariationIsBoundedAndCentered", "min=" + minimum + " max=" + maximum
			+ " mean=" + mean + " deviation=" + deviation + " nearMean=" + nearMean + " edges=" + edges
			+ " valid=" + valid);
		helper.assertTrue(valid, "threat variation lost its bounded, re-centred normal distribution");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void eliteAndBossBonusesFollowDimensionMinimum(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(0, ThreatState.MIN_VARIATION));
		int ordinary = ThreatState.level(mob);
		EliteState.markElite(mob, GearTier.DIAMOND);
		int elite = ThreatState.level(mob);
		EliteState.setBoss(mob, true);
		int boss = ThreatState.level(mob);
		Mob positiveRoll = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		((AttachmentTarget) positiveRoll).setAttached(ThreatState.TYPE,
			new ThreatState(0, ThreatState.MAX_VARIATION));
		int positive = ThreatState.level(positiveRoll);
		SpawnScaling.apply(mob);
		boolean valid = ordinary == ThreatState.NETHER_MINIMUM
			&& elite - ordinary == 5
			&& boss - ordinary == 10
			&& positive == ThreatState.NETHER_MINIMUM + ThreatState.MAX_VARIATION
			&& Math.abs(healthAmount(mob) - (SpawnScaling.healthMultiplier(boss) - 1.0)) < EPSILON;
		result("eliteAndBossBonusesFollowDimensionMinimum", "ordinary=" + ordinary
			+ " elite=" + elite + " boss=" + boss + " positive=" + positive + " valid=" + valid);
		helper.assertTrue(valid, "rank bonus stacked or fell below the Nether base");
		helper.succeed();
	}

	@GameTest
	public void threatRollSurvivesEntitySave(final GameTestHelper helper) {
		Mob original = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		ThreatState rolled = new ThreatState(17, -4);
		((AttachmentTarget) original).setAttached(ThreatState.TYPE, rolled);
		var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
			net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		original.saveWithoutId(output);
		Mob restored = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		restored.load(net.minecraft.world.level.storage.TagValueInput.create(
			net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
		ThreatState decoded = ThreatState.get(restored);
		boolean valid = rolled.equals(decoded) && ThreatState.getOrRoll(restored) == decoded;
		result("threatRollSurvivesEntitySave", "area=" + (decoded == null ? "missing" : decoded.areaLevel())
			+ " variation=" + (decoded == null ? "missing" : decoded.variation()) + " valid=" + valid);
		helper.assertTrue(valid, "saved mob lost or rerolled its threat state");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void oldBossModifiersMigrateWithoutHealing(final GameTestHelper helper) {
		Mob boss = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		EliteState.markElite(boss, GearTier.NETHERITE);
		EliteState.setBoss(boss, true);
		AttributeInstance health = boss.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance damage = boss.getAttribute(Attributes.ATTACK_DAMAGE);
		health.addPermanentModifier(new AttributeModifier(SpawnScaling.HEALTH_ID, 0.5,
			AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		health.addPermanentModifier(new AttributeModifier(Bosses.LEVEL_HEALTH_ID, 0.5,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		damage.addPermanentModifier(new AttributeModifier(SpawnScaling.DAMAGE_ID, 0.25,
			AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		damage.addPermanentModifier(new AttributeModifier(Bosses.LEVEL_DAMAGE_ID, 0.25,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		boss.setHealth(boss.getMaxHealth() * 0.4F);
		SpawnScaling.reconcileLoaded(boss);
		int threatLevel = ThreatState.level(boss);
		float firstMaximum = boss.getMaxHealth();
		float firstHealth = boss.getHealth();
		SpawnScaling.reconcileLoaded(boss);
		boolean valid = ThreatState.get(boss) != null
			&& health.getModifier(Bosses.LEVEL_HEALTH_ID) == null
			&& damage.getModifier(Bosses.LEVEL_DAMAGE_ID) == null
			&& health.getModifiers().size() == 1 && damage.getModifiers().size() == 1
			&& Math.abs(firstHealth / firstMaximum - 0.4F) < 0.01F
			&& Math.abs(boss.getMaxHealth() - firstMaximum) < EPSILON
			&& Math.abs(boss.getHealth() - firstHealth) < EPSILON;
		result("oldBossModifiersMigrateWithoutHealing", "threatLevel=" + threatLevel
			+ " max=" + firstMaximum + " health=" + firstHealth + " valid=" + valid);
		helper.assertTrue(valid, "saved boss retained legacy modifiers or healed during migration");
		helper.succeed();
	}
	@GameTest
	public void distanceAddsOneLevelEvery100BlocksFromTheOrigin(final GameTestHelper helper) {
		int[] distances = {0, 99, 100, 199, 1000, 5500, 1_000_000};
		int[] expected = {0, 0, 1, 1, 10, 55, 10_000};
		boolean[] levels = new boolean[distances.length];
		boolean valid = true;
		StringBuilder measured = new StringBuilder();
		// The respawn point is moved far off: distance is measured from 0,0, whatever the world spawn is.
		withProductionOrigin(() -> withConfig(PLAIN_CONFIG, () -> withRespawn(helper, new BlockPos(7_000, 64, -9_000), () -> {
			for (int index = 0; index < distances.length; index++) {
				Zombie east = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				east.snapTo(distances[index], helper.getLevel().getSeaLevel() + 1, 0.0, 0.0F, 0.0F);
				Zombie north = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				north.snapTo(0.0, helper.getLevel().getSeaLevel() + 1, -distances[index], 0.0F, 0.0F);
				int eastLevel = SpawnScaling.areaLevel(east);
				int northLevel = SpawnScaling.areaLevel(north);
				measured.append(eastLevel).append('/').append(northLevel).append(',');
				levels[index] = eastLevel == expected[index] && northLevel == expected[index];
			}
		})));
		for (boolean ok : levels) {
			valid &= ok;
		}
		result("distanceAddsOneLevelEvery100BlocksFromTheOrigin", "measured=" + measured + " valid=" + valid);
		helper.assertTrue(valid, "threat area did not advance at each 100-block boundary from 0,0");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void netherMobHasAtLeastTheNetherBaseLevel(final GameTestHelper helper) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		double health = healthAmount(mob);
		double damage = damageAmount(mob);
		boolean valid = health >= SpawnScaling.healthMultiplier(ThreatState.NETHER_MINIMUM) - 1.0 - EPSILON
			&& damage >= SpawnScaling.damageMultiplier(ThreatState.NETHER_MINIMUM) - 1.0 - EPSILON;
		result("netherMobHasAtLeastTheNetherBaseLevel",
			"healthAmount=" + health + " damageAmount=" + damage + " valid=" + valid);
		helper.assertTrue(valid, "Nether mob scaled below the Nether base level");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_end")
	public void endMobHasAtLeastTheEndBaseLevel(final GameTestHelper helper) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		double health = healthAmount(mob);
		double damage = damageAmount(mob);
		boolean valid = health >= SpawnScaling.healthMultiplier(ThreatState.END_MINIMUM) - 1.0 - EPSILON
			&& damage >= SpawnScaling.damageMultiplier(ThreatState.END_MINIMUM) - 1.0 - EPSILON;
		result("endMobHasAtLeastTheEndBaseLevel",
			"healthAmount=" + health + " damageAmount=" + damage + " valid=" + valid);
		helper.assertTrue(valid, "End mob scaled below the End base level");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void netherLevelClimbsEightPerHundredBlocksFromTheOrigin(final GameTestHelper helper) {
		int[] distances = {0, 99, 100, 1000};
		int[] expected = {50, 50, 58, 130};
		boolean valid = true;
		StringBuilder measured = new StringBuilder();
		// The respawn point is moved off too: the Nether has no divide-by-eight origin any more.
		for (int index = 0; index < distances.length; index++) {
			int[] area = new int[1];
			int[] threat = new int[1];
			int at = distances[index];
			withProductionOrigin(() -> withConfig(PLAIN_CONFIG, () -> withRespawn(helper, new BlockPos(7_000, 64, -9_000), () -> {
				Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				mob.snapTo(at, helper.getLevel().getSeaLevel() + 1, 0.0, 0.0F, 0.0F);
				((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(SpawnScaling.areaLevel(mob), 0));
				area[0] = SpawnScaling.areaLevel(mob);
				threat[0] = ThreatState.level(mob);
			})));
			measured.append(area[0]).append('/').append(threat[0]).append(',');
			valid &= area[0] == expected[index] && threat[0] == expected[index];
		}
		result("netherLevelClimbsEightPerHundredBlocksFromTheOrigin", "measured=" + measured + " valid=" + valid);
		helper.assertTrue(valid, "Nether level did not climb 8 per 100 blocks from a base of 50");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_end")
	public void endLevelClimbsEightPerHundredBlocksFromTheOrigin(final GameTestHelper helper) {
		int[] distances = {0, 99, 100, 1000};
		int[] expected = {80, 80, 88, 160};
		boolean valid = true;
		StringBuilder measured = new StringBuilder();
		for (int index = 0; index < distances.length; index++) {
			int[] area = new int[1];
			int[] threat = new int[1];
			int at = distances[index];
			withProductionOrigin(() -> withConfig(PLAIN_CONFIG, () -> {
				Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				mob.snapTo(at, helper.getLevel().getSeaLevel() + 1, 0.0, 0.0F, 0.0F);
				((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(SpawnScaling.areaLevel(mob), 0));
				area[0] = SpawnScaling.areaLevel(mob);
				threat[0] = ThreatState.level(mob);
			}));
			measured.append(area[0]).append('/').append(threat[0]).append(',');
			valid &= area[0] == expected[index] && threat[0] == expected[index];
		}
		result("endLevelClimbsEightPerHundredBlocksFromTheOrigin", "measured=" + measured + " valid=" + valid);
		helper.assertTrue(valid, "End level did not climb 8 per 100 blocks from a base of 80");
		helper.succeed();
	}

	@GameTest
	public void mobExperienceFallsAsPlayerLevelRises(final GameTestHelper helper) {
		Zombie mob = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		var spawn = helper.getLevel().getRespawnData().pos();
		mob.snapTo(spawn.getX(), helper.getLevel().getSeaLevel() + 1, spawn.getZ(), 0.0F, 0.0F);
		SpawnScaling.apply(mob);
		var player = helper.makeMockServerPlayerInLevel();
		player.experienceLevel = 0;
		int noviceReward = mob.getExperienceReward(helper.getLevel(), player);
		player.experienceLevel = 50;
		int veteranReward = mob.getExperienceReward(helper.getLevel(), player);
		player.experienceLevel = 0;
		mob.setLastHurtByPlayer(player, 100);
		Mob indirectKiller = EntityTypes.WOLF.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		int creditedReward = mob.getExperienceReward(helper.getLevel(), indirectKiller);
		boolean valid = noviceReward > veteranReward && veteranReward >= 1
			&& creditedReward == noviceReward;
		result("mobExperienceFallsAsPlayerLevelRises",
			"noviceReward=" + noviceReward + " veteranReward=" + veteranReward
				+ " creditedReward=" + creditedReward + " valid=" + valid);
		helper.assertTrue(valid, "mob XP ignored the player's level");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = 200)
	public void playerKillDropsThreatAdjustedExperienceOrbs(final GameTestHelper helper) {
		var level = helper.getLevel();
		Zombie mob = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(0, ThreatState.MIN_VARIATION));
		EliteState.setSummoned(mob, true);
		com.stella.mobs.behavior.ReactiveState.get(mob).setGearApplied(true);
		var position = helper.absolutePos(new net.minecraft.core.BlockPos(2, 3, 2));
		mob.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(position), 0.0F, 0.0F);
		level.addFreshEntity(mob);
		var player = helper.makeMockServerPlayerInLevel();
		player.experienceLevel = 10;
		player.snapTo(mob.getX() + 8.0, mob.getY(), mob.getZ(), 0.0F, 0.0F);
		mob.setLastHurtByPlayer(player, 100);
		mob.setHealth(1.0F);
		mob.hurt(level.damageSources().playerAttack(player), 20.0F);
		helper.runAfterDelay(1, () -> {
			var nearby = net.minecraft.world.phys.AABB.ofSize(mob.position(), 8.0, 8.0, 8.0);
			int orbs = level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, nearby)
				.stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum();
			int expected = ThreatExperience.reward(5, ThreatState.level(mob), player.experienceLevel);
			boolean valid = !mob.isAlive() && ThreatState.level(mob) == ThreatState.NETHER_MINIMUM
				&& orbs == expected;
			result("playerKillDropsThreatAdjustedExperienceOrbs",
				"mobLevel=" + ThreatState.level(mob) + " playerLevel=" + player.experienceLevel
					+ " orbExperience=" + orbs + " expected=" + expected + " valid=" + valid);
			helper.assertTrue(valid, "player kill did not drop threat-adjusted XP orbs");
			helper.succeed();
		});
	}

	@GameTest
	public void distanceLevelsFollowTheDimensionSlope(final GameTestHelper helper) {
		int[] actual = {
			SpawnScaling.distanceLevels(Level.OVERWORLD, 0.0),
			SpawnScaling.distanceLevels(Level.OVERWORLD, 99.9),
			SpawnScaling.distanceLevels(Level.OVERWORLD, 100.0),
			SpawnScaling.distanceLevels(Level.OVERWORLD, 5500.0),
			SpawnScaling.distanceLevels(Level.NETHER, 99.9),
			SpawnScaling.distanceLevels(Level.NETHER, 100.0),
			SpawnScaling.distanceLevels(Level.NETHER, 1000.0),
			SpawnScaling.distanceLevels(Level.END, 100.0),
			SpawnScaling.distanceLevels(Level.END, 1000.0)
		};
		int[] expected = {0, 0, 1, 55, 0, 8, 80, 8, 80};
		int wrong = 0;
		StringBuilder cases = new StringBuilder();
		for (int index = 0; index < expected.length; index++) {
			cases.append(index).append('=').append(actual[index]).append(' ');
			wrong += actual[index] == expected[index] ? 0 : 1;
		}
		result("distanceLevelsFollowTheDimensionSlope", "wrong=" + wrong + " " + cases);
		helper.assertValueEqual(Integer.valueOf(wrong), Integer.valueOf(0), "distance levels that disagree with the slope");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void netherMobTakesNoAltitudeMultiplier(final GameTestHelper helper) {
		double[] amounts = new double[2];
		double[] ys = {-30.0, 200.0};
		withConfig(PLAIN_CONFIG, () -> {
			for (int index = 0; index < ys.length; index++) {
				Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				mob.snapTo(1.0, ys[index], 1.0, 0.0F, 0.0F);
				((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(ThreatState.NETHER_MINIMUM, 0));
				SpawnScaling.apply(mob);
				amounts[index] = healthAmount(mob);
			}
		});
		double expected = SpawnScaling.healthMultiplier(ThreatState.NETHER_MINIMUM) - 1.0;
		boolean valid = Math.abs(amounts[0] - expected) < EPSILON && Math.abs(amounts[1] - expected) < EPSILON;
		result("netherMobTakesNoAltitudeMultiplier", "deep=" + amounts[0] + " high=" + amounts[1]
			+ " expected=" + expected + " valid=" + valid);
		helper.assertTrue(valid, "a Nether mob took a depth or height multiplier");
		helper.succeed();
	}

	@GameTest
	public void biomeAddsItsFlatLevelsFromTheConfig(final GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 3.0, 1.5));
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		Identifier biome = helper.getLevel().getBiome(zombie.blockPosition()).unwrapKey().orElseThrow().identifier();
		int[] offsets = new int[3];
		int[] areas = new int[3];
		String[] configs = {
			biomeConfig("\"" + biome + "\": 9"),
			biomeConfig("\"minecraft:the_void\": 9"),
			biomeConfig("\"" + biome + "\": -1000000")
		};
		for (int index = 0; index < configs.length; index++) {
			int at2 = index;
			String config = configs[index];
			withProductionOrigin(() -> withConfig(config, () -> {
				offsets[at2] = SpawnScaling.biomeOffset(zombie);
				areas[at2] = SpawnScaling.areaLevel(zombie);
			}));
		}
		int distanceLevels = SpawnScaling.distanceLevels(Level.OVERWORLD, Math.hypot(zombie.getX(), zombie.getZ()));
		boolean valid = offsets[0] == 9 && areas[0] == distanceLevels + 9
			&& offsets[1] == 0 && areas[1] == distanceLevels
			&& offsets[2] == -1_000_000 && areas[2] == 0;
		result("biomeAddsItsFlatLevelsFromTheConfig", "biome=" + biome + " distanceLevels=" + distanceLevels
			+ " offsets=" + offsets[0] + "/" + offsets[1] + "/" + offsets[2]
			+ " areas=" + areas[0] + "/" + areas[1] + "/" + areas[2] + " valid=" + valid);
		helper.assertTrue(valid, "the biome's flat levels were not added, or a negative offset went below level zero");
		helper.succeed();
	}

	@GameTest
	public void badBiomeEntriesLogAndSkip(final GameTestHelper helper) {
		GearConfig parsed = GearConfigParser.parse(biomeConfig(
			"\"minecraft:plains\": 3, \"Not A Biome!\": 4, \"minecraft:swamp\": 2.5, \"minecraft:jungle\": \"six\", "
				+ "\"minecraft:desert\": -4"
		));
		var levels = parsed.world().biomeLevels();
		boolean valid = levels.size() == 2
			&& parsed.world().biomeLevel(Identifier.parse("minecraft:plains")) == 3
			&& parsed.world().biomeLevel(Identifier.parse("minecraft:desert")) == -4;
		result("badBiomeEntriesLogAndSkip", "kept=" + levels + " valid=" + valid);
		helper.assertTrue(valid, "a bad biome entry was kept, or a good one lost");
		helper.succeed();
	}

	@GameTest
	public void shippedConfigCarriesEveryWorldRule(final GameTestHelper helper) {
		GearConfig shipped = GearConfigStore.defaults();
		var world = shipped.world();
		double[] actual = {
			world.eliteMinDistance(), world.bossMinDistance(), world.eliteBonusPer100(), world.eliteBonusCap(),
			world.eliteQuietRadius(), world.bossQuietRadius(), world.quietFactor(),
			shipped.spawnSafetyRadius(), SpawnSafety.SHIPPED_RADIUS,
			world.biomeLevels().isEmpty() ? 0 : 1
		};
		double[] expected = {500.0, 2000.0, 0.0005, 0.20, 16.0, 32.0, 0.5, 240.0, 240.0, 1};
		int wrong = 0;
		StringBuilder cases = new StringBuilder();
		for (int index = 0; index < expected.length; index++) {
			cases.append(index).append('=').append(actual[index]).append(' ');
			wrong += Math.abs(actual[index] - expected[index]) < EPSILON ? 0 : 1;
		}
		result("shippedConfigCarriesEveryWorldRule", "wrong=" + wrong + " biomes=" + world.biomeLevels().size() + " " + cases);
		helper.assertValueEqual(Integer.valueOf(wrong), Integer.valueOf(0), "shipped world rules that differ from the spec");
		helper.succeed();
	}

	@GameTest
	public void anOlderConfigGainsTheWorldRules(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			java.nio.file.Path older = helper.getLevel().getServer().getServerDirectory()
				.resolve("config-migration").resolve("world-rules.json");
			java.nio.file.Files.createDirectories(older.getParent());
			java.nio.file.Files.writeString(older, "{ \"version\": 1, \"entities\": {}, \"rolls\": { \"roll_chance\": 0.3 } }",
				java.nio.charset.StandardCharsets.UTF_8);
			var before = GearConfigParser.parse("{ \"rolls\": { \"roll_chance\": 0.3 } }").world();
			var migrated = GearConfigStore.loadFrom(older).world();
			var shipped = GearConfigStore.defaults().world();
			boolean valid = before.equals(com.stella.mobs.config.WorldRules.OFF) && migrated.equals(shipped)
				&& !migrated.biomeLevels().isEmpty();
			result("anOlderConfigGainsTheWorldRules", "before=" + before.eliteMinDistance()
				+ " migrated=" + migrated.eliteMinDistance() + "/" + migrated.bossMinDistance()
				+ " biomes=" + migrated.biomeLevels().size() + " valid=" + valid);
			helper.assertTrue(valid, "an older file did not gain the shipped world rules, or gained them wrongly");
		} catch (java.io.IOException e) {
			helper.fail("could not write the older config: " + e.getMessage());
		} finally {
			GearConfigStore.set(previous);
		}
		helper.succeed();
	}

	@GameTest
	public void theRigMeasuresFromTheWorldSpawnAndPlayFrom00(final GameTestHelper helper) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		BlockPos spawn = new BlockPos(4_000, 64, -3_000);
		double[] measured = new double[2];
		withRespawn(helper, spawn, () -> {
			zombie.snapTo(4_300.0, 64.0, -3_000.0, 0.0F, 0.0F);
			measured[0] = SpawnScaling.distanceFromOrigin(zombie);
			withProductionOrigin(() -> measured[1] = SpawnScaling.distanceFromOrigin(zombie));
		});
		boolean valid = Math.abs(measured[0] - 300.0) < EPSILON
			&& Math.abs(measured[1] - Math.hypot(4_300.0, 3_000.0)) < EPSILON;
		result("theRigMeasuresFromTheWorldSpawnAndPlayFrom00", "rig=" + measured[0] + " play=" + measured[1] + " valid=" + valid);
		helper.assertTrue(valid, "the origin switch does not pick the world spawn for the rig and 0,0 for play");
		helper.succeed();
	}

	private static final double EPSILON = 1.0E-6;
	// No gear and no elite, so this test measures the scaling path alone.
	private static final String PLAIN_CONFIG = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	@GameTest(maxTicks = 200)
	public void farMobSpawnsScaled(final GameTestHelper helper) {
		withConfig(PLAIN_CONFIG, () -> {
			Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
			double x = zombie.getX();
			double y = zombie.getY();
			double z = zombie.getZ();
			double distance = Math.hypot(x, z);
			int threatLevel = ThreatState.level(zombie);
			double expected = SpawnScaling.healthMultiplier(threatLevel);

			AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
			helper.assertTrue(health != null, "the zombie has no MAX_HEALTH attribute");
			AttributeModifier modifier = health.getModifier(SpawnScaling.HEALTH_ID);
			helper.assertTrue(modifier != null, "no spawn scaling modifier was applied");
			helper.assertTrue(
				Math.abs(modifier.amount() - (expected - 1.0)) < EPSILON,
				"the scaling amount " + modifier.amount() + " is not the expected " + (expected - 1.0)
					+ " at distance " + distance + " and height " + y
			);
			helper.assertValueEqual(
				Float.valueOf(zombie.getHealth()), Float.valueOf(zombie.getMaxHealth()), "the scaled mob healed to its new maximum"
			);
			result(
				"farMobSpawnsScaled",
				"dist=" + distance + " y=" + y + " threatLevel=" + threatLevel
					+ " healthMul=" + expected + " amount=" + modifier.amount()
					+ " hp=" + zombie.getHealth() + "/" + zombie.getMaxHealth()
			);
		});
		helper.succeed();
	}

	@GameTest
	public void diskLoadDoesNotScaleTwice(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(PLAIN_CONFIG));

		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		GearConfigStore.set(previous);
		// The spawn path scaled the mob already, so the claim is idempotence, not an empty start.
		int atSpawn = countScalingModifiers(mob);
		double healthAtSpawn = healthAmount(mob);
		double damageAtSpawn = damageAmount(mob);
		float maxAtSpawn = mob.getMaxHealth();

		SpawnScaling.apply(mob);
		SpawnScaling.apply(mob);

		helper.assertValueEqual(Integer.valueOf(countScalingModifiers(mob)), Integer.valueOf(atSpawn), "scaling modifier count after two more applications");
		helper.assertTrue(
			Math.abs(healthAmount(mob) - healthAtSpawn) < EPSILON,
			"the health scaling amount moved from " + healthAtSpawn + " to " + healthAmount(mob)
		);
		helper.assertTrue(
			Math.abs(damageAmount(mob) - damageAtSpawn) < EPSILON,
			"the damage scaling amount moved from " + damageAtSpawn + " to " + damageAmount(mob)
		);
		helper.assertValueEqual(Float.valueOf(mob.getMaxHealth()), Float.valueOf(maxAtSpawn), "the maximum health after two more applications");
		result(
			"diskLoadDoesNotScaleTwice",
			"modifiersAtSpawn=" + atSpawn + " modifiersAfter=" + countScalingModifiers(mob)
				+ " healthAmount=" + healthAtSpawn + " damageAmount=" + damageAtSpawn + " max=" + mob.getMaxHealth()
		);
		helper.succeed();
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}

	private static double healthAmount(final Mob mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		AttributeModifier modifier = health == null ? null : health.getModifier(SpawnScaling.HEALTH_ID);
		return modifier == null ? 0.0 : modifier.amount();
	}

	private static double damageAmount(final Mob mob) {
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		AttributeModifier modifier = damage == null ? null : damage.getModifier(SpawnScaling.DAMAGE_ID);
		return modifier == null ? 0.0 : modifier.amount();
	}

	private static int countScalingModifiers(final Mob mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		int count = 0;

		if (health != null && health.getModifier(SpawnScaling.HEALTH_ID) != null) {
			count++;
		}

		if (damage != null && damage.getModifier(SpawnScaling.DAMAGE_ID) != null) {
			count++;
		}

		return count;
	}

	/** Runs the body as play runs: distance from 0,0. The rig's own origin setting is put back afterwards. */
	private static void withProductionOrigin(final Runnable body) {
		String previous = System.getProperty(SpawnScaling.ORIGIN_PROPERTY);
		try {
			System.clearProperty(SpawnScaling.ORIGIN_PROPERTY);
			body.run();
		} finally {
			if (previous != null) {
				System.setProperty(SpawnScaling.ORIGIN_PROPERTY, previous);
			}
		}
	}

	/** Parses the config, runs the body, and puts the previous config back even when the body throws. */
	private static void withConfig(final String json, final Runnable body) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(json));
			body.run();
		} finally {
			GearConfigStore.set(previous);
		}
	}

	/** Moves the level's respawn point for the body, and restores it afterwards. */
	private static void withRespawn(final GameTestHelper helper, final BlockPos spawn, final Runnable body) {
		var level = helper.getLevel();
		LevelData.RespawnData previous = level.getRespawnData();
		try {
			level.setRespawnData(LevelData.RespawnData.of(level.dimension(), spawn, 0.0F, 0.0F));
			body.run();
		} finally {
			level.setRespawnData(previous);
		}
	}

	private static String biomeConfig(final String entries) {
		return "{ \"entities\": {}, \"rolls\": { \"roll_chance\": 0.0, \"elite_chance\": 0.0, \"biome_levels\": { "
			+ entries + " } } }";
	}

}
