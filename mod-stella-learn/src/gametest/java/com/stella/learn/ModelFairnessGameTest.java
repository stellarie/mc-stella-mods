package com.stella.learn;

import com.stella.learn.mixin.MoveControlAccessor;
import com.stella.learn.model.ActionSource;
import com.stella.learn.model.ArcherAction;
import com.stella.learn.model.Contract;
import com.stella.learn.model.LearnedArcherGoal;
import com.stella.learn.model.Mlp;
import com.stella.learn.model.MlpSource;
import com.stella.learn.model.ModelDriver;
import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * Whatever a model asks for, the archer turns, moves, draws and looses inside the limits the plan sets. Each test books
 * one per-tick callback while it sets up, since booking one later crashes the game test runner.
 */
public class ModelFairnessGameTest {
	private static final float TURN_LIMIT = LearnedArcherGoal.MAX_TURN_DEGREES;
	private static final int TURN_TICKS = 20;
	private static final int SPEED_SETTLE_TICKS = 30;
	private static final int SPEED_TICKS = 20;
	private static final int STRAFE_SETTLE_TICKS = 10;
	private static final int STRAFE_TICKS = 20;
	private static final double SPEED_TOLERANCE = 1.05;
	private static final int DECISION_TICKS = 2;
	private static final int DRAW_DECISIONS = 10;
	private static final float AIM_CLOSE_BLOCKS = 2.0F;

	/** A target far to the side, so the archer runs its goal without any of the test's numbers depending on it. */
	private static ServerPlayer farPlayer(final GameTestHelper helper) {
		return LearnArena.player(helper, 44.5, 44.5);
	}

	private static double horizontal(final Vec3 to, final Vec3 from) {
		return Math.hypot(to.x - from.x, to.z - from.z);
	}

