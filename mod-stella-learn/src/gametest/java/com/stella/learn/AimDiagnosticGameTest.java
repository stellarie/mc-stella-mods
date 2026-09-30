package com.stella.learn;

import com.stella.learn.gym.Bot;
import com.stella.learn.model.ActionSource;
import com.stella.learn.model.ArcherAction;
import com.stella.learn.model.ModelDriver;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * A driven archer with perfect aim and a full draw, against a player who stands still ten blocks away. It shows whether
 * the release path of the gym, which is the runtime's own, lands its arrows when the decisions are good.
 */
public class AimDiagnosticGameTest {
	private static final int TICKS = 500;
	private static final int FULL_DRAW = 20;
	private static final int PITCH_INDEX = 8;
	private static final int DRAW_INDEX = 9;
	private static final double SCALE_PITCH = 90.0;
	private static final double SCALE_DRAW = 20.0;

	@GameTest(maxTicks = 700, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void perfectAimHitsAStillTarget(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		ModelDriver.setActionSource(archer, aimAt(player));
		LearnArena.keepTargeting(helper, archer, player);
		List<AbstractArrow> seen = new ArrayList<>();
		float[] health = {player.getHealth()};
		int[] hits = {0};
		helper.onEachTick(() -> {
			ModelTests.collectArrows(helper, archer, seen);
			if (player.getHealth() < health[0]) {
				hits[0]++;
			}

			health[0] = player.getHealth();
		});
		LearnArena.whenReady(helper, List.of(player, archer), () -> helper.runAfterDelay(TICKS, () -> {
			int shots = seen.size();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=perfectAimHitsAStillTarget RESULT shots={} hits={} distance={}", shots, hits[0], LearnLog.num(archer.distanceTo(player)));
			helper.assertTrue(shots >= 5, "the archer loosed only " + shots + " arrows");
			helper.assertTrue(hits[0] * 2 >= shots, "only " + hits[0] + " of " + shots + " arrows hit a player who stood still");
			ModelTests.pass(helper);
		}));
	}

	private static ActionSource aimAt(final ServerPlayer player) {
		return (mob, observation) -> {
			Vec3 eye = mob.getEyePosition();
			Vec3 aim = player.getBoundingBox().getCenter();
			float yaw = Mth.wrapDegrees(Bot.yawTo(eye, aim) - mob.getYRot());
			double pitchNow = observation[PITCH_INDEX] * SCALE_PITCH;
			double pitchWanted = Bot.pitchTo(eye, aim);
			boolean drawn = observation[DRAW_INDEX] * SCALE_DRAW >= FULL_DRAW;
			return new ArcherAction(0.0F, 0.0F, Mth.clamp(yaw / 30.0F, -1.0F, 1.0F), (float) Mth.clamp((pitchWanted - pitchNow) / 30.0, -1.0, 1.0), false,
				ArcherAction.Bow.FIRE);
		};
	}
}
