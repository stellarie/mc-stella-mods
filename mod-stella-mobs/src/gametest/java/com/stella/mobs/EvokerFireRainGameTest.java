package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.EvokerStorm;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.phys.AABB;

/**
 * M8: the elite evoker's storm covers three times the old radius, drops a ball every interval, and
 * opens the fight the moment the evoker has a target, once per life.
 */
public class EvokerFireRainGameTest {
	/** Wider than the storm's radius, so a ball never lands in a neighbouring arena. */
	private static final int STORM_PADDING = 28;
	/** The goal selector re-evaluates an idle goal every second tick, so an instant cast lands within two. */
	private static final int OPENING_BOUND_TICKS = 2;
	private static final int RADIUS_SAMPLE_BALLS = 10;
	private static final int CADENCE_SAMPLE_BALLS = 4;
	private static final double CAST_WATCH_RADIUS = 16.0;
	private static final String QUIET = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}""";

	static {
		StormRecord.install();
	}

	@GameTest(maxTicks = 400, padding = STORM_PADDING)
	public void stormRadiusIsTripled(final GameTestHelper helper) {
		Evoker evoker = evoker(helper, true, false);
		startAtHalfHealth(helper, evoker);

		helper.succeedWhen(() -> {
			List<StormRecord.Ball> balls = StormRecord.balls(evoker);
			helper.assertTrue(balls.size() >= RADIUS_SAMPLE_BALLS, "only " + balls.size() + " balls so far");
			StormRecord.Begin begin = StormRecord.begins(evoker).get(0);
			double widest = 0.0;
			for (StormRecord.Ball ball : balls) {
				double reach = Math.max(Math.abs(ball.at().x - begin.centre().x), Math.abs(ball.at().z - begin.centre().z));
				helper.assertTrue(reach <= EvokerStorm.AREA_RADIUS + 1.0e-6, "a ball fell " + reach + " out, past " + EvokerStorm.AREA_RADIUS);
				widest = Math.max(widest, reach);
			}
			helper.assertTrue(widest > EvokerStorm.BASE_RADIUS, "no ball fell past the old radius " + EvokerStorm.BASE_RADIUS + "; widest " + widest);
			result("stormRadius", "balls=" + balls.size() + " widest=" + widest + " base=" + EvokerStorm.BASE_RADIUS + " radius=" + EvokerStorm.AREA_RADIUS);
		});
	}

	@GameTest(maxTicks = 400, padding = STORM_PADDING)
	public void stormIsTheOpeningAttack(final GameTestHelper helper) {
		Evoker evoker = evoker(helper, true, true);
		IronGolem golem = target(helper);
		evoker.setTarget(golem);
		int[] firstCast = {-1};
		AABB watch = evoker.getBoundingBox().inflate(CAST_WATCH_RADIUS);
		helper.onEachTick(() -> {
			boolean fangs = !helper.getLevel().getEntitiesOfClass(EvokerFangs.class, watch, fang -> fang.getOwner() == evoker).isEmpty();
			boolean vexes = !helper.getLevel().getEntitiesOfClass(Vex.class, watch, vex -> vex.getOwner() == evoker).isEmpty();
			if (firstCast[0] < 0 && (fangs || vexes)) {
				firstCast[0] = helper.getLevel().getServer().getTickCount();
			}
		});

		helper.succeedWhen(() -> {
			List<StormRecord.Begin> begins = StormRecord.begins(evoker);
			helper.assertTrue(firstCast[0] >= 0, "the evoker has not cast a fang or vex yet; storms " + begins);
			helper.assertTrue(!begins.isEmpty(), "the evoker cast a spell at " + firstCast[0] + " but never opened with the storm");
			StormRecord.Begin begin = begins.get(0);
			helper.assertTrue(
				EvokerStorm.REASON_OPENING.equals(begin.reason()) && begin.serverTick() <= firstCast[0],
				"the storm began at " + begin.serverTick() + " for " + begin.reason() + ", after the first spell at " + firstCast[0]
			);
			result("stormOpening", "stormAt=" + begin.serverTick() + " firstSpellAt=" + firstCast[0] + " reason=" + begin.reason());
		});
	}

	@GameTest(maxTicks = 100, padding = STORM_PADDING)
	public void stormCastsWithoutAWindUp(final GameTestHelper helper) {
		Evoker evoker = evoker(helper, true, true);
		evoker.setTarget(target(helper));
		int targetedAt = helper.getLevel().getServer().getTickCount();

		helper.succeedWhen(() -> {
			List<StormRecord.Begin> begins = StormRecord.begins(evoker);
			helper.assertTrue(!begins.isEmpty(), "no storm yet, " + (helper.getLevel().getServer().getTickCount() - targetedAt) + " ticks after the target");
			int delay = begins.get(0).serverTick() - targetedAt;
			helper.assertTrue(delay <= OPENING_BOUND_TICKS, "the storm began " + delay + " ticks after the target, past " + OPENING_BOUND_TICKS);
			result("stormNoWindUp", "delay=" + delay + " bound=" + OPENING_BOUND_TICKS);
		});
	}

	@GameTest(maxTicks = 400, padding = STORM_PADDING)
	public void stormBallCadenceMatchesTheInterval(final GameTestHelper helper) {
		Evoker evoker = evoker(helper, true, false);
		startAtHalfHealth(helper, evoker);

		helper.succeedWhen(() -> {
			List<StormRecord.Ball> balls = StormRecord.balls(evoker);
			helper.assertTrue(balls.size() >= CADENCE_SAMPLE_BALLS, "only " + balls.size() + " balls so far");
			List<Integer> gaps = new ArrayList<>();
			for (int i = 1; i < balls.size(); i++) {
				gaps.add(balls.get(i).serverTick() - balls.get(i - 1).serverTick());
			}
			boolean steady = gaps.stream().allMatch(gap -> gap == EvokerStorm.BALL_INTERVAL_TICKS);
			helper.assertTrue(steady, "balls fell " + gaps + " ticks apart, not " + EvokerStorm.BALL_INTERVAL_TICKS);
			result("stormCadence", "gaps=" + gaps + " interval=" + EvokerStorm.BALL_INTERVAL_TICKS);
		});
	}

	@GameTest(maxTicks = 200, padding = STORM_PADDING)
	public void stormStillCastsOncePerLife(final GameTestHelper helper) {
		Evoker evoker = evoker(helper, true, true);
		IronGolem golem = target(helper);
		evoker.setTarget(golem);

		helper.succeedWhen(() -> {
			helper.assertTrue(!StormRecord.begins(evoker).isEmpty(), "the opening storm has not begun");
			// A new target and a fall past half health are both triggers, and neither may cast again.
			evoker.setTarget(null);
			evoker.setTarget(golem);
			startAtHalfHealth(helper, evoker);
			int begins = StormRecord.begins(evoker).size();
			helper.assertTrue(begins == 1 && EvokerStorm.stormsFor(evoker) == 1, "the evoker cast " + begins + " storms in one life");
			result("stormOncePerLife", "begins=" + begins + " flag=" + EliteState.isStormCast(evoker));
		});
	}

	@GameTest(maxTicks = 100, padding = STORM_PADDING)
	public void aPlainEvokerNeverOpensWithTheStorm(final GameTestHelper helper) {
		Evoker evoker = evoker(helper, false, true);
		evoker.setTarget(target(helper));
		helper.runAfterDelay(60L, () -> {
			helper.assertFalse(Elite.isElite(evoker), "the subject was elite, so the case proves nothing");
			helper.assertTrue(evoker.getTarget() != null, "the evoker lost its target, so the case proves nothing");
			helper.assertTrue(StormRecord.begins(evoker).isEmpty(), "a plain evoker opened with the storm");
			result("stormPlainNoOpening", "begins=0 elite=false");
			helper.succeed();
		});
	}

	private static Evoker evoker(final GameTestHelper helper, final boolean elite, final boolean freeWill) {
		GearConfigStore.set(GearConfigParser.parse(QUIET));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		var spawner = helper.spawnMob(EntityTypes.EVOKER, 3, 3, 3);
		if (!freeWill) {
			spawner.withNoFreeWill();
		}
		Evoker evoker = spawner.spawnReason(EntitySpawnReason.NATURAL).spawn();
		evoker.setPersistenceRequired();
		evoker.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		if (elite) {
			Elite.mark(evoker, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		}
		return evoker;
	}

	private static IronGolem target(final GameTestHelper helper) {
		IronGolem golem = helper.spawnMob(EntityTypes.IRON_GOLEM, 3, 3, 7).withNoFreeWill().spawn();
		// Durable rather than invulnerable, because an invulnerable mob cannot be held as a target.
		golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		golem.setHealth(1000.0F);
		golem.setPersistenceRequired();
		return golem;
	}

	/** One real hit across half health, because that trigger rides the damage event. */
	private static void startAtHalfHealth(final GameTestHelper helper, final Evoker evoker) {
		evoker.setHealth(evoker.getMaxHealth() / 2.0F + 1.0F);
		evoker.setInvulnerableTime(0);
		evoker.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 2.0F);
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test=evokerFireRain.{} RESULT {}", test, detail);
	}
}
