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

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void turnRateIsCapped(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = farPlayer(helper);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		ModelDriver.setActionSource(archer, new MlpSource(new Mlp(ModelTests.policy("turn-right"))));
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
				float smallest = Float.MAX_VALUE;
				for (int i = 1; i < yaws.size(); i++) {
					float turned = Mth.wrapDegrees(yaws.get(i) - yaws.get(i - 1));
					largest = Math.max(largest, Math.abs(turned));
					smallest = Math.min(smallest, turned);
				}

				StellaLearn.LOGGER.info("[BEHAVIOR] test=turnRateIsCapped RESULT ticks={} largest={} smallest={} limit={}", TURN_TICKS, largest, smallest, TURN_LIMIT);
				helper.assertTrue(largest <= TURN_LIMIT + 0.01F, "the archer turned " + largest + " degrees in one tick");
				helper.assertTrue(smallest >= TURN_LIMIT - 1.0F, "the archer turned right by only " + smallest + " degrees in a tick, so the cap was not reached");
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
		ServerPlayer player = farPlayer(helper);
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
				helper.assertTrue(Math.abs(moved.z) < 0.5, "a right strafe moved the mob " + moved.z + " along z");
				ModelTests.pass(helper);
			}
		}, archer);
	}

	/** Draws for `DRAW_DECISIONS` decisions, then asks for a volley, over and over. */
	private static ActionSource drawThenVolley() {
		return ModelTests.script(decision -> ModelTests.bow(decision % (DRAW_DECISIONS + 1) == DRAW_DECISIONS ? ArcherAction.Bow.VOLLEY : ArcherAction.Bow.DRAW));
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

	/** Draws for `draws` decisions, releases once, then rests. */
	private static ActionSource drawThenRelease(final int draws) {
		return ModelTests.script(decision -> ModelTests.bow(decision < draws ? ArcherAction.Bow.DRAW : decision == draws ? ArcherAction.Bow.RELEASE : ArcherAction.Bow.IDLE));
	}

	private static void firstSpeed(final GameTestHelper helper, final Mob archer, final List<AbstractArrow> seen, final double[] speed) {
		ModelTests.collectArrows(helper, archer, seen);
		if (speed[0] == 0.0 && !seen.isEmpty()) {
			speed[0] = seen.get(0).getDeltaMovement().length();
		}
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void earlyReleaseIsWeak(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 40.5);
		Mob quick = ModelTests.archer(helper, 12.5, 24.5);
		Mob full = ModelTests.archer(helper, 36.5, 24.5);
		ModelDriver.setActionSource(quick, drawThenRelease(3));
		ModelDriver.setActionSource(full, drawThenRelease(DRAW_DECISIONS));
		List<AbstractArrow> quickArrows = new ArrayList<>();
		List<AbstractArrow> fullArrows = new ArrayList<>();
		double[] quickSpeed = {0.0};
		double[] fullSpeed = {0.0};
		ModelTests.everyTick(helper, player, () -> {
			firstSpeed(helper, quick, quickArrows, quickSpeed);
			firstSpeed(helper, full, fullArrows, fullSpeed);
		}, quick, full);
		LearnArena.whenReady(helper, List.of(player, quick, full), () -> LearnArena.pollUntil(helper, () -> quickSpeed[0] > 0.0 && fullSpeed[0] > 0.0, 200, () -> {
			String early = ModelTests.lines(quick, "RELEASE").get(0);
			String late = ModelTests.lines(full, "RELEASE").get(0);
			int earlyDraw = Integer.parseInt(ModelTests.field(early, "draw_ticks"));
			int lateDraw = Integer.parseInt(ModelTests.field(late, "draw_ticks"));
			StellaLearn.LOGGER.info(
				"[BEHAVIOR] test=earlyReleaseIsWeak RESULT early_draw={} early_speed={} late_draw={} late_speed={}", earlyDraw, quickSpeed[0], lateDraw, fullSpeed[0]
			);
			helper.assertTrue(earlyDraw >= LearnedArcherGoal.MIN_DRAW_TICKS && earlyDraw < lateDraw, "the draws were " + earlyDraw + " and " + lateDraw);
			helper.assertTrue(quickSpeed[0] < fullSpeed[0], "the early arrow flew at " + quickSpeed[0] + ", the late one at " + fullSpeed[0]);
			ModelTests.pass(helper);
		}, () -> "an archer never loosed: quick=" + quickSpeed[0] + " full=" + fullSpeed[0]));
	}

	/** Looks straight at the target's eyes and draws, then releases once; it reads its own pitch from the observation. */
	private static ActionSource aimThenRelease(final ServerPlayer player, final int draws) {
		int pitchAt = Contract.archer().indexOf("self.pitch");
		float pitchScale = Contract.archer().scale(pitchAt);
		return new ActionSource() {
			private int decisions;

			@Override
			public ArcherAction decide(final Mob mob, final float[] observation) {
				Vec3 toward = player.getEyePosition().subtract(mob.getEyePosition());
				float wantYaw = (float) (Mth.atan2(-toward.x, toward.z) * 180.0 / Math.PI);
				float wantPitch = (float) (-Mth.atan2(toward.y, Math.hypot(toward.x, toward.z)) * 180.0 / Math.PI);
				float pitch = observation[pitchAt] * pitchScale;
				float yaw = Mth.clamp(Mth.wrapDegrees(wantYaw - mob.getYRot()) / (TURN_LIMIT * DECISION_TICKS), -1.0F, 1.0F);
				float down = Mth.clamp((wantPitch - pitch) / (TURN_LIMIT * DECISION_TICKS), -1.0F, 1.0F);
				int decision = this.decisions++;
				ArcherAction.Bow bow = decision < draws ? ArcherAction.Bow.DRAW : decision == draws ? ArcherAction.Bow.RELEASE : ArcherAction.Bow.IDLE;
				return new ArcherAction(0.0F, 0.0F, yaw, down, false, bow);
			}
		};
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void arrowFollowsTheLook(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		// The archer starts facing away so the aim has to turn it.
		archer.setYRot(140.0F);
		archer.setYHeadRot(140.0F);
		archer.setYBodyRot(140.0F);
		ModelDriver.setActionSource(archer, aimThenRelease(player, DRAW_DECISIONS + 4));
		List<AbstractArrow> arrows = new ArrayList<>();
		ModelTests.everyTick(helper, player, () -> ModelTests.collectArrows(helper, archer, arrows), archer);
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, () -> !arrows.isEmpty() && (arrows.get(0).isRemoved() || arrows.get(0).getDeltaMovement().lengthSqr() < 1.0E-4), 300, () -> {
			AbstractArrow arrow = arrows.get(0);
			Vec3 centre = player.getBoundingBox().getCenter();
			double miss = arrow.position().distanceTo(centre);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=arrowFollowsTheLook RESULT arrows={} miss_blocks={} landed={} target={}", arrows.size(), miss, arrow.position(), centre);
			helper.assertTrue(arrows.size() == 1, "the archer loosed " + arrows.size() + " arrows");
			helper.assertTrue(miss <= AIM_CLOSE_BLOCKS, "the arrow landed " + miss + " blocks from a still target ten blocks away");
			ModelTests.pass(helper);
		}, () -> "the arrow never landed: " + arrows.size()));
	}
}
