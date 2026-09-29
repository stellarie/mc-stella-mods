package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** An archer keeps farther from a player who rushes, and does not change for one who does not. */
public class LearnedBandGameTest {
	private static final int MAX_TICKS = 200;
	private static final double BASE_NEAR = 5.0;
	private static final double BASE_FAR = 15.0;
	// The game test clock moves a few dozen ticks during a test, which decays a fresh count by about half a percent.
	private static final double TOLERANCE = 0.1;
	private static final String CONFIG = """
		{
			"version": 1,
			"entities": { "minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } } },
			"roles": { "minecraft:skeleton": { "role": "ranged", "near": %s, "far": %s } },
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""".formatted(BASE_NEAR, BASE_FAR);

	private static Mob archer(final GameTestHelper helper, final ServerPlayer player) {
		return archer(helper, player, 2, 2);
	}

	private static Mob archer(final GameTestHelper helper, final ServerPlayer player, final int x, final int z) {
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(CONFIG));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, x, LearnArena.SPAWN_Y, z).spawn();
		LearnArena.keepTargeting(helper, skeleton, player);
		return skeleton;
	}

	private static void rush(final ServerPlayer player, final double hits) {
		for (int i = 0; i < hits; i++) {
			Profiles.record(player, Habit.MELEE);
		}
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aFullRusherPushesTheNearBandOutByTheGain(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(40L, () -> {
			ReactiveState state = ReactiveState.get(skeleton);
			double want = BASE_NEAR + LearnConfigStore.get().rushBandGain();
			StellaLearn.LOGGER.info(
				"[BEHAVIOR] test=aFullRusherPushesTheNearBandOutByTheGain RESULT near={} far={} want={}", state.getNearDistance(), state.getFarDistance(), want
			);
			helper.assertTrue(Math.abs(state.getNearDistance() - want) < TOLERANCE, "near band was " + state.getNearDistance());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void theBandKeepsItsWidthWhenItMoves(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(40L, () -> {
			ReactiveState state = ReactiveState.get(skeleton);
			double width = state.getFarDistance() - state.getNearDistance();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theBandKeepsItsWidthWhenItMoves RESULT width={} base={}", width, BASE_FAR - BASE_NEAR);
			helper.assertTrue(Math.abs(width - (BASE_FAR - BASE_NEAR)) < TOLERANCE, "band width was " + width);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void halfARusherPushesHalfAsFar(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation() / 2.0);
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(40L, () -> {
			double near = ReactiveState.get(skeleton).getNearDistance();
			double want = BASE_NEAR + LearnConfigStore.get().rushBandGain() / 2.0;
			StellaLearn.LOGGER.info("[BEHAVIOR] test=halfARusherPushesHalfAsFar RESULT near={} want={}", near, want);
			helper.assertTrue(Math.abs(near - want) < TOLERANCE, "near band was " + near);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aPlayerWhoNeverRushesLeavesTheBandAlone(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		Profiles.record(player, Habit.RANGED);
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(40L, () -> {
			double near = ReactiveState.get(skeleton).getNearDistance();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aPlayerWhoNeverRushesLeavesTheBandAlone RESULT near={}", near);
			helper.assertTrue(Math.abs(near - BASE_NEAR) < TOLERANCE, "near band was " + near);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aForgottenRushReturnsTheBand(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		Mob skeleton = archer(helper, player);
		helper.runAfterDelay(30L, () -> {
			double wide = ReactiveState.get(skeleton).getNearDistance();
			// Ten half lives leave under a thousandth of the count.
			long halfLife = LearnConfigStore.get().profileHalfLifeTicks();
			Profiles.set(player, PlayerProfile.EMPTY.record(
				Habit.MELEE, LearnConfigStore.get().rushSaturation(), LearnClock.now() - halfLife * 10L, halfLife
			));
			helper.runAfterDelay(30L, () -> {
				double near = ReactiveState.get(skeleton).getNearDistance();
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aForgottenRushReturnsTheBand RESULT wide={} after={}", wide, near);
				helper.assertTrue(wide > BASE_NEAR + 1.0, "the rush never widened the band, so the case proves nothing");
				helper.assertTrue(near - BASE_NEAR < 0.01, "near band stayed at " + near);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aMeleeMobIsNotGivenABand(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 12.5, 12.5);
		rush(player, LearnConfigStore.get().rushSaturation());
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, LearnArena.SPAWN_Y, 2).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		helper.runAfterDelay(40L, () -> {
			double near = ReactiveState.get(zombie).getNearDistance();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aMeleeMobIsNotGivenABand RESULT near={}", near);
			helper.assertTrue(near == 0.0, "a zombie was given a near band of " + near);
			helper.succeed();
		});
	}

	/** The mobs mod picks each flee spot at random and rests 40 ticks between tries, so the test waits for one that works. */
	@GameTest(maxTicks = 400)
	public void anArcherBacksOffFromARusher(final GameTestHelper helper) {
		LearnArena.floor(helper);
		// Eight blocks: inside the widened near band of 11, outside the plain one of 5, with room to back off.
		ServerPlayer rusher = LearnArena.player(helper, 32.5, 24.5);
		rush(rusher, LearnConfigStore.get().rushSaturation());
		Mob skeleton = archer(helper, rusher, 24, 24);
		helper.succeedWhen(() -> {
			double distance = skeleton.distanceTo(rusher);
			if (distance > 10.5) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherBacksOffFromARusher RESULT distance={}", distance);
			}

			helper.assertTrue(distance > 10.5, "the archer is only " + distance + " from a rusher");
		});
	}

	@GameTest(maxTicks = 400)
	public void anArcherStaysPutForAPlayerWhoNeverRushes(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer shooter = LearnArena.player(helper, 32.5, 24.5);
		Profiles.record(shooter, Habit.RANGED);
		Mob skeleton = archer(helper, shooter, 24, 24);
		double[] farthest = {0.0};
		helper.onEachTick(() -> farthest[0] = Math.max(farthest[0], skeleton.distanceTo(shooter)));
		// The same window the rusher case is given, so a retreat that only needs time would show here too.
		helper.runAfterDelay(300L, () -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherStaysPutForAPlayerWhoNeverRushes RESULT farthest={}", farthest[0]);
			helper.assertTrue(farthest[0] < 9.0, "the archer backed off to " + farthest[0] + " from a player who never rushed");
			helper.succeed();
		});
	}
}
