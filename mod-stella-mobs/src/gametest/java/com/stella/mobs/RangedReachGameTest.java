package com.stella.mobs;

import com.stella.mobs.behavior.BossAbilities;
import com.stella.mobs.behavior.CrossbowCadenceGoal;
import com.stella.mobs.behavior.RangedReach;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.SkeletonVolleyGoal;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.RoleSpec;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Every ranged unit shoots from the far edge of its band. Each case puts a shooter and a survival player on a wide
 * floor and watches what the shooter does. A shot counts where it appears, against the distance from the shooter to
 * its target at that tick. The cases cover the band, the follow range, the end of the approach, the sight clause,
 * and the crossbow piglin. The witch potion and the shot lead have their own classes.
 */
public class RangedReachGameTest {
	private static final int MAX_TICKS = 500;
	/** One block inside the far edge, so a unit has a reason to fire and none to move. */
	private static final double START_DISTANCE = RangedReach.RANGE - 1.0;
	private static final double FIRST_SHOT_MIN = RangedReach.RANGE - 4.0;
	private static final double CLOSEST_MIN = RangedReach.RANGE - 6.0;
	private static final double MAGIC_START_DISTANCE = RangedReach.MAGIC_RANGE - 1.0;
	private static final double MAGIC_MIN = RangedReach.MAGIC_RANGE - 4.0;
	private static final double PIGLIN_START_DISTANCE = RangedReach.RANGE - 4.0;
	// A piglin walks until it sees its target.
	// Vanilla's range is 8, so 12 is far from it.
	private static final double PIGLIN_MIN = RangedReach.RANGE - 12.0;
	private static final int WINDOW_TICKS = 140;
	/** The shooter's own ticks. Vanilla rolls one in ten for a target once per two ticks, so 100 ticks miss one run in 200. */
	private static final int ACQUIRE_TICKS = 200;
	// Two blocks past the edge, so the unit
	// crosses early in the 60 tick window.
	private static final double APPROACH_START = RangedReach.RANGE + 2.0;
	private static final int APPROACH_SHOT_TICKS = 40;
	private static final int SIGHT_TICKS = 100;
	private static final double CENTER = WideArena.SIZE / 2.0;
	private static final double SHOOTER_Z = 6.5;
	/** A potion thrown at the shooter's own feet is no shot at the target. */
	private static final double AIMED_SPEED = 0.2;
	private static final int WALL_HALF_WIDTH = 2;
	private static final int WALL_HEIGHT = 4;
	private static final int WALL_Z = 10;
	private static final double SIGHT_START_DISTANCE = 20.0;

	/** The shooter, its target, and what the driver has measured of them. */
	private static final class Scene {
		private volatile Mob shooter;
		private volatile LivingEntity target;
		private int ticks;
		private double closest = Double.MAX_VALUE;
		private int crossedAt = -1;
		private int acquiredAt = -1;
		private int groundTicks;
		private boolean blockedAtStart;
	}

