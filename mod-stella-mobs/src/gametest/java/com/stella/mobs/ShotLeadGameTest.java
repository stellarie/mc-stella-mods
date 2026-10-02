package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.ShotLead;
import com.stella.mobs.gear.GearTier;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Shots lead a moving target. The lead point is the target's position after the shot's flight time. The first case
 * reads it as a sum, the second reads the vertical rule, and the rest shoot real projectiles at a player who runs
 * sideways and read which way each one points.
 */
public class ShotLeadGameTest {
	private static final int MAX_TICKS = 700;
	/** An arrow's launch speed from a mob bow. The sums need a speed and not that exact one. */
	private static final double SHOT_SPEED = 1.6;
	private static final double SIDEWAYS = 0.2;
	private static final double POINT_TOLERANCE = 0.5;
	private static final double CENTER = WideArena.SIZE / 2.0;
	private static final double SHOOTER_Z = 6.5;
	private static final double TARGET_Z = 24.5;
	/** The runner turns at the ends of this sweep, so the floor holds the whole run. */
	private static final double HALF_SWEEP = 10.0;
	private static final double SUM_DISTANCE = 20.0;
	private static final int SOLVER_PASSES = 60;
	private static final int FIREBALL_TICK_CAP = 400;
	/** Under the test's own limit, so a shooter that stops firing fails with its state and not with a bare timeout. */
	private static final int GIVE_UP_TICKS = MAX_TICKS - 100;

	/** Ticks a shot takes to cover `distance`, from the shot as it appeared. */
	private interface FlightTime {
		double ticks(WideArena.Shot shot, double distance);
	}

