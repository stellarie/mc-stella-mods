package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.PearlThrowGoal;
import com.stella.mobs.behavior.RiderSquad;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RiderSpec;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * R09 riders and R25 ender pearls on a headless rig. Every chance is pinned through the config, so no
 * assertion depends on luck: the rider tests set 1.0 or 0.0, and the pearl tests pin the elite roll.
 */
public class RiderPearlGameTest {
	private static final String RIDERS_ALWAYS = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 },
			"riders": { "chance": 1.0 }
		}""";
	private static final String RIDERS_NEVER = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 },
			"riders": { "chance": 0.0 }
		}""";
	private static final String RIDERS_SPIDER_ONLY = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 },
			"riders": {
				"chance": 0.40,
				"entities": { "minecraft:spider": { "chance": 1.0 } }
			}
		}""";
	private static final String ELITE_ONLY = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 1.0 }
		}""";
	private static final String NO_ELITE = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";
	private static final int SPAWN_X = 3;
	private static final int SPAWN_Y = 3;
	private static final int SPAWN_Z = 3;
	private static final int MAX_TICKS = 200;
	private static final double RIDER_BOX = 4.0;
	private static final int PEARL_WAIT_TICKS = 100;
	// The target stands on a perch above the arena, so the line of sight between the pair crosses only
	// air. A perch at the subject's own level is blocked by the stone that surrounds every arena.
	private static final int PERCH_X = 3;
	private static final int PERCH_Y = 14;
	private static final int PERCH_Z = 13;

	@GameTest(maxTicks = MAX_TICKS)
	public void spiderSpawnsWithASkeletonRider(final GameTestHelper helper) {
		Spider spider = spawnSpider(helper, RIDERS_ALWAYS);

		helper.succeedWhen(() -> helper.assertTrue(
			hasSkeletonRider(helper, spider), "the spider never gained a rider: " + describe(spider)
		));
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void riderIsSeatedOnTheMount(final GameTestHelper helper) {
		Spider spider = spawnSpider(helper, RIDERS_ALWAYS);

		helper.succeedWhen(() -> {
			Skeleton rider = skeletonRider(helper, spider);
			helper.assertTrue(rider != null, "the spider never gained a rider: " + describe(spider));
			result(
				"riderIsSeatedOnTheMount",
				"rider=" + name(rider.getType()) + " seated=" + (rider.getVehicle() == spider)
					+ " alive=" + rider.isAlive() + " distance=" + spider.distanceTo(rider)
			);
			helper.assertTrue(rider.getVehicle() == spider, "the rider is not seated on the spider");
			helper.assertTrue(rider.isAlive(), "the rider is not alive");
			// The flag the negative case keys on: this mod's riders are summoned before they are added,
			// which is what tells them apart from vanilla's own spider jockey.
			helper.assertTrue(
				EliteState.isSummoned(rider), "the seated rider is not flagged summoned, so the flag is not a rider mark"
			);
			helper.assertTrue(
				spider.distanceTo(rider) < 1.5, "the rider is " + spider.distanceTo(rider) + " blocks from the spider"
			);
		});
	}

	/**
	 * The clause is "this mod seats no rider", not "the spider carries no passenger". Vanilla's own
	 * `Spider.finalizeSpawn` rolls a one percent skeleton jockey with `EntitySpawnReason.JOCKEY`, so a
	 * plain passenger count measures vanilla's dice. Every rider this mod seats is flagged summoned
	 * before it is added, so that flag is what tells the two apart.
	 */
	@GameTest(maxTicks = MAX_TICKS)
	public void zeroChanceSpawnsNoRider(final GameTestHelper helper) {
		Spider spider = spawnSpider(helper, RIDERS_NEVER);

		helper.runAfterDelay(60L, () -> {
			boolean modRider = spider.getPassengers().stream()
				.anyMatch(passenger -> passenger instanceof Mob mob && EliteState.isSummoned(mob));
			result(
				"zeroChanceSpawnsNoRider",
				"modRiders=" + (modRider ? 1 : 0) + " ridersOff=" + !spider.isVehicle()
					+ " passengers=" + spider.getPassengers().size()
			);
			helper.assertFalse(
				modRider, "a zero rider chance still seated a summoned rider: " + describe(spider)
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void riderChanceIsParsedGloballyAndPerMount(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(RIDERS_SPIDER_ONLY));
		double global = GearConfigStore.get().riderDefaults().chance();
		double spider = GearConfigStore.get().riderFor(Identifier.parse("minecraft:spider")).chance();
		double zombie = GearConfigStore.get().riderFor(Identifier.parse("minecraft:zombie")).chance();

		result(
			"riderChanceIsParsedGloballyAndPerMount",
			"global=" + global + " spider=" + spider + " zombie=" + zombie
		);

		helper.assertTrue(Math.abs(global - 0.40) < 1.0e-6, "the global rider chance is " + global);
		helper.assertTrue(Math.abs(spider - 1.0) < 1.0e-6, "the spider rider override is " + spider);
		helper.assertTrue(Math.abs(zombie - 0.40) < 1.0e-6, "the zombie inherited " + zombie);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void shippedDefaultCarriesTheRiderChance(final GameTestHelper helper) {
		// Read the shipped default, not whatever config a previously run test left behind.
		GearConfigStore.set(GearConfig.EMPTY);
		RiderSpec shipped = GearConfigStore.get().riderDefaults();
		result(
			"shippedDefaultCarriesTheRiderChance",
			"shipped=" + shipped.chance() + " constant=" + RiderSpec.DEFAULT_CHANCE
				+ " pairs=" + RiderSquad.RIDER_TYPES.size()
		);

		helper.assertTrue(
			Math.abs(shipped.chance() - RiderSpec.DEFAULT_CHANCE) < 1.0e-6,
			"the shipped rider chance is " + shipped.chance() + ", not " + RiderSpec.DEFAULT_CHANCE
		);
		helper.assertTrue(!RiderSquad.RIDER_TYPES.isEmpty(), "no mount has a mapped rider type");
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void eliteThrowsAPearlAtADistantTarget(final GameTestHelper helper) {
		Zombie elite = spawnElite(helper, ELITE_ONLY);
		Mob target = spawnGolemTarget(helper);
		helper.onEachTick(() -> elite.setTarget(target));

		helper.succeedWhen(() -> {
			List<ThrownEnderpearl> pearls = pearlsOf(helper, elite);
			helper.assertTrue(!pearls.isEmpty(), "the elite threw no pearl: " + describe(elite));
			result(
				"eliteThrowsAPearlAtADistantTarget",
				"pearls=" + pearls.size() + " pearlsLeft=" + EliteState.pearlsLeft(elite)
					+ " distance=" + String.format(Locale.ROOT, "%.2f", elite.distanceTo(target))
			);
			helper.assertTrue(
				EliteState.pearlsLeft(elite) < PearlThrowGoal.PEARL_COUNT,
				"the pearl budget did not decrease: " + EliteState.pearlsLeft(elite)
			);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void pearlCarriesTheEliteToTheTarget(final GameTestHelper helper) {
		Zombie elite = spawnElite(helper, ELITE_ONLY);
		Mob target = spawnGolemTarget(helper);
		helper.onEachTick(() -> elite.setTarget(target));
		double before = elite.distanceTo(target);

		helper.succeedWhen(() -> {
			double now = elite.distanceTo(target);
			helper.assertTrue(
				now < before, "the elite never closed on the target: " + before + " then " + now
			);
			result(
				"pearlCarriesTheEliteToTheTarget",
				"before=" + String.format(Locale.ROOT, "%.2f", before)
					+ " after=" + String.format(Locale.ROOT, "%.2f", now)
			);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void nonEliteThrowsNoPearl(final GameTestHelper helper) {
		Zombie plain = spawnElite(helper, NO_ELITE);
		Mob target = spawnGolemTarget(helper);
		helper.onEachTick(() -> plain.setTarget(target));

		helper.runAfterDelay(PEARL_WAIT_TICKS, () -> {
			List<ThrownEnderpearl> pearls = pearlsOf(helper, plain);
			result(
				"nonEliteThrowsNoPearl",
				"elite=" + Elite.isElite(plain) + " pearls=" + pearls.size()
					+ " pearlsLeft=" + EliteState.pearlsLeft(plain)
			);
			helper.assertFalse(Elite.isElite(plain), "a zero elite chance still marked the zombie");
			helper.assertTrue(pearls.isEmpty(), "a non-elite threw " + pearls.size() + " pearls");
			helper.assertTrue(
				EliteState.pearlsLeft(plain) == 0, "a non-elite carries a pearl budget: " + EliteState.pearlsLeft(plain)
			);
			helper.succeed();
		});
	}

	private static Spider spawnSpider(final GameTestHelper helper, final String config) {
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		Spider spider = helper.spawnMob(EntityTypes.SPIDER, SPAWN_X, SPAWN_Y, SPAWN_Z).spawnReason(EntitySpawnReason.NATURAL).spawn();
		spider.setPersistenceRequired();
		return spider;
	}

	private static Zombie spawnElite(final GameTestHelper helper, final String config) {
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		clearSpawnSpace(helper, SPAWN_X, SPAWN_Y, SPAWN_Z);
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, SPAWN_X, SPAWN_Y, SPAWN_Z).spawnReason(EntitySpawnReason.NATURAL).spawn();
		zombie.setPersistenceRequired();
		// Near-zero speed, so a closing distance later can only be the pearl teleport, not a walk.
		zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.001);
		zombie.setTarget(null);
		return zombie;
	}

	/**
	 * The subject stands on a platform inside the same air pocket the player uses. Without this the mob
	 * spawns inside solid stone, never lands, and the goal's ground gate is never satisfied.
	 */
	private static void clearSpawnSpace(final GameTestHelper helper, final int x, final int y, final int z) {
		helper.setBlock(x, y, z, Blocks.STONE);
		helper.setBlock(x, y + 1, z, Blocks.AIR);
		helper.setBlock(x, y + 2, z, Blocks.AIR);
	}

	/** The perch stands above the arena, with air above it, so nothing blocks the sight line. */
	private static void preparePlayerPerch(final GameTestHelper helper) {
		for (int dy = 0; dy <= 2; dy++) {
			helper.setBlock(PERCH_X, PERCH_Y + dy, PERCH_Z, Blocks.AIR);
		}

		helper.setBlock(PERCH_X, PERCH_Y - 1, PERCH_Z, Blocks.STONE);
	}

	/**
	 * A passive, defenceless target above the arena. An iron golem would kill the subject before the
	 * pearl goal ever fires, and a mock player is dropped by Mob.setTarget while it is in creative.
	 */
	private static Mob spawnGolemTarget(final GameTestHelper helper) {
		preparePlayerPerch(helper);
		Mob target = helper.spawnMob(EntityTypes.IRON_GOLEM, PERCH_X, PERCH_Y, PERCH_Z).spawn();
		target.setPersistenceRequired();
		target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		return target;
	}

	private static boolean hasSkeletonRider(final GameTestHelper helper, final Spider spider) {
		return skeletonRider(helper, spider) != null;
	}

	private static Skeleton skeletonRider(final GameTestHelper helper, final Spider spider) {
		for (var passenger : spider.getPassengers()) {
			if (passenger instanceof Skeleton skeleton) {
				return skeleton;
			}
		}

		List<Skeleton> nearby = helper.getLevel()
			.getEntitiesOfClass(Skeleton.class, spider.getBoundingBox().inflate(RIDER_BOX), candidate -> true);
		return nearby.isEmpty() ? null : nearby.getFirst();
	}

	private static List<ThrownEnderpearl> pearlsOf(final GameTestHelper helper, final Mob elite) {
		return helper.getLevel()
			.getEntitiesOfClass(
				ThrownEnderpearl.class,
				elite.getBoundingBox().inflate(64.0),
				pearl -> pearl.getOwner() == elite
			);
	}

	private static String describe(final Mob mob) {
		return "passengers=" + mob.getPassengers().size() + " tick=" + mob.tickCount;
	}

	private static String name(final EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