	/** An archer that starts facing away from the target turns to it at 30 degrees a tick and no faster, then stays on it. */
	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void turnRateIsCapped(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		archer.setYRot(140.0F);
		archer.setYHeadRot(140.0F);
		archer.setYBodyRot(140.0F);
		ModelDriver.setActionSource(archer, ModelTests.script(decision -> ModelTests.bow(ArcherAction.Bow.IDLE)));
		List<Float> yaws = new ArrayList<>();
		boolean[] done = {false};
		ModelTests.everyTick(helper, player, () -> {
			if (done[0] || !ModelTests.running(archer)) {
				return;
			}

			yaws.add(archer.getYRot());
			if (yaws.size() == TURN_TICKS + 1) {
				done[0] = true;
				float largest = 0.0F;
				int atCap = 0;
				for (int i = 1; i < yaws.size(); i++) {
					float turned = Math.abs(Mth.wrapDegrees(yaws.get(i) - yaws.get(i - 1)));
					largest = Math.max(largest, turned);
					if (turned >= TURN_LIMIT - 1.0F) {
						atCap++;
					}
				}

				Vec3 toward = player.getEyePosition().subtract(archer.getEyePosition());
				float wanted = (float) (Mth.atan2(-toward.x, toward.z) * 180.0 / Math.PI);
				float off = Math.abs(Mth.wrapDegrees(yaws.get(yaws.size() - 1) - wanted));
				StellaLearn.LOGGER.info("[BEHAVIOR] test=turnRateIsCapped RESULT ticks={} largest={} ticks_at_cap={} final_off={} limit={}", TURN_TICKS, largest, atCap, off, TURN_LIMIT);
				helper.assertTrue(largest <= TURN_LIMIT + 0.01F, "the archer turned " + largest + " degrees in one tick");
				helper.assertTrue(atCap >= 3, "the archer reached the cap in only " + atCap + " ticks");
				helper.assertTrue(off < 5.0F, "the archer ended " + off + " degrees off the target");
				ModelTests.pass(helper);
			}
		}, archer);
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void speedIsCapped(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = farPlayer(helper);
		Mob archer = ModelTests.skeleton(helper, EntityTypes.SKELETON, 14.5, 14.5, true);
		// Clear of the test box's barrier walls near the origin, which would stop a walk.
		// The control walks under the same move control call with the plan's 1.2 modifier and no model in the way.
		Mob control = ModelTests.skeleton(helper, EntityTypes.SKELETON, 22.5, 14.5, false);
		control.addTag(StellaMobs.NO_BEHAVIOR_TAG);
		control.getGoalSelector().removeAllGoals(goal -> true);
		int[] running = {0};
		Vec3[] archerFrom = {null};
		Vec3[] controlFrom = {null};
		ModelTests.everyTick(helper, player, () -> {
			control.getMoveControl().strafe(1.0F, 0.0F);
			((MoveControlAccessor) control.getMoveControl()).stella$setSpeedModifier(LearnedArcherGoal.SPEED_MODIFIER);
			if (!ModelTests.running(archer) || running[0] > SPEED_SETTLE_TICKS + SPEED_TICKS) {
				return;
			}

			running[0]++;
			if (running[0] == SPEED_SETTLE_TICKS) {
				archerFrom[0] = archer.position();
				controlFrom[0] = control.position();
			} else if (running[0] == SPEED_SETTLE_TICKS + SPEED_TICKS) {
				double model = horizontal(archer.position(), archerFrom[0]) / SPEED_TICKS;
				double reference = horizontal(control.position(), controlFrom[0]) / SPEED_TICKS;
				double attribute = archer.getAttributeValue(Attributes.MOVEMENT_SPEED);
				StellaLearn.LOGGER.info(
					"[BEHAVIOR] test=speedIsCapped RESULT model_blocks_per_tick={} control_blocks_per_tick={} ratio={} attribute={} attribute_times_1_2={}",
					model, reference, model / reference, attribute, attribute * LearnedArcherGoal.SPEED_MODIFIER
				);
				helper.assertTrue(reference > 0.05, "the control did not walk: " + reference);
				helper.assertTrue(model <= reference * SPEED_TOLERANCE, "the model archer walked " + model + " blocks a tick against " + reference);
				helper.assertTrue(model >= reference * 0.8, "the model archer walked only " + model + " against " + reference + ", so the limit was not reached");
				ModelTests.pass(helper);
			}
		}, archer);
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void strafeRightMovesToTheMobsRight(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		// The player stands straight ahead, so the mob keeps its facing while it strafes.
		ServerPlayer player = LearnArena.player(helper, 30.5, 44.5);
		Mob archer = ModelTests.skeleton(helper, EntityTypes.SKELETON, 30.5, 14.5, true);
		ModelDriver.setActionSource(archer, ModelTests.script(decision -> new ArcherAction(0.0F, 1.0F, 0.0F, 0.0F, false, ArcherAction.Bow.IDLE)));
		int[] running = {0};
		Vec3[] from = {null};
		ModelTests.everyTick(helper, player, () -> {
			if (!ModelTests.running(archer) || running[0] > STRAFE_SETTLE_TICKS + STRAFE_TICKS) {
				return;
			}

			running[0]++;
			if (running[0] == STRAFE_SETTLE_TICKS) {
				from[0] = archer.position();
			} else if (running[0] == STRAFE_SETTLE_TICKS + STRAFE_TICKS) {
				Vec3 moved = archer.position().subtract(from[0]);
				StellaLearn.LOGGER.info("[BEHAVIOR] test=strafeRightMovesToTheMobsRight RESULT yaw={} dx={} dz={}", archer.getYRot(), moved.x, moved.z);
				// At yaw 0 the mob faces +z, so its right hand is -x.
				helper.assertTrue(moved.x < -2.0, "a right strafe moved the mob " + moved.x + " along x");
				helper.assertTrue(Math.abs(moved.z) < 0.5 * Math.abs(moved.x), "a right strafe moved the mob " + moved.z + " along z");
				ModelTests.pass(helper);
			}
		}, archer);
	}

	/** Asks for a volley at every decision; one leaves only when the cooldown is ready. */
	private static ActionSource drawThenVolley() {
		return ModelTests.script(decision -> ModelTests.bow(ArcherAction.Bow.FIRE_VOLLEY));
	}

	private static int volleysBy(final Mob mob, final int untilTick) {
		return (int) ModelTests.lines(mob, "RELEASE", "volley=true").stream().filter(line -> Integer.parseInt(ModelTests.field(line, "tick")) <= untilTick).count();
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void volleyWaitsForCooldown(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		LearnArena.keepTargeting(helper, archer, player);
		ModelDriver.setActionSource(archer, drawThenVolley());
		int[] start = {-1};
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, () -> ModelTests.running(archer), 60, () -> {
			start[0] = archer.tickCount;
			LearnArena.pollUntil(helper, () -> archer.tickCount >= start[0] + 262, 300, () -> {
				int early = volleysBy(archer, start[0] + 130);
				int late = volleysBy(archer, start[0] + 260);
				long releases = ModelTests.lines(archer, "RELEASE").size();
				StellaLearn.LOGGER.info("[BEHAVIOR] test=volleyWaitsForCooldown RESULT volleys_by_130={} volleys_by_260={} releases={}", early, late, releases);
				helper.assertTrue(releases >= 8, "the archer loosed only " + releases + " times, so the test asked for too few volleys");
				helper.assertTrue(early == 1, "the archer loosed " + early + " volleys in 130 ticks");
				helper.assertTrue(late == 2, "the archer loosed " + late + " volleys in 260 ticks");
				ModelTests.pass(helper);
			}, () -> "the clock never reached 260 ticks");
		}, () -> "the archer never started its model goal"));
	}

	private static void firstSpeed(final GameTestHelper helper, final Mob archer, final List<AbstractArrow> seen, final double[] speed) {
		ModelTests.collectArrows(helper, archer, seen);
		if (speed[0] == 0.0 && !seen.isEmpty()) {
			speed[0] = seen.get(0).getDeltaMovement().length();
		}
	}

	private static Mob firstShot(final GameTestHelper helper, final ServerPlayer player, final ActionSource source, final List<AbstractArrow> arrows, final double[] speed, final double x) {
		Mob archer = ModelTests.archer(helper, x, 24.5);
		ModelDriver.setActionSource(archer, source);
		ModelTests.everyTick(helper, player, () -> firstSpeed(helper, archer, arrows, speed), archer);
		return archer;
	}

	/** Asks to fire at every decision: the first arrow leaves at a full draw and at full speed. */
	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void fireWaitsForFullDraw(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 40.5);
		double[] speed = {0.0};
		Mob firing = firstShot(helper, player, ModelTests.script(decision -> ModelTests.bow(ArcherAction.Bow.FIRE)), new ArrayList<>(), speed, 24.5);
		LearnArena.whenReady(helper, List.of(player, firing), () -> LearnArena.pollUntil(helper, () -> speed[0] > 0.0, 200, () -> {
			int draw = Integer.parseInt(ModelTests.field(ModelTests.lines(firing, "RELEASE").get(0), "draw_ticks"));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=fireWaitsForFullDraw RESULT draw_ticks={} speed={}", draw, speed[0]);
			helper.assertTrue(draw >= LearnedArcherGoal.FULL_DRAW_TICKS, "the first arrow left after " + draw + " draw ticks");
			helper.assertTrue(speed[0] >= LearnedArcherGoal.POWER * 0.95, "the first arrow flew at " + speed[0] + ", not at full speed");
			ModelTests.pass(helper);
		}, () -> "the firing archer never loosed"));
	}

