package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.SpiderBroodGoal;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * R23, the elite spider: a guaranteed sword skeleton rider seated on the deferred tick through the
 * shipped seating path, and poison spiders bred on a cadence up to a cap that stops when the elite dies.
 * Every count names the relationship, which is the summoned flag, and every assertion is written so a
 * wandering brood cannot break it: the cap is an upper bound sampled over time, and the stop clause
 * compares identifiers inside a box frozen at the moment of death.
 */
public class SpiderEliteGameTest {
	private static final String TEST = "spiderElite";
	private static final int SAMPLE_INTERVAL = 200;
	// elite_chance 1.0 forces the roll, and roll_chance 0.0 keeps the subject free of rolled gear. This
	// window is short on purpose: the config store is global, and a long window leaks into the tests
	// that run beside this one in the same batch.
	private static final String ELITE_CONFIG = """
		{
			"entities": {},
			"rolls": { "elite_chance": 1.0, "boss_chance": 0.0, "roll_chance": 0.0 }
		}""";

	@GameTest(maxTicks = 200)
	public void eliteSpiderCarriesASwordSkeleton(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(ELITE_CONFIG));
			Arena.buildFloor(helper);
			helper.setTime(Arena.NIGHT_TIME);
			Spider spider = helper.spawnMob(EntityTypes.SPIDER, 2, 3, 2).spawn();

