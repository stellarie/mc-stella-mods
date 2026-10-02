package com.stella.mobs;

import com.stella.mobs.behavior.RangedReach;
import com.stella.mobs.behavior.WitchPotions;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.phys.Vec3;

/**
 * The witch's own potion reaches the far edge of its band, and it leads a target that runs. Vanilla throws at 0.75
 * blocks per tick, and a potion falls 0.05 a tick, so a vanilla throw drops short at about 11 blocks. The mod's
 * throw solves the arc for the distance it is given. Each case reads where the potion ends, against where the
 * target stands.
 */
public class WitchReachGameTest {
	private static final int MAX_TICKS = 500;
	private static final double LANDING_TOLERANCE = 2.0;
	private static final double CENTER = WideArena.SIZE / 2.0;
	private static final double WITCH_Z = 6.5;
	private static final double RUN_DISTANCE = 18.0;
	private static final double RUN_SPEED = 0.2;
	private static final double HALF_SWEEP = 10.0;
	/** The witch settles a little as it lands, so a throw from this far counts as a throw from the target's distance. */
	private static final double SETTLE = 2.0;
	/** A potion thrown at the witch's own feet is no throw at the target. */
	private static final double AIMED_SPEED = 0.2;

	/**
	 * A potion thrown at a still target 24 blocks away lands within two blocks of it. The vanilla witch goal throws
	 * beside the mod goal, so the case reads only the mod's own splash potions, which carry a custom effect on an empty
	 * potion.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void potionLandsAt24(final GameTestHelper helper) {
		landsNearTarget(helper, "potionLandsAt24", RangedReach.RANGE, false);
	}

	/**
	 * The target runs sideways at 0.2 blocks per tick, and the potion is in the air for about a second, so a throw at
	 * the target's position lands several blocks behind it. The potion has to arrive where the runner will be.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void potionLeadsARunner(final GameTestHelper helper) {
		landsNearTarget(helper, "potionLeadsARunner", RUN_DISTANCE, true);
	}

	private static void landsNearTarget(final GameTestHelper helper, final String name, final double distance, final boolean runs) {
		WideArena arena = WideArena.build(helper);
		Witch[] witch = new Witch[1];
		ServerPlayer[] target = new ServerPlayer[1];
		boolean[] logged = new boolean[1];
		double startX = runs ? CENTER - HALF_SWEEP : CENTER;
		double[] run = {startX, 1.0};
		arena.whenReady(
			List.of(new Vec3(CENTER, 1.0, WITCH_Z), new Vec3(startX, 1.0, WITCH_Z + distance)),
			() -> {
				target[0] = arena.survivalPlayer(startX, WITCH_Z + distance);
				witch[0] = arena.spawn(EntityTypes.WITCH, CENTER, WITCH_Z, mob -> { });
				WideArena.watch(witch[0], () -> target[0]);
			}
		);
		helper.onEachTick(() -> {
			if (witch[0] == null) {
				return;
			}

			WideArena.keepValid(target[0]);
			witch[0].setTarget(target[0]);
			if (!runs) {
				return;
			}

			run[0] += run[1] * RUN_SPEED;
			if (run[0] > CENTER + HALF_SWEEP) {
				run[1] = -1.0;
			} else if (run[0] < CENTER - HALF_SWEEP) {
				run[1] = 1.0;
			}

			Vec3 at = arena.at(run[0], 1.0, WITCH_Z + distance);
			target[0].setPos(at.x, at.y, at.z);
			target[0].setKnownMovement(new Vec3(run[1] * RUN_SPEED, 0.0, 0.0));
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(witch[0] != null, "the arena is not up yet");
			Optional<WideArena.Shot> thrown = WideArena.shots(witch[0]).stream()
				.filter(shot -> shot.projectile() instanceof ThrownSplashPotion potion && WitchPotions.isModThrow(potion.getItem()))
				.filter(shot -> Math.hypot(shot.velocity().x, shot.velocity().z) >= AIMED_SPEED)
				.findFirst();
			helper.assertTrue(thrown.isPresent(), "the witch has thrown no mod potion at the player: " + state(witch[0], target[0]));
			WideArena.Shot shot = thrown.get();
			helper.assertTrue(shot.projectile().isRemoved(), "the potion is still in flight");
			Vec3 landed = shot.projectile().position();
			double error = landed.distanceTo(target[0].getBoundingBox().getCenter());
			if (!logged[0]) {
				logged[0] = true;
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=witchReach.{} RESULT throwDistance={} landingError={} flightTicks={} throwSpeed={}",
					name, number(shot.distance()), number(error), shot.projectile().tickCount, number(shot.velocity().length())
				);
				arena.finish();
			}

			helper.assertTrue(
				shot.distance() >= distance - SETTLE,
				"the witch threw from " + number(shot.distance()) + " blocks, under " + (distance - SETTLE)
			);
			helper.assertTrue(
				error <= LANDING_TOLERANCE,
				"the potion landed " + number(error) + " blocks from the target, past " + LANDING_TOLERANCE + ", at " + landed
			);
		});
	}

	private static String state(final Witch witch, final ServerPlayer target) {
		return "ticks=" + witch.tickCount + " distance=" + number(witch.distanceTo(target)) + " sees=" + witch.getSensing().hasLineOfSight(target)
			+ " target=" + (witch.getTarget() == null ? "none" : "set") + " shots=" + WideArena.shots(witch).size();
	}

	private static String number(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
