package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Mobs change where they stand and how they approach for a player who strafes, charges, kites or jumps to crit. */
public class MovementTacticsGameTest {
	private static final int LONG = 900;

	static {
		Rally.register();
	}

	private static void fill(final ServerPlayer player, final Habit habit, final double times) {
		for (int i = 0; i < times; i++) {
			Profiles.record(player, habit);
		}
	}

	private static ServerPlayer facingSouth(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = LearnArena.player(helper, x, z);
		player.setYRot(0.0F);
		player.setYHeadRot(0.0F);
		return player;
	}

	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
		mob.setNoAi(true);
		return mob;
	}

	private static double sideOf(final Mob mob, final ServerPlayer player, final int index) {
		return Rally.slotFor(mob, player, index).x - player.getX();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theFirstFlankerTakesTheSideAStraferMovesToward(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer leftStrafer = facingSouth(helper, 24.5, 24.5);
		fill(leftStrafer, Habit.STRAFE_LEFT, LearnConfigStore.get().moveSaturation());
		ServerPlayer plain = facingSouth(helper, 10.5, 24.5);
		Mob mob = zombie(helper, 20, 30);
		LearnArena.whenReady(helper, List.of(leftStrafer, plain, mob), () -> {
			double strafer = sideOf(mob, leftStrafer, 0);
			double stranger = sideOf(mob, plain, 0);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theFirstFlankerTakesTheSideAStraferMovesToward RESULT strafer={} stranger={}", strafer, stranger);
			helper.assertTrue(stranger < 0.0, "the default first flanker was not on the right (west), so the case proves nothing: " + stranger);
			helper.assertTrue(strafer > 0.0, "the first flanker did not go to the strafer's left (east): " + strafer);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSecondFlankerTakesTheOtherSide(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper, 24.5, 24.5);
		fill(player, Habit.STRAFE_LEFT, LearnConfigStore.get().moveSaturation());
		Mob mob = zombie(helper, 20, 30);
		LearnArena.whenReady(helper, List.of(player, mob), () -> {
			double first = sideOf(mob, player, 0);
			double second = sideOf(mob, player, 1);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theSecondFlankerTakesTheOtherSide RESULT first={} second={}", first, second);
			helper.assertTrue(first * second < 0.0, "both flankers took the same side: " + first + " and " + second);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aChargerTightensTheFlankRing(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer charger = facingSouth(helper, 24.5, 24.5);
		fill(charger, Habit.CHARGE, LearnConfigStore.get().moveSaturation());
		ServerPlayer plain = facingSouth(helper, 10.5, 24.5);
		Mob mob = zombie(helper, 20, 30);
		LearnArena.whenReady(helper, List.of(charger, plain, mob), () -> {
			double tight = Math.abs(sideOf(mob, charger, 0));
			double wide = Math.abs(sideOf(mob, plain, 0));
			double want = Rally.MELEE_SLOT_DISTANCE * (1.0 - LearnConfigStore.get().chargeTighten());
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aChargerTightensTheFlankRing RESULT tight={} wide={} want={}", tight, wide, want);
			helper.assertTrue(Math.abs(wide - Rally.MELEE_SLOT_DISTANCE) < 0.01, "an ordinary player's ring was " + wide);
			helper.assertTrue(Math.abs(tight - want) < 0.05, "a charger's ring was " + tight + ", not " + want);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theInterceptPointIsAheadOfAMovingKiter(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer kiter = facingSouth(helper, 24.5, 24.5);
		fill(kiter, Habit.KITE, LearnConfigStore.get().moveSaturation());
		Vec3 velocity = new Vec3(0.15, 0.0, 0.0);
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) kiter).setAttached(MovementRecorder.VELOCITY, velocity);
		Mob mob = zombie(helper, 24, 38);
		LearnArena.whenReady(helper, List.of(kiter, mob), () -> {
			Vec3 point = Intercept.pointFor(mob, kiter);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theInterceptPointIsAheadOfAMovingKiter RESULT point={}", point);
			helper.assertTrue(point != null, "no intercept point for a moving kiter");
			Vec3 want = kiter.position().add(velocity.scale(LearnConfigStore.get().interceptLeadTicks()));
			helper.assertTrue(point.distanceTo(new Vec3(want.x, point.y, want.z)) < 0.01, "the point was " + point + ", not " + want);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void thereIsNothingToLeadForAPlayerWhoStandsStill(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer kiter = facingSouth(helper, 24.5, 24.5);
		fill(kiter, Habit.KITE, LearnConfigStore.get().moveSaturation());
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) kiter).setAttached(MovementRecorder.VELOCITY, new Vec3(Intercept.MINIMUM_SPEED / 2.0, 0.0, 0.0));
		Mob mob = zombie(helper, 24, 38);
		LearnArena.whenReady(helper, List.of(kiter, mob), () -> {
			helper.assertTrue(Intercept.pointFor(mob, kiter) == null, "a point was found to lead a player who barely moves");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void thereIsNothingToLeadForAPlayerWithNoMovementHabit(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper, 24.5, 24.5);
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MovementRecorder.VELOCITY, new Vec3(0.2, 0.0, 0.0));
		Mob mob = zombie(helper, 24, 38);
		LearnArena.whenReady(helper, List.of(player, mob), () -> {
			helper.assertTrue(Intercept.pointFor(mob, player) == null, "a point was found for a player with no habit");
			helper.succeed();
		});
	}

	/** Two zombies chase a kiter who runs east: the one with the goal aims ahead of the player, the one without does not. */
	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMeleeMobAimsAheadOfAMovingKiter(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer kiter = facingSouth(helper, 8.5, 12.5);
		fill(kiter, Habit.KITE, LearnConfigStore.get().moveSaturation());
		Mob leader = helper.spawnMob(EntityTypes.ZOMBIE, 14, LearnArena.SPAWN_Y, 30).spawn();
		Mob plain = helper.spawnMob(EntityTypes.ZOMBIE, 16, LearnArena.SPAWN_Y, 30).spawn();
		// The goals are installed as the mob loads, so the control has its intercept goal taken out afterwards.
		plain.getGoalSelector().removeAllGoals(goal -> goal instanceof InterceptGoal);
		LearnArena.keepTargeting(helper, leader, kiter);
		LearnArena.keepTargeting(helper, plain, kiter);
		double[] gap = {-1.0, -1.0};
		helper.onEachTick(() -> {
			if (helper.getTick() > 20L) {
				kiter.setPos(kiter.getX() + 0.15, kiter.getY(), kiter.getZ());
			}

			BlockPos aimed = leader.getNavigation().getTargetPos();
			BlockPos straight = plain.getNavigation().getTargetPos();
			if (gap[0] < 0.0 && helper.getTick() > 80L && aimed != null && straight != null) {
				// Positive is ahead of the player along their run east.
				gap[0] = aimed.getX() + 0.5 - kiter.getX();
				gap[1] = straight.getX() + 0.5 - kiter.getX();
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aMeleeMobAimsAheadOfAMovingKiter RESULT leaderGap={} plainGap={}", gap[0], gap[1]);
				helper.assertTrue(gap[1] < 0.5, "the plain zombie was aiming ahead of the player: " + gap[1]);
				helper.assertTrue(gap[0] > gap[1] + 0.5 * LearnConfigStore.get().interceptLeadTicks() * 0.15, "the leading zombie aimed " + gap[0] + " ahead of the player, the plain one " + gap[1]);
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobStepsBackWhenACritJumperLeavesTheGround(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer jumper = facingSouth(helper, 24.5, 24.5);
		fill(jumper, Habit.CRIT, LearnConfigStore.get().critSaturation());
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 27).spawn();
		LearnArena.keepTargeting(helper, mob, jumper);
		boolean[] airborne = {false};
		helper.onEachTick(() -> jumper.setOnGround(!airborne[0]));
		LearnArena.whenReady(helper, List.of(jumper, mob), () -> LearnArena.pollUntil(helper, () -> mob.distanceTo(jumper) < 2.5, 300, () -> {
			airborne[0] = true;
			LearnArena.pollUntil(helper, () -> mob.distanceTo(jumper) > 3.5, 100, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobStepsBackWhenACritJumperLeavesTheGround RESULT distance={}", mob.distanceTo(jumper));
				List<String> dodges = LearnTestInit.linesWith(mob.getUUID().toString(), "learnMove DODGE");
				helper.assertTrue(dodges.size() >= 1 && dodges.get(0).contains("crit_jumper=1.00"), "the dodge was not logged: " + dodges);
				airborne[0] = false;
				helper.succeed();
			}, () -> "the mob stayed at " + mob.distanceTo(jumper) + " while the player was in the air");
		}, () -> "the mob never closed in, so the case proves nothing"));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobStaysCloseWhenAPlayerWithNoCritHabitJumps(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper, 24.5, 24.5);
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 27).spawn();
		LearnArena.keepTargeting(helper, mob, player);
		boolean[] airborne = {false};
		double[] farthest = {0.0};
		helper.onEachTick(() -> {
			player.setOnGround(!airborne[0]);
			if (airborne[0]) {
				farthest[0] = Math.max(farthest[0], mob.distanceTo(player));
			}
		});
		LearnArena.whenReady(helper, List.of(player, mob), () -> LearnArena.pollUntil(helper, () -> mob.distanceTo(player) < 2.5, 300, () -> {
			airborne[0] = true;
			helper.runAfterDelay(60L, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobStaysCloseWhenAPlayerWithNoCritHabitJumps RESULT farthest={}", farthest[0]);
				helper.assertTrue(farthest[0] < 3.5, "the mob backed off to " + farthest[0] + " from a player with no crit habit");
				helper.succeed();
			});
		}, () -> "the mob never closed in"));
	}

	/** The lines a server log needs to show that mobs adapted to movement: the lead and the flank side. */
	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anInterceptAndAFlankAreLogged(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer kiter = facingSouth(helper, 8.5, 12.5);
		fill(kiter, Habit.KITE, LearnConfigStore.get().moveSaturation());
		fill(kiter, Habit.CRIT, LearnConfigStore.get().critSaturation());
		fill(kiter, Habit.STRAFE_LEFT, LearnConfigStore.get().moveSaturation());
		Mob leader = helper.spawnMob(EntityTypes.ZOMBIE, 14, LearnArena.SPAWN_Y, 30).spawn();
		LearnArena.keepTargeting(helper, leader, kiter);
		boolean[] airborne = {false};
		helper.onEachTick(() -> {
			if (helper.getTick() > 20L) {
				kiter.setPos(kiter.getX() + 0.15, kiter.getY(), kiter.getZ());
			}

			kiter.setOnGround(!airborne[0]);
		});
		String id = leader.getUUID().toString();
		LearnArena.whenReady(helper, List.of(kiter, leader), () -> LearnArena.pollUntil(
			helper, () -> !LearnTestInit.linesWith(id, "learnMove INTERCEPT").isEmpty(), 300, () -> {
				String intercept = LearnTestInit.linesWith(id, "learnMove INTERCEPT").get(0);
				helper.assertTrue(intercept.contains("lead_ticks=" + LearnConfigStore.get().interceptLeadTicks()) && intercept.contains("point="), "the intercept line reads " + intercept);
				Mob flanker = helper.spawnMob(EntityTypes.ZOMBIE, 14, LearnArena.SPAWN_Y, 34).spawn();
				flanker.setNoAi(true);
				LearnArena.whenReady(helper, List.of(flanker), () -> {
					Rally.slotFor(flanker, kiter, 0);
					CalloutBoard.send(leader, kiter, new Callout(Rally.TYPE, leader.getUUID(), kiter.getUUID(), 1, 100, null), LearnConfig.defaults().withCalloutRadius(24.0));
					List<String> slots = LearnTestInit.linesWith(flanker.getUUID().toString(), "learnRally SLOT");
					helper.assertTrue(slots.size() == 1 && slots.get(0).contains("strafe_bias=-1.00") && slots.get(0).contains("side=left"), "the slot lines were " + slots);
					StellaLearn.LOGGER.info("[BEHAVIOR] test=anInterceptAndAFlankAreLogged RESULT intercept={} slot={}", intercept, slots.get(0));
					helper.succeed();
				});
			}, () -> "no intercept line was logged"
		));
	}
}
