package com.stella.learn;

import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** A profile counts what a player does, forgets it on a half life, and keeps it across a save. */
public class ProfileGameTest {
	private static final long HALF_LIFE = LearnConfig.DEFAULT_PROFILE_HALF_LIFE_TICKS;
	private static final double TOLERANCE = 1.0e-9;

	@GameTest
	public void aRecordedHabitCounts(final GameTestHelper helper) {
		PlayerProfile profile = PlayerProfile.EMPTY.record(Habit.BLOCK, 1.0, 0L, HALF_LIFE).record(Habit.BLOCK, 1.0, 0L, HALF_LIFE);
		double value = profile.value(Habit.BLOCK, 0L, HALF_LIFE);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aRecordedHabitCounts RESULT block={}", value);
		helper.assertTrue(Math.abs(value - 2.0) < TOLERANCE, "two blocks counted " + value);
		helper.succeed();
	}

	@GameTest
	public void aHabitIsForgottenOnItsHalfLife(final GameTestHelper helper) {
		PlayerProfile profile = PlayerProfile.EMPTY.record(Habit.MELEE, 4.0, 0L, HALF_LIFE);
		double half = profile.value(Habit.MELEE, HALF_LIFE, HALF_LIFE);
		double quarter = profile.value(Habit.MELEE, HALF_LIFE * 2L, HALF_LIFE);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aHabitIsForgottenOnItsHalfLife RESULT half={} quarter={}", half, quarter);
		helper.assertTrue(Math.abs(half - 2.0) < TOLERANCE, "one half life left " + half);
		helper.assertTrue(Math.abs(quarter - 1.0) < TOLERANCE, "two half lives left " + quarter);
		helper.succeed();
	}

	@GameTest
	public void aNewRecordDecaysTheOldOnesFirst(final GameTestHelper helper) {
		PlayerProfile profile = PlayerProfile.EMPTY.record(Habit.RANGED, 2.0, 0L, HALF_LIFE).record(Habit.RANGED, 1.0, HALF_LIFE, HALF_LIFE);
		double value = profile.value(Habit.RANGED, HALF_LIFE, HALF_LIFE);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aNewRecordDecaysTheOldOnesFirst RESULT ranged={}", value);
		helper.assertTrue(Math.abs(value - 2.0) < TOLERANCE, "1 fresh plus half of 2 gave " + value);
		helper.succeed();
	}

	@GameTest
	public void habitsAreCountedApart(final GameTestHelper helper) {
		PlayerProfile profile = PlayerProfile.EMPTY.record(Habit.BLOCK, 3.0, 0L, HALF_LIFE);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=habitsAreCountedApart RESULT melee={}", profile.value(Habit.MELEE, 0L, HALF_LIFE));
		helper.assertTrue(profile.value(Habit.MELEE, 0L, HALF_LIFE) == 0.0, "blocking counted as melee");
		helper.succeed();
	}

	@GameTest
	public void aTimeBeforeTheStampDoesNotGrowAHabit(final GameTestHelper helper) {
		PlayerProfile profile = PlayerProfile.EMPTY.record(Habit.BLOCK, 5.0, HALF_LIFE, HALF_LIFE);
		double value = profile.value(Habit.BLOCK, 0L, HALF_LIFE);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aTimeBeforeTheStampDoesNotGrowAHabit RESULT block={}", value);
		helper.assertTrue(Math.abs(value - 5.0) < TOLERANCE, "a clock that ran backward gave " + value);
		helper.succeed();
	}

	@GameTest
	public void aProfileSurvivesASave(final GameTestHelper helper) {
		PlayerProfile profile = PlayerProfile.EMPTY.record(Habit.BLOCK, 3.0, 40L, HALF_LIFE).record(Habit.RANGED, 1.5, 90L, HALF_LIFE);
		Tag saved = PlayerProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).getOrThrow();
		DataResult<PlayerProfile> loaded = PlayerProfile.CODEC.parse(NbtOps.INSTANCE, saved);
		PlayerProfile back = loaded.getOrThrow();
		StellaLearn.LOGGER.info(
			"[BEHAVIOR] test=aProfileSurvivesASave RESULT block={} ranged={}", back.value(Habit.BLOCK, 40L, HALF_LIFE), back.value(Habit.RANGED, 90L, HALF_LIFE)
		);
		helper.assertTrue(Math.abs(back.value(Habit.BLOCK, 40L, HALF_LIFE) - 3.0) < TOLERANCE, "block was lost in the save");
		helper.assertTrue(Math.abs(back.value(Habit.RANGED, 90L, HALF_LIFE) - 1.5) < TOLERANCE, "ranged was lost in the save");
		helper.succeed();
	}

	@GameTest
	public void theProfileIsSavedWithThePlayer(final GameTestHelper helper) {
		boolean persistent = Profiles.TYPE.isPersistent();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theProfileIsSavedWithThePlayer RESULT persistent={}", persistent);
		helper.assertTrue(persistent, "the profile attachment is not persistent");
		helper.succeed();
	}

	@GameTest
	public void theClockAdvancesWhenTold(final GameTestHelper helper) {
		long before = LearnClock.now();
		LearnClock.advance(HALF_LIFE);
		long after = LearnClock.now();
		LearnClock.reset();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theClockAdvancesWhenTold RESULT advanced={}", after - before);
		helper.assertTrue(after - before == HALF_LIFE, "the clock advanced " + (after - before));
		helper.succeed();
	}

	@GameTest
	public void aPlayerDecaysWithTheClock(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Profiles.record(player, Habit.BLOCK);
		double fresh = Profiles.value(player, Habit.BLOCK);
		LearnClock.advance(LearnConfigStore.get().profileHalfLifeTicks());
		double later = Profiles.value(player, Habit.BLOCK);
		LearnClock.reset();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aPlayerDecaysWithTheClock RESULT fresh={} later={}", fresh, later);
		helper.assertTrue(Math.abs(fresh - 1.0) < TOLERANCE && Math.abs(later - 0.5) < TOLERANCE, "fresh " + fresh + " later " + later);
		helper.succeed();
	}
}