	@GameTest
	public void bandMatchesReach(final GameTestHelper helper) {
		RoleSpec ranged = RoleSpec.of(CombatRole.RANGED);
		RoleSpec magic = RoleSpec.of(CombatRole.MAGIC);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=rangedReach.bandMatchesReach RESULT rangedFar={} reach={} magicFar={} magicReach={}",
			ranged.farDistance(), RangedReach.RANGE, magic.farDistance(), RangedReach.MAGIC_RANGE
		);
		helper.assertValueEqual(Double.valueOf(ranged.farDistance()), Double.valueOf(RangedReach.RANGE), "ranged far band");
		helper.assertValueEqual(Double.valueOf(magic.farDistance()), Double.valueOf(RangedReach.MAGIC_RANGE), "magic far band");
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void strayFiresFromRange(final GameTestHelper helper) {
		fireFromRange(helper, "stray", EntityTypes.STRAY, bow(), START_DISTANCE, FIRST_SHOT_MIN, CLOSEST_MIN);
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void boggedFiresFromRange(final GameTestHelper helper) {
		fireFromRange(helper, "bogged", EntityTypes.BOGGED, bow(), START_DISTANCE, FIRST_SHOT_MIN, CLOSEST_MIN);
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void pillagerFiresFromRange(final GameTestHelper helper) {
		fireFromRange(
			helper, "pillager", EntityTypes.PILLAGER, mob -> mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW)),
			START_DISTANCE, FIRST_SHOT_MIN, CLOSEST_MIN
		);
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void drownedFiresFromRange(final GameTestHelper helper) {
		fireFromRange(
			helper, "drowned", EntityTypes.DROWNED, mob -> mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT)),
			START_DISTANCE, FIRST_SHOT_MIN, CLOSEST_MIN
		);
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void witchFiresFromRange(final GameTestHelper helper) {
		fireFromRange(helper, "witch", EntityTypes.WITCH, mob -> { }, START_DISTANCE, FIRST_SHOT_MIN, CLOSEST_MIN);
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void illusionerFiresFromRange(final GameTestHelper helper) {
		fireFromRange(helper, "illusioner", EntityTypes.ILLUSIONER, bow(), MAGIC_START_DISTANCE, MAGIC_MIN, MAGIC_MIN);
	}

	/**
	 * A unit with no manual target picks the player by itself from 23 blocks. The follow range decides it: the
	 * vanilla default of 16 never reaches the player, and the target goal drops a target past that range.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void acquiresAt23(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		Scene scene = new Scene();
		arena.whenReady(spots(START_DISTANCE), () -> {
			scene.target = arena.survivalPlayer(CENTER, SHOOTER_Z + START_DISTANCE);
			scene.shooter = arena.spawn(EntityTypes.STRAY, CENTER, SHOOTER_Z, bow());
		});
		helper.onEachTick(() -> {
			if (scene.shooter == null) {
				return;
			}

			WideArena.keepValid(scene.target);
			if (scene.acquiredAt < 0 && scene.shooter.getTarget() == scene.target) {
				scene.acquiredAt = scene.shooter.tickCount;
			}
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(scene.shooter != null, "the arena is not up yet");
			double follow = scene.shooter.getAttributeValue(Attributes.FOLLOW_RANGE);
			helper.assertTrue(scene.acquiredAt >= 0, "the stray never targeted the player: follow=" + follow + " " + describe(scene.shooter, scene.target));
			helper.assertTrue(scene.acquiredAt <= ACQUIRE_TICKS, "the stray targeted the player on tick " + scene.acquiredAt + ", past " + ACQUIRE_TICKS);
			helper.assertTrue(
				follow >= RangedReach.FOLLOW_RANGE, "the stray's follow range is " + follow + ", under " + RangedReach.FOLLOW_RANGE
			);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=rangedReach.acquiresAt23 RESULT acquiredAt={} followRange={} distance={}",
				scene.acquiredAt, follow, number(scene.shooter.distanceTo(scene.target))
			);
			arena.finish();
		});
	}

	/**
	 * The burst and the reach each widen a skeleton's follow range to 32, and neither adds its width to the other's.
	 * Three skeletons cover the order the installer uses, burst first, and reach first. Each is installed twice more,
	 * because a load runs the installer again. A skeleton with the no behavior tag skips the installer, so its two
	 * installs run by hand, in the order the case names.
	 */
	@GameTest
	public void followRangeDoesNotStack(final GameTestHelper helper) {
		Skeleton installer = skeleton(helper, 1.5, false);
		Skeleton burstFirst = skeleton(helper, 3.5, true);
		Skeleton reachFirst = skeleton(helper, 5.5, true);
		SkeletonVolleyGoal.install(burstFirst);
		RangedReach.install(burstFirst);
		RangedReach.install(reachFirst);
		SkeletonVolleyGoal.install(reachFirst);
		for (Skeleton skeleton : List.of(installer, burstFirst, reachFirst)) {
			RangedReach.install(skeleton);
			SkeletonVolleyGoal.install(skeleton);
			RangedReach.install(skeleton);
		}

		double natural = installer.getAttributeValue(Attributes.FOLLOW_RANGE);
		double burstThenReach = burstFirst.getAttributeValue(Attributes.FOLLOW_RANGE);
		double reachThenBurst = reachFirst.getAttributeValue(Attributes.FOLLOW_RANGE);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=rangedReach.followRangeDoesNotStack RESULT installer={} burstThenReach={} reachThenBurst={}",
			natural, burstThenReach, reachThenBurst
		);
		List.of(installer, burstFirst, reachFirst).forEach(Skeleton::discard);
		helper.assertValueEqual(Double.valueOf(natural), Double.valueOf(RangedReach.FOLLOW_RANGE), "follow range after the installer");
		helper.assertValueEqual(Double.valueOf(burstThenReach), Double.valueOf(RangedReach.FOLLOW_RANGE), "follow range, burst then reach");
		helper.assertValueEqual(Double.valueOf(reachThenBurst), Double.valueOf(RangedReach.FOLLOW_RANGE), "follow range, reach then burst");
		helper.succeed();
	}