	/** One fire decision, then idle and fire in turn: no two idles in a row, so the shot still leaves at a full draw. */
	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void fireLatchesThroughIdle(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 40.5);
		double[] speed = {0.0};
		Mob archer = firstShot(helper, player, ModelTests.script(decision -> ModelTests.bow(decision % 2 == 0 ? ArcherAction.Bow.FIRE : ArcherAction.Bow.IDLE)), new ArrayList<>(), speed, 24.5);
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, () -> speed[0] > 0.0, 200, () -> {
			int draw = Integer.parseInt(ModelTests.field(ModelTests.lines(archer, "RELEASE").get(0), "draw_ticks"));
			helper.assertTrue(draw >= LearnedArcherGoal.FULL_DRAW_TICKS && speed[0] >= LearnedArcherGoal.POWER * 0.95, "draw " + draw + " speed " + speed[0]);
			helper.assertTrue(!ModelTests.lines(archer, "FIRE_LATCH").isEmpty() && ModelTests.lines(archer, "CANCEL").isEmpty(), "no latch line, or a cancel line");
			ModelTests.pass(helper);
		}, () -> "the archer never loosed through its idle decisions"));
	}

	/** A fire decision, then idle decisions from then on: two in a row cancel the committed draw and nothing leaves. */
	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void doubleIdleCancels(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 40.5);
		double[] speed = {0.0};
		Mob archer = firstShot(helper, player, ModelTests.script(decision -> ModelTests.bow(decision == 0 ? ArcherAction.Bow.FIRE : ArcherAction.Bow.IDLE)), new ArrayList<>(), speed, 24.5);
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, () -> !ModelTests.lines(archer, "CANCEL").isEmpty(), 100, () -> LearnArena.pollUntil(helper, () -> archer.tickCount > 90, 200, () -> {
			helper.assertTrue(speed[0] == 0.0 && ModelTests.lines(archer, "RELEASE").isEmpty(), "a cancelled draw still loosed an arrow");
			helper.assertTrue(!ModelTests.lines(archer, "FIRE_LATCH").isEmpty(), "the fire decision never latched");
			ModelTests.pass(helper);
		}, () -> "the clock never passed 90 ticks"), () -> "no CANCEL line after two idle decisions"));
	}

	/** Zero offsets and fire at every decision: a still target at 10, 16 and 20 blocks is hit by at least 40 percent of the arrows. */
	@GameTest(maxTicks = 900, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void zeroOffsetHitsAStillTarget(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		double[] ranges = {10.0, 16.0, 20.0};
		ServerPlayer player = LearnArena.player(helper, 24.5, 44.5);
		List<Mob> archers = new ArrayList<>();
		List<List<AbstractArrow>> seen = new ArrayList<>();
		for (double range : ranges) {
			Mob archer = ModelTests.archer(helper, 24.5 + (archers.size() - 1) * 3.0, 44.5 - range);
			archer.setYRot(140.0F);
			archer.setYHeadRot(140.0F);
			archer.setYBodyRot(140.0F);
			ModelDriver.setActionSource(archer, ModelTests.script(decision -> ModelTests.bow(ArcherAction.Bow.FIRE)));
			archers.add(archer);
			seen.add(new ArrayList<>());
		}

		int[] hits = new int[ranges.length];
		List<Object[]> flying = new ArrayList<>();
		ModelTests.everyTick(helper, player, () -> {
			for (int i = 0; i < archers.size(); i++) {
				int before = seen.get(i).size();
				ModelTests.collectArrows(helper, archers.get(i), seen.get(i));
				for (int k = before; k < seen.get(i).size(); k++) {
					flying.add(new Object[] {i, seen.get(i).get(k), new double[] {Double.MAX_VALUE}});
				}
			}

			for (Object[] one : new ArrayList<>(flying)) {
				AbstractArrow arrow = (AbstractArrow) one[1];
				double[] closest = (double[]) one[2];
				closest[0] = Math.min(closest[0], Math.sqrt(player.getBoundingBox().distanceToSqr(arrow.position())));
				if (arrow.isRemoved() || arrow.getDeltaMovement().lengthSqr() < 1.0E-4) {
					flying.remove(one);
					if (closest[0] < 0.3) {
						hits[(int) one[0]]++;
					}
				}
			}
		}, archers.toArray(new Mob[0]));
		List<net.minecraft.world.entity.Entity> watched = new ArrayList<>(archers);
		watched.add(player);
		LearnArena.whenReady(helper, watched, () -> helper.runAfterDelay(600L, () -> {
			List<String> problems = new ArrayList<>();
			for (int i = 0; i < ranges.length; i++) {
				int shots = seen.get(i).size();
				StellaLearn.LOGGER.info("[BEHAVIOR] test=zeroOffsetHitsAStillTarget RESULT range={} shots={} hits={}", ranges[i], shots, hits[i]);
				if (shots < 5 || hits[i] * 100 < shots * 40) {
					problems.add("range " + ranges[i] + ": " + hits[i] + " of " + shots);
				}
			}

			helper.assertTrue(problems.isEmpty(), "too few hits with zero offsets: " + problems);
			ModelTests.pass(helper);
		}));
	}

	private static float arrowYaw(final AbstractArrow arrow) {
		Vec3 v = arrow.getDeltaMovement();
		return (float) (Mth.atan2(-v.x, v.z) * 180.0 / Math.PI);
	}

	/** A yaw output of +1 or -1 turns the arrow 30 degrees to the right or left of the line to the target. */
	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void offsetShiftsAim(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 40.5);
		Mob right = ModelTests.archer(helper, 14.5, 24.5);
		Mob left = ModelTests.archer(helper, 34.5, 24.5);
		ModelDriver.setActionSource(right, ModelTests.script(decision -> new ArcherAction(0.0F, 0.0F, 1.0F, 0.0F, false, ArcherAction.Bow.FIRE)));
		ModelDriver.setActionSource(left, ModelTests.script(decision -> new ArcherAction(0.0F, 0.0F, -1.0F, 0.0F, false, ArcherAction.Bow.FIRE)));
		float[] rightYaw = {Float.NaN};
		float[] leftYaw = {Float.NaN};
		List<AbstractArrow> rightSeen = new ArrayList<>();
		List<AbstractArrow> leftSeen = new ArrayList<>();
		ModelTests.everyTick(helper, player, () -> {
			ModelTests.collectArrows(helper, right, rightSeen);
			ModelTests.collectArrows(helper, left, leftSeen);
			if (Float.isNaN(rightYaw[0]) && !rightSeen.isEmpty()) {
				rightYaw[0] = arrowYaw(rightSeen.get(0));
			}

			if (Float.isNaN(leftYaw[0]) && !leftSeen.isEmpty()) {
				leftYaw[0] = arrowYaw(leftSeen.get(0));
			}
		}, right, left);
		LearnArena.whenReady(helper, List.of(player, right, left), () -> LearnArena.pollUntil(helper, () -> !Float.isNaN(rightYaw[0]) && !Float.isNaN(leftYaw[0]), 200, () -> {
			Vec3 fromRight = player.getEyePosition().subtract(right.getEyePosition());
			Vec3 fromLeft = player.getEyePosition().subtract(left.getEyePosition());
			float rightBy = Mth.wrapDegrees(rightYaw[0] - (float) (Mth.atan2(-fromRight.x, fromRight.z) * 180.0 / Math.PI));
			float leftBy = Mth.wrapDegrees(leftYaw[0] - (float) (Mth.atan2(-fromLeft.x, fromLeft.z) * 180.0 / Math.PI));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=offsetShiftsAim RESULT right_by={} left_by={}", rightBy, leftBy);
			helper.assertTrue(Math.abs(rightBy - 30.0F) < 6.0F, "the +1 archer aimed " + rightBy + " degrees off the line, not 30");
			helper.assertTrue(Math.abs(leftBy + 30.0F) < 6.0F, "the -1 archer aimed " + leftBy + " degrees off the line, not -30");
			ModelTests.pass(helper);
		}, () -> "an archer never loosed"));
	}
}
