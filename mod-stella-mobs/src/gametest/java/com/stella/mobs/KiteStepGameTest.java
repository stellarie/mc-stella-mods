package com.stella.mobs;

import com.stella.mobs.behavior.KeepDistanceGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A ranged unit that a pursuer presses kites: it walks a step away, stops, looses one shot, and walks again.
 *
 * <p>The pursuer walks faster than the unit and holds two blocks off it, so the unit stays inside its near edge for the whole
 * test. A step is one run of the distance goal. A rest is the time between two runs. Every time counts the unit's own
 * ticks, because the level clock barely moves in a test.
 */
public class KiteStepGameTest {
	private static final String TEST = "kiteStep";
	private static final int MAX_TICKS = StanceGameTest.READY_TICKS + 700;
	private static final double CENTER = WideArena.SIZE / 2.0 + 0.5;
	private static final double MOB_Z = 12.5;
	private static final double START_GAP = 2.5;
	private static final double PURSUIT_SPEED = 0.5;
	private static final double PURSUIT_HOLD = 2.0;
	private static final int SETTLE_TICKS = 1;
	private static final int STEPS = 3;
	private static final double STEP_TOLERANCE = 1.0;
	private static final int WINDOW_TICKS = 400;
	private static final int HEARTBEAT_TICKS = 20;
	private static final int CHUNK = 16;

	/** One closed step and what the unit did in the rest after it. */
	private record Step(double travel, int shots) {
	}

	/**
	 * A skeleton with a bow. Over its first three steps each walk covers the step length within a block, and each rest
	 * that follows holds a shot.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void skeletonKitesInSteps(final GameTestHelper helper) {
		kite(helper, "skeleton", EntityTypes.SKELETON, Items.BOW);
	}

	/** The same for a pillager with a crossbow: its rest covers the charge and the shot. */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void pillagerKitesInSteps(final GameTestHelper helper) {
		kite(helper, "pillager", EntityTypes.PILLAGER, Items.CROSSBOW);
	}

	private static <T extends Mob> void kite(final GameTestHelper helper, final String name, final EntityType<T> type, final Item weapon) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		List<Vec3> spots = spots();
		Mob[] mob = {null};
		ServerPlayer[] player = {null};
		StanceGameTest.whenTicking(helper, spots, () -> {
			ServerPlayer survivor = arena.survivalPlayer(CENTER, MOB_Z - START_GAP);
			survivor.setPermanentlyInvulnerable(true);
			player[0] = survivor;
			mob[0] = arena.spawn(type, CENTER, MOB_Z, spawned -> spawned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon)));
			WideArena.watch(mob[0], () -> survivor);
		});
		List<Step> steps = new ArrayList<>();
		boolean[] live = {false};
		boolean[] running = {false};
		int[] ticks = {0};
		Vec3[] stepFrom = {null};
		int[] restFrom = {0};
		double[] lastTravel = {0.0};
		helper.onEachTick(() -> {
			if (mob[0] == null || !mob[0].isAlive() || steps.size() >= STEPS) {
				return;
			}

			Mob unit = mob[0];
			ServerPlayer pursuer = player[0];
			WideArena.keepValid(pursuer);
			if (!live[0]) {
				if (!unit.onGround()) {
					return;
				}

				pursuer.setPermanentlyInvulnerable(false);
				live[0] = true;
			}

			unit.setTarget(pursuer);
			MeleeKitFloor.lookAt(pursuer, unit);
			ticks[0]++;
			if (ticks[0] < SETTLE_TICKS) {
				return;
			}

			Vec3 toUnit = flat(unit.position().subtract(pursuer.position()));
			Vec3 move = toUnit.length() <= PURSUIT_HOLD ? Vec3.ZERO : toUnit.normalize().scale(PURSUIT_SPEED);
			pursuer.setPos(pursuer.getX() + move.x, pursuer.getY(), pursuer.getZ() + move.z);
			pursuer.setKnownMovement(move);
			boolean now = distanceGoalRuns(unit);
			if (now && !running[0]) {
				if (stepFrom[0] != null) {
					int shots = (int) WideArena.shots(unit).stream().filter(shot -> shot.ownerTick() >= restFrom[0]).count();
					steps.add(new Step(lastTravel[0], shots));
					StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} STEP n={} travel={} shotsInRest={}", TEST, name, steps.size(), number(lastTravel[0]), shots);
				}

				stepFrom[0] = unit.position();
			}

			if (now) {
				lastTravel[0] = flat(unit.position().subtract(stepFrom[0])).length();
			} else if (running[0]) {
				restFrom[0] = unit.tickCount;
			}

			running[0] = now;
			if (ticks[0] % HEARTBEAT_TICKS == 0) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={}.{} t={} dist={} running={} steps={} shots={}",
					TEST, name, ticks[0], number(unit.distanceTo(pursuer)), now, steps.size(), WideArena.shots(unit).size()
				);
			}

			if (steps.size() < STEPS) {
				helper.assertTrue(ticks[0] < WINDOW_TICKS, "only " + steps.size() + " kite steps in " + WINDOW_TICKS + " ticks: " + steps);
				return;
			}

			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT steps={}", TEST, name, steps);
			for (Step step : steps) {
				helper.assertTrue(
					Math.abs(step.travel() - KeepDistanceGoal.STEP_BLOCKS) <= STEP_TOLERANCE,
					"a step walked " + number(step.travel()) + " blocks, not " + KeepDistanceGoal.STEP_BLOCKS + " plus or minus " + STEP_TOLERANCE + ": " + steps
				);
				helper.assertTrue(step.shots() >= 1, "a rest between steps held no shot: " + steps);
			}

			helper.succeed();
		});
	}

	private static boolean distanceGoalRuns(final Mob mob) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.isRunning() && wrapped.getGoal() instanceof KeepDistanceGoal<?>) {
				return true;
			}
		}

		return false;
	}

	/** Every chunk of the floor, so none still loads its entities. */
	private static List<Vec3> spots() {
		List<Vec3> spots = new ArrayList<>(List.of(new Vec3(CENTER, 1.0, MOB_Z), new Vec3(CENTER, 1.0, MOB_Z - START_GAP)));
		List<Integer> lines = new ArrayList<>();
		for (int at = 0; at < WideArena.SIZE; at += CHUNK) {
			lines.add(at);
		}

		lines.add(WideArena.SIZE - 1);
		for (int x : lines) {
			for (int z : lines) {
				spots.add(new Vec3(x, 1.0, z));
			}
		}

		return spots;
	}

	private static Vec3 flat(final Vec3 vector) {
		return new Vec3(vector.x, 0.0, vector.z);
	}

	private static String number(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
