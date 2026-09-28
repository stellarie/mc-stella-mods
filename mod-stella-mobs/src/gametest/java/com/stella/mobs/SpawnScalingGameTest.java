package com.stella.mobs;

import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.gear.GearTier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
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
	public void threatVariationIsBoundedAndCentered(final GameTestHelper helper) {
		var random = net.minecraft.util.RandomSource.create(20_260_928L);
		int minimum = Integer.MAX_VALUE;
		int maximum = Integer.MIN_VALUE;
		int zero = 0;
		int edges = 0;
		long sum = 0L;
		long squares = 0L;
		for (int sample = 0; sample < 100_000; sample++) {
			int variation = ThreatState.rollVariation(random);
			minimum = Math.min(minimum, variation);
			maximum = Math.max(maximum, variation);
			zero += variation == 0 ? 1 : 0;
			edges += Math.abs(variation) == ThreatState.MAX_VARIATION ? 1 : 0;
			sum += variation;
			squares += (long) variation * variation;
		}
		double mean = sum / 100_000.0;
		double deviation = Math.sqrt(squares / 100_000.0 - mean * mean);
		boolean valid = minimum >= -ThreatState.MAX_VARIATION && maximum <= ThreatState.MAX_VARIATION
			&& Math.abs(mean) < 0.1 && deviation > 4.0 && deviation < ThreatState.VARIATION_STDDEV
			&& zero > edges;
		result("threatVariationIsBoundedAndCentered", "min=" + minimum + " max=" + maximum
			+ " mean=" + mean + " deviation=" + deviation + " zero=" + zero + " edges=" + edges
			+ " valid=" + valid);
		helper.assertTrue(valid, "threat variation lost its bounded normal distribution");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void eliteAndBossBonusesFollowDimensionMinimum(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(0, -ThreatState.MAX_VARIATION));
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
			&& positive == ThreatState.NETHER_MINIMUM + 10
			&& Math.abs(healthAmount(mob) - (SpawnScaling.healthMultiplier(boss,
				Math.max(0.0, helper.getLevel().getSeaLevel() - mob.getY())) - 1.0)) < EPSILON;
		result("eliteAndBossBonusesFollowDimensionMinimum", "ordinary=" + ordinary
			+ " elite=" + elite + " boss=" + boss + " positive=" + positive + " valid=" + valid);
		helper.assertTrue(valid, "rank bonus stacked or fell below the Nether minimum");
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
	public void distanceAddsOneLevelEvery500Blocks(final GameTestHelper helper) {
		int[] distances = {0, 499, 500, 999, 1000, 5500, 1_000_000};
		int[] expected = {0, 0, 1, 1, 2, 11, 2000};
		var spawn = helper.getLevel().getRespawnData().pos();
		boolean valid = true;
		StringBuilder measured = new StringBuilder();
		for (int index = 0; index < distances.length; index++) {
			Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			zombie.snapTo(spawn.getX() + distances[index], helper.getLevel().getSeaLevel() + 1,
				spawn.getZ(), 0.0F, 0.0F);
			int level = SpawnScaling.areaLevel(zombie);
			measured.append(level).append(',');
			valid &= level == expected[index];
		}
		result("distanceAddsOneLevelEvery500Blocks", "measured=" + measured + " valid=" + valid);
		helper.assertTrue(valid, "threat area did not advance at each 500-block boundary");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void netherMobHasAtLeastTwentyThreatLevels(final GameTestHelper helper) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		double health = healthAmount(mob);
		double damage = damageAmount(mob);
		boolean valid = health >= 20 * SpawnScaling.LEVEL_HEALTH_BONUS
			&& damage >= 20 * SpawnScaling.LEVEL_DAMAGE_BONUS;
		result("netherMobHasAtLeastTwentyThreatLevels",
			"healthAmount=" + health + " damageAmount=" + damage + " valid=" + valid);
		helper.assertTrue(valid, "Nether mob scaled below threat level 20");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_end")
	public void endMobHasAtLeastFiftyThreatLevels(final GameTestHelper helper) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		double health = healthAmount(mob);
		double damage = damageAmount(mob);
		boolean valid = health >= 50 * SpawnScaling.LEVEL_HEALTH_BONUS
			&& damage >= 50 * SpawnScaling.LEVEL_DAMAGE_BONUS;
		result("endMobHasAtLeastFiftyThreatLevels",
			"healthAmount=" + health + " damageAmount=" + damage + " valid=" + valid);
		helper.assertTrue(valid, "End mob scaled below threat level 50");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void netherTravelRaisesThreatBeyondItsMinimum(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		var spawn = helper.getLevel().getRespawnData().pos();
		mob.snapTo(spawn.getX() / 8.0 + 20_000.0, helper.getLevel().getSeaLevel() + 1,
			spawn.getZ() / 8.0, 0.0F, 0.0F);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(SpawnScaling.areaLevel(mob), 0));
		int area = SpawnScaling.areaLevel(mob);
		int threat = ThreatState.level(mob);
		boolean valid = area == 40 && threat == 40;
		result("netherTravelRaisesThreatBeyondItsMinimum",
			"area=" + area + " threat=" + threat + " valid=" + valid);
		helper.assertTrue(valid, "Nether travel did not raise threat beyond level 20");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_end")
	public void endTravelRaisesThreatBeyondItsMinimum(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		mob.snapTo(30_000.0, helper.getLevel().getSeaLevel() + 1, 0.0, 0.0F, 0.0F);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(SpawnScaling.areaLevel(mob), 0));
		int area = SpawnScaling.areaLevel(mob);
		int threat = ThreatState.level(mob);
		boolean valid = area == 60 && threat == 60;
		result("endTravelRaisesThreatBeyondItsMinimum",
			"area=" + area + " threat=" + threat + " valid=" + valid);
		helper.assertTrue(valid, "End travel did not raise threat beyond level 50");
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
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(0, -ThreatState.MAX_VARIATION));
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
			boolean valid = !mob.isAlive() && ThreatState.level(mob) == ThreatState.NETHER_MINIMUM
				&& orbs == 10;
			result("playerKillDropsThreatAdjustedExperienceOrbs",
				"mobLevel=" + ThreatState.level(mob) + " playerLevel=" + player.experienceLevel
					+ " orbExperience=" + orbs + " valid=" + valid);
			helper.assertTrue(valid, "player kill did not drop threat-adjusted XP orbs");
			helper.succeed();
		});
	}
	private static final double EPSILON = 1.0E-6;
	// No gear and no elite, so this test measures the scaling path alone.
	private static final String PLAIN_CONFIG = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	@GameTest
	public void threatAndDepthScalingCombine(final GameTestHelper helper) {
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.healthMultiplier(0, 0.0)), Double.valueOf(1.0), "health multiplier at level zero"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.healthMultiplier(1, 0.0)), Double.valueOf(1.05), "health multiplier at level one"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.healthMultiplier(0, 256.0)), Double.valueOf(1.25), "health multiplier at 256 blocks deep"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.healthMultiplier(10, 4096.0)), Double.valueOf(1.75), "health multiplier with ten levels and depth cap"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.damageMultiplier(1, 0.0)), Double.valueOf(1.025), "damage multiplier at level one"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.damageMultiplier(0, 256.0)), Double.valueOf(1.125), "damage multiplier at 256 blocks deep"
		);
		helper.assertValueEqual(
			Double.valueOf(SpawnScaling.damageMultiplier(10, 4096.0)), Double.valueOf(1.375), "damage multiplier with ten levels and depth cap"
		);
		result(
			"threatAndDepthScalingCombine",
			"healthAtZero=" + SpawnScaling.healthMultiplier(0, 0.0)
				+ " healthAtOne=" + SpawnScaling.healthMultiplier(1, 0.0)
				+ " healthAtDepthCap=" + SpawnScaling.healthMultiplier(0, 256.0)
				+ " healthAtTenAndDepth=" + SpawnScaling.healthMultiplier(10, 4096.0)
				+ " damageAtTenAndDepth=" + SpawnScaling.damageMultiplier(10, 4096.0)
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void farMobSpawnsScaled(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(PLAIN_CONFIG));

		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
		double x = zombie.getX();
		double y = zombie.getY();
		double z = zombie.getZ();
		double distance = Math.hypot(x - helper.getLevel().getRespawnData().pos().getX(), z - helper.getLevel().getRespawnData().pos().getZ());
		double depth = Math.max(0.0, helper.getLevel().getSeaLevel() - y);
		int threatLevel = ThreatState.level(zombie);
		double expected = SpawnScaling.healthMultiplier(threatLevel, depth);

		AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
		helper.assertTrue(health != null, "the zombie has no MAX_HEALTH attribute");
		AttributeModifier modifier = health.getModifier(SpawnScaling.HEALTH_ID);
		helper.assertTrue(modifier != null, "no spawn scaling modifier was applied");
		helper.assertTrue(
			Math.abs(modifier.amount() - (expected - 1.0)) < EPSILON,
			"the scaling amount " + modifier.amount() + " is not the expected " + (expected - 1.0)
				+ " at distance " + distance + " and depth " + depth
		);
		helper.assertValueEqual(
			Float.valueOf(zombie.getHealth()), Float.valueOf(zombie.getMaxHealth()), "the scaled mob healed to its new maximum"
		);
		result(
			"farMobSpawnsScaled",
			"dist=" + distance + " depth=" + depth + " threatLevel=" + threatLevel
				+ " healthMul=" + expected + " amount=" + modifier.amount()
				+ " hp=" + zombie.getHealth() + "/" + zombie.getMaxHealth()
		);
		helper.succeed();
	}

	@GameTest
	public void diskLoadDoesNotScaleTwice(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(PLAIN_CONFIG));

		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 1).spawn();
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
}
