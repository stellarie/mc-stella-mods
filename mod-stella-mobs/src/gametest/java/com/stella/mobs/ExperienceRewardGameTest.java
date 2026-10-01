package com.stella.mobs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.ThreatExperience;
import com.stella.mobs.scaling.ThreatState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kill experience is a base per mob type plus a part from the mob level and its gap to the killer. These
 * tests fix the contract: the formula table, the base table, vanilla zero and the dragon staying vanilla,
 * the elite and Nemesis multipliers, the unrolled mob, and the config keys.
 */
public class ExperienceRewardGameTest {
	private static final String NEMESIS = "stella_nemesis";
	private static final int ZOMBIE_BASE = 6;

	@GameTest
	public void formula(final GameTestHelper helper) {
		withShippedConfig(() -> checkFormula(helper));
	}

	private static void checkFormula(final GameTestHelper helper) {
		int[][] table = {
			{0, 0, 6}, {50, 50, 81}, {40, 50, 36}, {60, 50, 141}, {20, 50, 9}, {100, 50, 231}
		};
		StringBuilder measured = new StringBuilder();
		boolean valid = true;
		for (int[] row : table) {
			int actual = ThreatExperience.reward(ZOMBIE_BASE, row[0], row[1]);
			measured.append("T").append(row[0]).append("/L").append(row[1]).append('=').append(actual).append(' ');
			valid &= actual == row[2];
		}
		int floor = ThreatExperience.reward(0, 0, 0);
		measured.append("floor=").append(floor);
		result("formula", measured + " valid=" + valid);
		helper.assertTrue(valid && floor == 1, "the reward table differs: " + measured);
		helper.succeed();
	}

	/** A scaling.json left in the run directory must not move the contract these tests pin. */
	private static void withShippedConfig(final Runnable body) {
		ScalingConfig previous = ScalingConfigStore.get();
		try {
			ScalingConfigStore.set(ScalingConfig.DEFAULTS);
			body.run();
		} finally {
			ScalingConfigStore.set(previous);
		}
	}

	@GameTest
	public void baseTable(final GameTestHelper helper) {
		withShippedConfig(() -> checkBaseTable(helper));
	}