	@GameTest
	public void pointLeadsMovingTarget(final GameTestHelper helper) {
		Cow shooter = cow(helper, 0.0, 0.0);
		Cow target = cow(helper, 0.0, SUM_DISTANCE);
		target.setDeltaMovement(SIDEWAYS, 0.0, 0.0);
		Vec3 point = ShotLead.point(shooter, target, SHOT_SPEED);

		// The sum from first principles: flight time to
		// where the target will be, to a fixed point.
		Vec3 from = shooter.getEyePosition();
		Vec3 center = target.getBoundingBox().getCenter();
		double ticks = from.distanceTo(center) / SHOT_SPEED;
		for (int pass = 0; pass < SOLVER_PASSES; pass++) {
			ticks = from.distanceTo(center.add(SIDEWAYS * ticks, 0.0, 0.0)) / SHOT_SPEED;
		}

		Vec3 expected = target.position().add(SIDEWAYS * ticks, 0.0, 0.0);
		double error = point.distanceTo(expected);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=shotLead.pointLeadsMovingTarget RESULT point={} expected={} error={} flightTicks={}",
			point, expected, number(error), number(ticks)
		);
		shooter.discard();
		target.discard();
		helper.assertTrue(error <= POINT_TOLERANCE, "the lead point " + point + " is " + number(error) + " blocks from " + expected);
		helper.assertTrue(point.x - target.getX() > 1.0, "the lead point " + point + " does not lead the target at " + target.position());
		helper.succeed();
	}

	/**
	 * A target on the ground rests on the floor with a small downward speed, and aiming that far below its feet
	 * would bury every arrow. The same speed in the air leads downward. The air case is the control: without it the
	 * ground case could pass because the lead ignored the vertical speed everywhere.
	 */
	@GameTest
	public void groundedTargetGetsNoVerticalLead(final GameTestHelper helper) {
		Cow shooter = cow(helper, 0.0, 0.0);
		Cow grounded = cow(helper, 0.0, SUM_DISTANCE);
		Cow airborne = cow(helper, 4.0, SUM_DISTANCE);
		Vec3 falling = new Vec3(SIDEWAYS, -0.0784, 0.0);
		grounded.setDeltaMovement(falling);
		grounded.setOnGround(true);
		airborne.setDeltaMovement(falling);
		airborne.setOnGround(false);
		Vec3 onGround = ShotLead.point(shooter, grounded, SHOT_SPEED);
		Vec3 inAir = ShotLead.point(shooter, airborne, SHOT_SPEED);
		double groundedDrop = grounded.getY() - onGround.y;
		double airborneDrop = airborne.getY() - inAir.y;
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=shotLead.groundedTargetGetsNoVerticalLead RESULT groundedDrop={} airborneDrop={}",
			number(groundedDrop), number(airborneDrop)
		);
		shooter.discard();
		grounded.discard();
		airborne.discard();
		helper.assertTrue(Math.abs(groundedDrop) < 1.0E-9, "the grounded target's lead sank " + groundedDrop + " blocks");
		helper.assertTrue(airborneDrop > POINT_TOLERANCE, "the airborne target's lead sank only " + airborneDrop + " blocks, so the control proves nothing");
		helper.succeed();
	}

	/**
	 * A stray looses five arrows at a player who runs sideways. The player's server side movement stays zero, as it
	 * does in play, and the known movement carries the run, so a lead that read the wrong one would aim at the player's
	 * feet. Each arrow's bearing is compared, in yaw, with the player's position when the arrow left and with the
	 * point the player reaches by the time the arrow arrives. The arrows must on average point nearer the second.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void strayLeadsItsShots(final GameTestHelper helper) {
		leadsItsShots(helper, "stray", EntityTypes.STRAY, ShotLeadGameTest::holdBow, 5, ShotLeadGameTest::byArrowSpeed, false);
	}

	/** An elite stray's sniper arrow flies straight and fast, and leads by the same sum. */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void sniperLeadsItsShots(final GameTestHelper helper) {
		leadsItsShots(
			helper, "sniper", EntityTypes.STRAY, stray -> {
				holdBow(stray);
				Elite.mark(stray, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			}, 3, ShotLeadGameTest::byArrowSpeed, false
		);
	}

	/** A fireball starts slow and gains speed, so its flight time is not the distance over a launch speed. */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void blazeLeadsItsShots(final GameTestHelper helper) {
		leadsItsShots(helper, "blaze", EntityTypes.BLAZE, mob -> { }, 4, ShotLeadGameTest::byFireball, false);
	}

	/** A ghast drifts, so this case holds it in place, or it wanders out of the chunks that tick and stops firing. */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void ghastLeadsItsShots(final GameTestHelper helper) {
		leadsItsShots(helper, "ghast", EntityTypes.GHAST, mob -> { }, 4, ShotLeadGameTest::byFireball, true);
	}

	private static <T extends Mob> void leadsItsShots(
		final GameTestHelper helper, final String name, final EntityType<T> type, final Consumer<T> gear, final int shots,
		final FlightTime flight, final boolean pinned
	) {
		WideArena arena = WideArena.build(helper);
		Mob[] shooter = new Mob[1];
		ServerPlayer[] runner = new ServerPlayer[1];
		double[] run = {CENTER, 1.0};
		Vec3[] home = new Vec3[1];
		int[] waited = new int[1];
		arena.whenReady(
			List.of(new Vec3(CENTER, 1.0, SHOOTER_Z), new Vec3(CENTER, 1.0, TARGET_Z)),
			() -> {
				runner[0] = arena.survivalPlayer(CENTER, TARGET_Z);
				shooter[0] = arena.spawn(type, CENTER, SHOOTER_Z, gear);
				home[0] = shooter[0].position();
				WideArena.watch(shooter[0], () -> runner[0]);
			}
		);
		helper.onEachTick(() -> {
			if (shooter[0] == null) {
				return;
			}

			WideArena.keepValid(runner[0]);
			shooter[0].setTarget(runner[0]);
			if (pinned) {
				shooter[0].setPos(home[0].x, home[0].y, home[0].z);
				shooter[0].setDeltaMovement(Vec3.ZERO);
			}

			run[0] += run[1] * SIDEWAYS;
			if (run[0] > CENTER + HALF_SWEEP) {
				run[1] = -1.0;
			} else if (run[0] < CENTER - HALF_SWEEP) {
				run[1] = 1.0;
			}

			Vec3 at = arena.at(run[0], 1.0, TARGET_Z);
			runner[0].setPos(at.x, at.y, at.z);
			runner[0].setKnownMovement(new Vec3(run[1] * SIDEWAYS, 0.0, 0.0));
			List<WideArena.Shot> loosed = WideArena.shots(shooter[0]);
			if (loosed.size() < shots) {
				if (++waited[0] > GIVE_UP_TICKS) {
					helper.fail(
						"the " + name + " loosed " + loosed.size() + " of " + shots + " shots in " + GIVE_UP_TICKS + " ticks: distance="
							+ number(shooter[0].distanceTo(runner[0])) + " sees=" + shooter[0].getSensing().hasLineOfSight(runner[0])
							+ " at=" + shooter[0].position().subtract(arena.at(0.0, 0.0, 0.0)) + " alive=" + shooter[0].isAlive()
							+ " target=" + (shooter[0].getTarget() == runner[0]) + " ticking="
							+ helper.getLevel().isPositionEntityTicking(shooter[0].blockPosition())
							+ " difficulty=" + helper.getLevel().getDifficulty()
					);
				}

				return;
			}

			double toLead = 0.0;
			double toCurrent = 0.0;
			for (WideArena.Shot shot : loosed.subList(0, shots)) {
				Vec3 now = shot.targetAt().subtract(shot.spawnAt());
				double ticks = flight.ticks(shot, now.length());
				Vec3 lead = shot.targetAt().add(shot.targetMotion().x * ticks, 0.0, shot.targetMotion().z * ticks).subtract(shot.spawnAt());
				double aimed = Math.atan2(shot.velocity().z, shot.velocity().x);
				toCurrent += Math.abs(wrap(aimed - Math.atan2(now.z, now.x)));
				toLead += Math.abs(wrap(aimed - Math.atan2(lead.z, lead.x)));
			}

			double meanToCurrent = Math.toDegrees(toCurrent / shots);
			double meanToLead = Math.toDegrees(toLead / shots);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=shotLead.{}LeadsItsShots RESULT shots={} meanDegreesToLead={} meanDegreesToCurrent={}",
				name, shots, number(meanToLead), number(meanToCurrent)
			);
			arena.finish();
			helper.assertTrue(
				meanToLead < meanToCurrent,
				"the " + name + "'s shots point " + number(meanToLead) + " degrees from the lead point and " + number(meanToCurrent)
					+ " from the player's position"
			);
			helper.succeed();
		});
	}

	/** An arrow's launch speed is the length of its first velocity. */
	private static double byArrowSpeed(final WideArena.Shot shot, final double distance) {
		return distance / shot.velocity().length();
	}

	/**
	 * A vanilla fireball leaves at 0.1 blocks per tick, gains 0.1 each tick, and keeps 95 percent of its speed.
	 * Counted tick by tick here, apart from the mod's own sum.
	 */
	private static double byFireball(final WideArena.Shot shot, final double distance) {
		double speed = 0.1;
		double covered = 0.0;
		int ticks = 0;
		while (covered < distance && ticks < FIREBALL_TICK_CAP) {
			speed = (speed + 0.1) * 0.95;
			covered += speed;
			ticks++;
		}

		return ticks;
	}

	private static void holdBow(final Mob mob) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	private static Cow cow(final GameTestHelper helper, final double x, final double z) {
		Cow cow = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		cow.setPos(at.x, at.y, at.z);
		return cow;
	}

	private static double wrap(final double angle) {
		return Math.atan2(Math.sin(angle), Math.cos(angle));
	}

	private static String number(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