	/**
	 * A unit past its band walks in and stops at the edge, then looses its first arrow within 40 ticks. The approach is
	 * a 60 tick window that holds MOVE and LOOK, and the bow goal cannot start while it does, so a unit that keeps the
	 * window after it enters the band walks on and stays silent for seconds.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void approachEndsAtBand(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		Scene scene = new Scene();
		boolean[] logged = new boolean[1];
		arena.whenReady(spots(APPROACH_START), () -> {
			scene.target = arena.survivalPlayer(CENTER, SHOOTER_Z + APPROACH_START);
			scene.shooter = arena.spawn(EntityTypes.STRAY, CENTER, SHOOTER_Z, bow());
			WideArena.watch(scene.shooter, () -> scene.target);
		});
		helper.onEachTick(() -> {
			if (scene.shooter == null) {
				return;
			}

			WideArena.keepValid(scene.target);
			scene.shooter.setTarget(scene.target);
			scene.closest = Math.min(scene.closest, scene.shooter.distanceTo(scene.target));
			if (scene.crossedAt < 0 && scene.shooter.distanceTo(scene.target) <= RangedReach.RANGE) {
				scene.crossedAt = scene.shooter.tickCount;
			}
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(scene.shooter != null, "the arena is not up yet");
			List<WideArena.Shot> shots = WideArena.shots(scene.shooter);
			helper.assertTrue(scene.crossedAt >= 0, "the stray never closed to " + RangedReach.RANGE + ": " + describe(scene.shooter, scene.target));
			helper.assertTrue(!shots.isEmpty(), "the stray has loosed no arrow: crossedAt=" + scene.crossedAt + " " + describe(scene.shooter, scene.target));
			int wait = shots.get(0).ownerTick() - scene.crossedAt;
			if (!logged[0]) {
				logged[0] = true;
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=rangedReach.approachEndsAtBand RESULT crossedAt={} firstArrowAt={} wait={} closest={} firstArrowDistance={}",
					scene.crossedAt, shots.get(0).ownerTick(), wait, number(scene.closest), number(shots.get(0).distance())
				);
			}

			helper.assertTrue(
				wait <= APPROACH_SHOT_TICKS,
				"the stray loosed its first arrow " + wait + " ticks after it crossed " + RangedReach.RANGE + ", past " + APPROACH_SHOT_TICKS
			);
			arena.finish();
		});
	}

	/**
	 * A unit inside its band with a wall in the way must walk to a spot it can see from. The standoff goal stops the
	 * navigation, and without a sight clause it holds a unit behind cover for good.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void standoffNeedsSight(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		Scene scene = new Scene();
		for (int x = (int) CENTER - WALL_HALF_WIDTH; x <= (int) CENTER + WALL_HALF_WIDTH; x++) {
			for (int y = 1; y <= WALL_HEIGHT; y++) {
				helper.setBlock(x, y, WALL_Z, Blocks.STONE);
			}
		}

		arena.whenReady(spots(SIGHT_START_DISTANCE), () -> {
			scene.target = arena.survivalPlayer(CENTER, SHOOTER_Z + SIGHT_START_DISTANCE);
			scene.shooter = arena.spawn(EntityTypes.STRAY, CENTER, SHOOTER_Z, bow());
		});
		helper.onEachTick(() -> {
			if (scene.shooter == null || !scene.shooter.onGround() && scene.groundTicks == 0) {
				return;
			}

			WideArena.keepValid(scene.target);
			scene.shooter.setTarget(scene.target);
			boolean sees = scene.shooter.getSensing().hasLineOfSight(scene.target);
			if (scene.groundTicks++ == 0) {
				scene.blockedAtStart = !sees;
				helper.assertTrue(
					!sees, "the wall does not block sight, so the case proves nothing: " + describe(scene.shooter, scene.target)
				);
			}

			if (sees) {
				ReactiveState state = ReactiveState.get(scene.shooter);
				double distance = scene.shooter.distanceTo(scene.target);
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=rangedReach.standoffNeedsSight RESULT regainedSightAt={} blockedAtStart={} distance={} band={}..{}",
					scene.groundTicks, scene.blockedAtStart, number(distance), state.getNearDistance(), state.getFarDistance()
				);
				arena.finish();
				helper.succeed();
			} else if (scene.groundTicks > SIGHT_TICKS) {
				helper.fail("the stray never regained sight in " + SIGHT_TICKS + " ticks: " + describe(scene.shooter, scene.target));
			}
		});
	}

	/**
	 * A piglin with a crossbow shoots from 20 blocks (decision D5). The Brain alone drives it: the cadence goal is not
	 * installed, and the attack range check stretches to the reach. The bolt count is the U2 measurement.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void piglinFiresFromRange(final GameTestHelper helper) {
		double distance = PIGLIN_START_DISTANCE;
		WideArena arena = WideArena.build(helper);
		Scene scene = new Scene();
		arena.whenReady(spots(distance), () -> {
			scene.target = arena.survivalPlayer(CENTER, SHOOTER_Z + distance);
			scene.shooter = arena.spawn(EntityTypes.PIGLIN, CENTER, SHOOTER_Z, mob -> mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW)));
			WideArena.watch(scene.shooter, () -> scene.target);
		});
		helper.onEachTick(() -> {
			if (scene.shooter == null) {
				return;
			}

			WideArena.keepValid(scene.target);
			Piglin piglin = (Piglin) scene.shooter;
			piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, scene.target.getUUID(), 600L);
			scene.ticks++;
			scene.closest = Math.min(scene.closest, piglin.distanceTo(scene.target));
			if (scene.ticks == WINDOW_TICKS) {
				List<WideArena.Shot> shots = WideArena.shots(piglin);
				int drivers = BossAbilities.copiesOf(piglin, CrossbowCadenceGoal.class);
				long bolts = shots.stream().filter(shot -> shot.ownerTick() >= 20 && shot.ownerTick() < 120).count();
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=rangedReach.piglinFiresFromRange RESULT bolts={} boltsIn100Ticks={} firstBoltDistance={} closest={} cadenceGoals={} follow={}",
					shots.size(), bolts, shots.isEmpty() ? "none" : number(shots.get(0).distance()), number(scene.closest), drivers,
					piglin.getAttributeValue(Attributes.FOLLOW_RANGE)
				);
				arena.finish();
				helper.assertTrue(drivers == 0, "the piglin still has " + drivers + " cadence goals beside its Brain");
				helper.assertTrue(!shots.isEmpty(), "the piglin loosed no bolt at the player " + distance + " blocks away: " + describe(piglin, scene.target));
				helper.assertTrue(
					shots.get(0).distance() >= PIGLIN_MIN,
					"the piglin loosed its first bolt from " + number(shots.get(0).distance()) + " blocks, under " + PIGLIN_MIN
				);
				helper.assertTrue(scene.closest >= PIGLIN_MIN, "the piglin closed to " + number(scene.closest) + " blocks, under " + PIGLIN_MIN);
				helper.succeed();
			}
		});
	}

	/**
	 * The shooter fires at a still survival player and holds where it stands. The first aimed shot leaves from the
	 * far side of the band, and the shooter never closes past the margin: a unit with the old band of 15 walks in
	 * while it draws.
	 */
	private static <T extends Mob> void fireFromRange(
		final GameTestHelper helper, final String name, final EntityType<T> type, final Consumer<T> gear,
		final double distance, final double firstShotMin, final double closestMin
	) {
		WideArena arena = WideArena.build(helper);
		Scene scene = new Scene();
		arena.whenReady(spots(distance), () -> {
			scene.target = arena.survivalPlayer(CENTER, SHOOTER_Z + distance);
			scene.shooter = arena.spawn(type, CENTER, SHOOTER_Z, gear);
			WideArena.watch(scene.shooter, () -> scene.target);
		});
		helper.onEachTick(() -> {
			if (scene.shooter == null) {
				return;
			}

			WideArena.keepValid(scene.target);
			scene.shooter.setTarget(scene.target);
			scene.ticks++;
			scene.closest = Math.min(scene.closest, scene.shooter.distanceTo(scene.target));
			if (scene.ticks != WINDOW_TICKS) {
				return;
			}

			List<WideArena.Shot> shots = WideArena.shots(scene.shooter);
			Optional<WideArena.Shot> first = shots.stream().filter(RangedReachGameTest::aimed).findFirst();
			String state = describe(scene.shooter, scene.target);
			double followRange = scene.shooter.getAttributeValue(Attributes.FOLLOW_RANGE);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=rangedReach.{}FiresFromRange RESULT shots={} firstShotDistance={} closest={} followRange={} startDistance={}",
				name, shots.size(), first.map(shot -> number(shot.distance())).orElse("none"), number(scene.closest), followRange, number(distance)
			);
			arena.finish();
			helper.assertTrue(first.isPresent(), "the " + name + " loosed no aimed shot at the player in " + WINDOW_TICKS + " ticks: " + state);
			helper.assertTrue(
				first.get().distance() >= firstShotMin,
				"the " + name + " loosed its first shot from " + number(first.get().distance()) + " blocks, under " + firstShotMin + ": " + state
			);
			helper.assertTrue(
				scene.closest >= closestMin, "the " + name + " closed to " + number(scene.closest) + " blocks, under " + closestMin + ": " + state
			);
			helper.succeed();
		});
	}

	private static boolean aimed(final WideArena.Shot shot) {
		return Math.hypot(shot.velocity().x, shot.velocity().z) >= AIMED_SPEED;
	}

	private static <T extends Mob> Consumer<T> bow() {
		return mob -> mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	/** The shooter's spot and the player's, in the test's frame, which both must stand in a chunk that ticks. */
	private static List<Vec3> spots(final double distance) {
		return List.of(new Vec3(CENTER, 1.0, SHOOTER_Z), new Vec3(CENTER, 1.0, SHOOTER_Z + distance));
	}

	/** A bow skeleton the installer does not read when `tagged`, so the test runs its installs in its own order. */
	private static Skeleton skeleton(final GameTestHelper helper, final double x, final boolean tagged) {
		Skeleton skeleton = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, 2.5));
		skeleton.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		skeleton.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setPersistenceRequired();
		if (tagged) {
			skeleton.addTag(StellaMobs.NO_BEHAVIOR_TAG);
			ReactiveState.get(skeleton).setRoleSpec(RoleSpec.of(CombatRole.RANGED));
		}

		helper.getLevel().addFreshEntity(skeleton);
		return skeleton;
	}

	/** The shooter's state, for a failure message. */
	private static String describe(final Mob shooter, final LivingEntity target) {
		StringBuilder goals = new StringBuilder();
		for (WrappedGoal wrapped : shooter.getGoalSelector().getAvailableGoals()) {
			goals.append(wrapped.getGoal().getClass().getSimpleName()).append(wrapped.isRunning() ? ":run " : ":idle ");
		}

		ReactiveState state = ReactiveState.get(shooter);
		return "ticks=" + shooter.tickCount + " onGround=" + shooter.onGround() + " distance=" + number(shooter.distanceTo(target))
			+ " sees=" + shooter.getSensing().hasLineOfSight(target) + " target=" + (shooter.getTarget() == null ? "none" : "set")
			+ " role=" + state.getRole() + " band=" + state.getNearDistance() + ".." + state.getFarDistance()
			+ " follow=" + shooter.getAttributeValue(Attributes.FOLLOW_RANGE) + " playerInRange=" + state.playerInRange(shooter)
			+ " goals=" + goals;
	}

	private static String number(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