	private static void checkBaseTable(final GameTestHelper helper) {
		Map<EntityType<? extends Mob>, Integer> expected = Map.ofEntries(
			Map.entry(EntityTypes.SLIME, 2), Map.entry(EntityTypes.MAGMA_CUBE, 2), Map.entry(EntityTypes.SILVERFISH, 2),
			Map.entry(EntityTypes.ENDERMITE, 3), Map.entry(EntityTypes.VEX, 3),
			Map.entry(EntityTypes.ZOMBIE, 6), Map.entry(EntityTypes.SKELETON, 6), Map.entry(EntityTypes.CREEPER, 6),
			Map.entry(EntityTypes.SPIDER, 6), Map.entry(EntityTypes.PIGLIN, 6),
			Map.entry(EntityTypes.HOGLIN, 8), Map.entry(EntityTypes.ZOGLIN, 8),
			Map.entry(EntityTypes.ENDERMAN, 12), Map.entry(EntityTypes.WITCH, 12), Map.entry(EntityTypes.BLAZE, 12),
			Map.entry(EntityTypes.SHULKER, 12),
			Map.entry(EntityTypes.PIGLIN_BRUTE, 20), Map.entry(EntityTypes.ILLUSIONER, 20),
			Map.entry(EntityTypes.EVOKER, 40), Map.entry(EntityTypes.RAVAGER, 40), Map.entry(EntityTypes.ELDER_GUARDIAN, 40),
			Map.entry(EntityTypes.WITHER, 500), Map.entry(EntityTypes.WARDEN, 500)
		);
		List<String> wrong = new ArrayList<>();
		expected.forEach((type, base) -> {
			Mob mob = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
			int actual = ThreatExperience.base(mob);
			if (actual != base) {
				wrong.add(type + "=" + actual + " want " + base);
			}
		});
		ScalingConfig shipped = ScalingConfig.DEFAULTS;
		int unlistedHostile = ThreatExperience.base(EntityTypes.GIANT.create(helper.getLevel(), EntitySpawnReason.COMMAND));
		int unlistedPassive = ThreatExperience.base(EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.COMMAND));
		boolean dragonListed = shipped.xpBase().containsKey("minecraft:ender_dragon");
		boolean valid = wrong.isEmpty() && unlistedHostile == shipped.xpBaseHostile()
			&& unlistedPassive == shipped.xpBasePassive() && !dragonListed;
		result("baseTable", "wrong=" + wrong + " unlistedHostile=" + unlistedHostile
			+ " unlistedPassive=" + unlistedPassive + " dragonListed=" + dragonListed + " valid=" + valid);
		helper.assertTrue(valid, "the base table differs: wrong=" + wrong + " hostile=" + unlistedHostile
			+ " passive=" + unlistedPassive);
		helper.succeed();
	}

	@GameTest
	public void baseFollowsConfig(final GameTestHelper helper) {
		ScalingConfig previous = ScalingConfigStore.get();
		int zombie;
		int cow;
		int unlistedHostile;
		int unlistedPassive;
		try {
			ScalingConfigStore.set(ScalingConfigStore.parse(
				"{\"xp_base\": {\"zombie\": 9, \"minecraft:cow\": 4}, \"xp_base_hostile\": 7, \"xp_base_passive\": 2}",
				message -> { }));
			zombie = ThreatExperience.base(EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND));
			cow = ThreatExperience.base(EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.COMMAND));
			unlistedHostile = ThreatExperience.base(EntityTypes.GIANT.create(helper.getLevel(), EntitySpawnReason.COMMAND));
			unlistedPassive = ThreatExperience.base(EntityTypes.PIG.create(helper.getLevel(), EntitySpawnReason.COMMAND));
		} finally {
			ScalingConfigStore.set(previous);
		}
		result("baseFollowsConfig", "zombie=" + zombie + " cow=" + cow
			+ " hostile=" + unlistedHostile + " passive=" + unlistedPassive);
		helper.assertTrue(zombie == 9 && cow == 4 && unlistedHostile == 7 && unlistedPassive == 2,
			"config did not reach the base: " + zombie + "/" + cow + "/" + unlistedHostile + "/" + unlistedPassive);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void zeroStaysZero(final GameTestHelper helper) {
		ServerPlayer player = player(helper, 30);
		Mob villager = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Mob golem = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		int villagerReward = reward(helper, villager, player);
		int golemReward = reward(helper, golem, player);
		Cow baby = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		baby.setBaby(true);
		killedBy(helper, baby, player, 2);
		helper.runAfterDelay(2, () -> {
			int babyOrbs = orbsAround(helper, baby);
			result("zeroStaysZero", "villager=" + villagerReward + " golem=" + golemReward + " babyOrbs=" + babyOrbs
				+ " babyAlive=" + baby.isAlive());
			helper.assertTrue(villagerReward == 0 && golemReward == 0 && babyOrbs == 0 && !baby.isAlive(),
				"a vanilla zero became XP: villager=" + villagerReward + " golem=" + golemReward + " babyOrbs=" + babyOrbs);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void adultCowKillDropsFormulaExperience(final GameTestHelper helper) {
		ServerPlayer player = player(helper, 30);
		Cow cow = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		killedBy(helper, cow, player, 2);
		helper.runAfterDelay(2, () -> {
			int orbs = orbsAround(helper, cow);
			int expected = ThreatExperience.reward(ThreatExperience.base(cow), ThreatState.level(cow), 30);
			result("adultCowKillDropsFormulaExperience", "orbs=" + orbs + " expected=" + expected
				+ " level=" + ThreatState.level(cow));
			helper.assertTrue(!cow.isAlive() && orbs == expected, "the cow kill dropped " + orbs + ", want " + expected);
			helper.succeed();
		});
	}

	@GameTest
	public void eliteAndNemesis(final GameTestHelper helper) {
		withShippedConfig(() -> checkEliteAndNemesis(helper));
	}

	private static void checkEliteAndNemesis(final GameTestHelper helper) {
		ServerPlayer player = player(helper, 30);
		int plain = reward(helper, unrolledZombie(helper), player);

		Mob elite = unrolledZombie(helper);
		EliteState.markElite(elite, GearTier.DIAMOND);
		int eliteReward = reward(helper, elite, player);

		Mob nemesis = unrolledZombie(helper);
		nemesis.addTag(NEMESIS);
		int nemesisReward = reward(helper, nemesis, player);

		Mob both = unrolledZombie(helper);
		EliteState.markElite(both, GearTier.DIAMOND);
		both.addTag(NEMESIS);
		int bothReward = reward(helper, both, player);

		int onLevel = ThreatExperience.reward(ZOMBIE_BASE, 30, 30);
		result("eliteAndNemesis", "plain=" + plain + " elite=" + eliteReward + " nemesis=" + nemesisReward
			+ " both=" + bothReward + " onLevel=" + onLevel);
		helper.assertTrue(plain == onLevel && eliteReward == onLevel * 3 && nemesisReward == onLevel * 5
				&& bothReward == onLevel * 5,
			"multipliers differ: plain=" + plain + " elite=" + eliteReward + " nemesis=" + nemesisReward + " both=" + bothReward);
		helper.succeed();
	}

	@GameTest
	public void unrolledMobUsesKillerLevel(final GameTestHelper helper) {
		withShippedConfig(() -> checkUnrolledMob(helper));
	}

	private static void checkUnrolledMob(final GameTestHelper helper) {
		ServerPlayer player = player(helper, 30);
		Mob zombie = unrolledZombie(helper);
		((AttachmentTarget) zombie).setAttached(ThreatState.TYPE, new ThreatState(0, ThreatState.MAX_VARIATION, 0, 400));
		Mob enderman = EntityTypes.ENDERMAN.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		enderman.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		int zombieReward = reward(helper, zombie, player);
		int endermanReward = reward(helper, enderman, player);
		result("unrolledMobUsesKillerLevel", "zombie=" + zombieReward + " enderman=" + endermanReward);
		helper.assertTrue(zombieReward == 51 && endermanReward == 57,
			"an unrolled mob did not count as on level: zombie=" + zombieReward + " enderman=" + endermanReward);
		helper.succeed();
	}

	@GameTest
	public void dragonUnchanged(final GameTestHelper helper) {
		ServerPlayer player = player(helper, 30);
		Mob dragon = EntityTypes.ENDER_DRAGON.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Mob wolf = EntityTypes.WOLF.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		int byPlayer = dragon.getExperienceReward(helper.getLevel(), player);
		int withoutPlayer = dragon.getExperienceReward(helper.getLevel(), wolf);
		result("dragonUnchanged", "byPlayer=" + byPlayer + " withoutPlayer=" + withoutPlayer);
		helper.assertTrue(byPlayer == withoutPlayer,
			"the dragon reward changed: byPlayer=" + byPlayer + " vanilla=" + withoutPlayer);
		helper.succeed();
	}

	@GameTest
	public void configKeys(final GameTestHelper helper) throws Exception {
		List<String> errors = new ArrayList<>();
		ScalingConfig previous = ScalingConfigStore.get();
		JsonObject written;
		ScalingConfig reread;
		ScalingConfig legacy;
		try {
			Path file = Files.createTempDirectory("stella-xp").resolve("scaling.json");
			ScalingConfigStore.loadFrom(file, errors::add);
			written = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			reread = ScalingConfigStore.parse(written.toString(), errors::add);
			legacy = ScalingConfigStore.parse("{\"xp_level_divisor\": 25}", errors::add);
		} finally {
			ScalingConfigStore.set(previous);
		}
		ScalingConfig d = ScalingConfig.DEFAULTS;
		boolean keys = written.has("xp_base") && written.get("xp_base").isJsonObject()
			&& !written.has("xp_level_divisor")
			&& near(written, "xp_base_hostile", d.xpBaseHostile()) && near(written, "xp_base_passive", d.xpBasePassive())
			&& near(written, "xp_level_factor", d.xpLevelFactor()) && near(written, "xp_gap_step", d.xpGapStep())
			&& near(written, "xp_gap_min", d.xpGapMin()) && near(written, "xp_gap_max", d.xpGapMax())
			&& near(written, "xp_elite_multiplier", d.xpEliteMultiplier())
			&& near(written, "xp_nemesis_multiplier", d.xpNemesisMultiplier());
		boolean shipped = d.xpBaseHostile() == 6 && d.xpBasePassive() == 1 && d.xpLevelFactor() == 1.5
			&& d.xpGapStep() == 0.05 && d.xpGapMin() == 0.1 && d.xpGapMax() == 1.5
			&& d.xpEliteMultiplier() == 3.0 && d.xpNemesisMultiplier() == 5.0;
		boolean retired = legacy.equals(d)
			&& errors.stream().anyMatch(message -> message.contains("xp_level_divisor") && message.contains("retired"));
		result("configKeys", "keys=" + keys + " shipped=" + shipped + " roundTrip=" + reread.equals(d)
			+ " retired=" + retired + " errors=" + errors);
		helper.assertTrue(keys && shipped && reread.equals(d) && retired,
			"xp config keys differ: keys=" + keys + " shipped=" + shipped + " retired=" + retired + " errors=" + errors);
		helper.succeed();
	}

	private static boolean near(final JsonObject object, final String key, final double expected) {
		return object.has(key) && Math.abs(object.get(key).getAsDouble() - expected) < 1.0E-9;
	}

	private static int reward(final GameTestHelper helper, final Mob mob, final ServerPlayer player) {
		return mob.getExperienceReward(helper.getLevel(), player);
	}

	private static void killedBy(final GameTestHelper helper, final Mob mob, final ServerPlayer player, final int height) {
		var level = helper.getLevel();
		var position = helper.absolutePos(new BlockPos(2, height + 1, 2));
		mob.snapTo(Vec3.atBottomCenterOf(position), 0.0F, 0.0F);
		level.addFreshEntity(mob);
		player.snapTo(mob.getX() + 4.0, mob.getY(), mob.getZ(), 0.0F, 0.0F);
		mob.setLastHurtByPlayer(player, 100);
		mob.setHealth(1.0F);
		mob.hurt(level.damageSources().playerAttack(player), 20.0F);
	}

	private static int orbsAround(final GameTestHelper helper, final Mob mob) {
		AABB nearby = AABB.ofSize(mob.position(), 8.0, 8.0, 8.0);
		return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, nearby).stream()
			.mapToInt(ExperienceOrb::getValue).sum();
	}

	private static Mob unrolledZombie(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		return mob;
	}

	private static ServerPlayer player(final GameTestHelper helper, final int level) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.experienceLevel = level;
		return player;
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
