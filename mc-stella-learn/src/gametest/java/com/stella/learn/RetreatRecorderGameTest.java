package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** A player who moves straight away from a monster that is after them is counted as falling back. */
public class RetreatRecorderGameTest {
	private static final int LONG = 400;

	private static Mob hunter(final GameTestHelper helper, final ServerPlayer player, final int x, final int z) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
		zombie.setNoAi(true);
		zombie.setTarget(player);
		return zombie;
	}

	private static Vec3 shift(final ServerPlayer player, final double dx, final double dz) {
		Vec3 before = player.position();
		player.setPos(before.x + dx, before.y, before.z + dz);
		return before;
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void movingStraightAwayFromAHunterIsARetreat(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob zombie = hunter(helper, player, 24, 30);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			zombie.setTarget(player);
			Vec3 previous = shift(player, 0.0, -2.0);
			boolean counted = RetreatRecorder.sampleFrom(player, previous);
			double retreat = Profiles.value(player, Habit.RETREAT);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=movingStraightAwayFromAHunterIsARetreat RESULT counted={} retreat={}", counted, retreat);
			helper.assertTrue(counted && retreat > 0.0, "counted=" + counted + " retreat=" + retreat);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void movingTowardAHunterIsNotARetreat(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob zombie = hunter(helper, player, 24, 30);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			zombie.setTarget(player);
			Vec3 previous = shift(player, 0.0, 2.0);
			boolean counted = RetreatRecorder.sampleFrom(player, previous);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=movingTowardAHunterIsNotARetreat RESULT counted={}", counted);
			helper.assertTrue(!counted && Profiles.value(player, Habit.RETREAT) == 0.0, "a step toward the hunter counted");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void movingSidewaysIsNotARetreat(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob zombie = hunter(helper, player, 24, 30);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			zombie.setTarget(player);
			Vec3 previous = shift(player, 2.0, 0.0);
			boolean counted = RetreatRecorder.sampleFrom(player, previous);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=movingSidewaysIsNotARetreat RESULT counted={}", counted);
			helper.assertTrue(!counted, "a sideways step counted");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void walkingAwayFromNobodyIsNotARetreat(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob idle = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 30).spawn();
		idle.setNoAi(true);
		LearnArena.whenReady(helper, List.of(player, idle), () -> {
			Vec3 previous = shift(player, 0.0, -2.0);
			boolean counted = RetreatRecorder.sampleFrom(player, previous);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=walkingAwayFromNobodyIsNotARetreat RESULT counted={}", counted);
			helper.assertTrue(!counted, "walking away from a monster that was not after the player counted");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aHunterBeyondRangeDoesNotCount(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 12.5);
		Mob zombie = hunter(helper, player, 24, 40);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			zombie.setTarget(player);
			Vec3 previous = shift(player, 0.0, -2.0);
			boolean counted = RetreatRecorder.sampleFrom(player, previous);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aHunterBeyondRangeDoesNotCount RESULT counted={} distance={}", counted, zombie.distanceTo(player));
			helper.assertTrue(zombie.distanceTo(player) > RetreatRecorder.RANGE, "the hunter was in range, so the case proves nothing");
			helper.assertTrue(!counted, "a hunter beyond range counted");
			helper.succeed();
		});
	}
}