			helper.startSequence()
				.thenIdle(4)
				.thenExecute(() -> {
					List<Entity> passengers = spider.getPassengers();
					helper.assertTrue(Elite.isElite(spider), "the spider never rolled elite");
					helper.assertValueEqual(Integer.valueOf(passengers.size()), Integer.valueOf(1), "the elite spider's rider count");
					helper.assertTrue(passengers.get(0) instanceof Mob, "the rider is not a mob");
					Mob rider = (Mob) passengers.get(0);
					helper.assertTrue(rider instanceof Skeleton, "the rider is not a skeleton");
					helper.assertTrue(rider.getMainHandItem().is(Items.IRON_SWORD), "the rider holds no sword: " + rider.getMainHandItem());
					helper.assertTrue(EliteState.isSummoned(rider), "the rider is not flagged summoned");
					helper.assertFalse(Elite.isElite(rider), "the rider rolled elite itself");
					helper.assertTrue(hasBroodGoal(spider), "the elite spider got no brood goal from the installer");
					StellaMobs.LOGGER.info(
						"[BEHAVIOR] test={} RESULT passengers={} rider={} sword={} summoned={} riderElite={} broodGoal={}",
						TEST, passengers.size(), rider.getType(), rider.getMainHandItem(),
						EliteState.isSummoned(rider), Elite.isElite(rider), hasBroodGoal(spider)
					);
				})
				.thenSucceed();
		} finally {
			GearConfigStore.set(previous);
		}
	}

	/**
	 * Clause 2, asserted as the three things a dispersal cannot move: an arrival happens, the count never
	 * passes the cap at any sample, and no new identifier appears after the mother dies.
	 */
	@GameTest(maxTicks = 1400)
	public void eliteSpiderBroodsToItsCapAndStopsWhenItDies(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Spider spider = helper.spawnMob(EntityTypes.SPIDER, 2, 3, 2).spawn();
		// No config store write here: the elite is marked and the ability installed by hand, so a long
		// test cannot leak a non-default chance into the tests running beside it.
		Elite.mark(spider, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		BehaviorInstaller.installSpiderBrood(spider);
		// A wild cave spider near the elite is not summoned, so it must never consume the cap.
		CaveSpider wild = helper.spawnMob(EntityTypes.CAVE_SPIDER, 4, 3, 4).spawn();

		int[] ticks = new int[1];
		int[] maxSeen = new int[1];
		AABB[] frozen = new AABB[1];
		Set<UUID> atDeath = new HashSet<>();
		// Registered from the test body, never from a delayed callback.
		helper.onEachTick(() -> {
			ticks[0]++;
			AABB box = frozen[0] != null ? frozen[0] : broodBox(spider);
			int seen = broodIds(helper.getLevel(), box).size();
			maxSeen[0] = Math.max(maxSeen[0], seen);

			if (ticks[0] % SAMPLE_INTERVAL == 0) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={} SAMPLE t={} cadence={} cap={} brood={} maxSeen={} radius={}",
					TEST, ticks[0], SpiderBroodGoal.CADENCE_TICKS, SpiderBroodGoal.CAP, seen, maxSeen[0],
					SpiderBroodGoal.NEIGHBOUR_RADIUS
				);
			}
		});

		helper.startSequence()
			.thenIdle(SpiderBroodGoal.CADENCE_TICKS + 20)
			.thenExecute(() -> {
				int brood = broodCount(spider);
				helper.assertTrue(Elite.isElite(spider), "the hand marked spider is not elite");
				helper.assertTrue(brood >= 1, "no brood arrived by the first cadence of " + SpiderBroodGoal.CADENCE_TICKS);
				helper.assertFalse(EliteState.isSummoned(wild), "the rig's wild cave spider became a summon");
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={} RESULT cadence={} cap={} broodAfterFirstCadence={} wildSummoned={} radius={}",
					TEST, SpiderBroodGoal.CADENCE_TICKS, SpiderBroodGoal.CAP, brood,
					EliteState.isSummoned(wild), SpiderBroodGoal.NEIGHBOUR_RADIUS
				);
			})
			.thenIdle(SpiderBroodGoal.CADENCE_TICKS * (SpiderBroodGoal.CAP + 2))
			.thenExecute(() -> {
				helper.assertTrue(maxSeen[0] >= 1, "the sampler never saw a brood, so the ability never fired");
				// The goal counts a radius, so a brood member that strays outside reads as absent and the
				// goal may spawn one more: the observed maximum can sit one above the cap. The cap is what
				// the goal's own count respects, and this ceiling allows exactly one stray, named here.
				helper.assertTrue(
					maxSeen[0] <= SpiderBroodGoal.CAP + 1,
					"the brood passed its cap of " + SpiderBroodGoal.CAP + " by more than one stray: " + maxSeen[0]
				);
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={} RESULT cadence={} cap={} maxSeen={} samples={}",
					TEST, SpiderBroodGoal.CADENCE_TICKS, SpiderBroodGoal.CAP, maxSeen[0], ticks[0]
				);
			})
			.thenExecute(() -> {
				frozen[0] = broodBox(spider);
				atDeath.addAll(broodIds(helper.getLevel(), frozen[0]));
				spider.kill(helper.getLevel());
			})
			.thenIdle(SpiderBroodGoal.CADENCE_TICKS * 2)
			.thenExecute(() -> {
				Set<UUID> arrived = new HashSet<>(broodIds(helper.getLevel(), frozen[0]));
				arrived.removeAll(atDeath);
				helper.assertValueEqual(
					Integer.valueOf(arrived.size()), Integer.valueOf(0), "brood identifiers that appeared after the mother died"
				);
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={} RESULT cadence={} cap={} atDeath={} arrivedAfterDeath={}",
					TEST, SpiderBroodGoal.CADENCE_TICKS, SpiderBroodGoal.CAP, atDeath.size(), arrived.size()
				);
			})
			.thenSucceed();
	}

	private static boolean hasBroodGoal(final Spider spider) {
		for (WrappedGoal wrapped : spider.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof SpiderBroodGoal) {
				return true;
			}
		}

		return false;
	}

	private static AABB broodBox(final Spider spider) {
		return spider.getBoundingBox().inflate(SpiderBroodGoal.NEIGHBOUR_RADIUS);
	}

	private static int broodCount(final Spider spider) {
		return broodIds(spider.level(), broodBox(spider)).size();
	}

	/** Summoned cave spiders in a box: the relationship, not the kind, is the filter. */
	private static Set<UUID> broodIds(final Level level, final AABB box) {
		Set<UUID> ids = new HashSet<>();

		for (CaveSpider spider : level.getEntities(EntityTypes.CAVE_SPIDER, box, EliteState::isSummoned)) {
			ids.add(spider.getUUID());
		}

		return ids;
	}
}
