package com.stella.mobs;

import com.stella.mobs.behavior.KiteGoal;
import com.stella.mobs.behavior.PlayerReadout;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.Stance;
import com.stella.mobs.behavior.StanceBands;
import com.stella.mobs.behavior.StanceGoal;
import com.stella.mobs.behavior.StanceRules;
import com.stella.mobs.config.RoleSpec;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * What a mob does once it reads the player. Each case puts one mob and one survival player on a wide floor. The player
 * is a headless mock: it never moves by itself, so a case reports a chase through the player's known movement, and a
 * case turns the player's head by setting its rotation. The mob's own health and the player's health share are set for
 * the case, and the driver holds both every tick. The player cannot be a target until the mob stands on the ground, so no
 * goal of the mob starts while it falls. Every distance is read in the driver's own ticks, from the tick that the mob
 * stands on the ground, because the level clock barely moves in a test. A melee subject is a vindicator, because a
 * zombie never retreats. Each case writes one summary line with its outcome.
 */
public class StanceGameTest {
	private static final String TEST = "stance";
	// WideArena.whenReady gives up after 300 ticks.
	// A quiet server runs that in about a second.
	// A floor in a new place needs longer.
	static final int READY_TICKS = 2400;
	private static final int MAX_TICKS = READY_TICKS + 400;
	private static final double CENTER = WideArena.SIZE / 2.0;
	private static final double LOW_MOB = 0.30;
	private static final double LOW_ZOMBIE = 0.20;
	private static final double FULL_MOB = 1.0;
	private static final double HIGH_PLAYER = 0.90;
	private static final double LOW_PLAYER = 0.10;
	// Near enough to count as a chase by range.
	// Inside a retreat that ends 8 blocks out.
	private static final double START_GAP = 3.0;
	private static final double MIN_GAIN = 4.0;
	private static final double MAX_FIGHT_GAIN = 1.0;
	private static final int RETREAT_WINDOW = 60;
	// An archer starts at its near edge.
	// The retreat gains only up to the gap of 8.
	private static final double RANGED_RETREAT_GAIN = 2.0;
	private static final double SHIELD_GAP = 10.0;
	private static final int GUARD_WINDOW = 60;
	private static final int FIGHT_WINDOW = 80;
	private static final int HEARTBEAT_TICKS = 5;
	private static final double CHASE_STEP = PlayerReadout.CHASE_SPEED * 2.0;
	// Four allies inside the morale radius.
	// They stand off the mob's escape line.
	private static final double[][] ALLY_OFFSETS = {{-6.0, -3.0}, {6.0, -3.0}, {-6.0, 0.0}, {6.0, 0.0}};
	// Healthy monsters 12 blocks east of the mob.
	// East is off the line away from the player.
	private static final double[][] PACK_OFFSETS = {{12.0, -1.0}, {12.0, 1.0}, {13.0, 0.0}};
	private static final double MIN_PACK_DOT = 0.5;
	private static final double MIN_TRIAL_MOVE = 0.5;
	private static final int PACK_TRIALS = 4;
	private static final int PACK_TRIAL_TICKS = 80;
	private static final int PACK_REST_TICKS = 5;
	private static final int PACK_MAX_TICKS = READY_TICKS + 900;
	private static final double KITE_START = 9.0;
	private static final int KITE_WINDOW = 240;
	// Two retreat windows and the rest between.
	// The unit has had its turns to clear the edge.
	private static final int KITE_SETTLED_FROM = 130;
	private static final double PURSUE_START = 20.0;
	private static final int PURSUE_WINDOW = 100;
	private static final double HEALTHY_MIN = 15.0;
	private static final double WEAK_X = 10.0;
	private static final double HEALTHY_X = 46.0;
	private static final double MELEE_KITE_START = 7.0;
	private static final int WATCH_TICKS = 40;
	// A kiter in its band stands still.
	// A half block step means no kite holds it.
	private static final double HOLD_DRIFT = 0.5;
	private static final int STRIKE_WINDOW = 100;
	private static final int DWELL_FLIP_TICKS = 20;
	private static final int DWELL_ENTRY_TICKS = 30;

	private enum Look {
		KEEP,
		AT_MOB,
		AWAY
	}

	/** What the driver does to the player on one tick. */
	private record Plan(IntToDoubleFunction playerShare, boolean chase, IntFunction<Look> look) {
		static Plan steady(final double playerShare, final boolean chase) {
			return new Plan(tick -> playerShare, chase, tick -> Look.KEEP);
		}
	}

	/** The stance of a mob on one of its own ticks. */
	private record StanceSample(int mobTick, Stance stance) {
	}

	/** One mob, one player, and what was measured. */
	private static final class Scene {
		private final String name;
		private volatile ServerPlayer player;
		private volatile Mob mob;
		private final List<Mob> hunters = new ArrayList<>();
		private final List<Double> distances = new ArrayList<>();
		// From the mob's first tick, so a dwell is exact.
		private final List<StanceSample> stanceTrace = new ArrayList<>();
		private final EnumSet<Stance> seen = EnumSet.noneOf(Stance.class);
		private boolean live;
		private boolean done;
		private int ticks;
		private double startDistance;
		private Vec3 startPosition = Vec3.ZERO;
		private double maxGain;
		private double closest = Double.MAX_VALUE;
		private int retreatTicks;

		private Scene(final String name) {
			this.name = name;
		}

		/**
		 * Applies the plan for this tick, then measures. It answers whether a measurement was taken, which is false until
		 * the mob stands on the ground.
		 */
		private boolean step(final Plan plan) {
			ServerPlayer player = this.player;
			Mob mob = this.mob;
			if (player == null || mob == null || !mob.isAlive() || this.done) {
				return false;
			}

			WideArena.keepValid(player);
			player.setHealth((float) (plan.playerShare().applyAsDouble(this.ticks) * player.getMaxHealth()));
			this.stanceTrace.add(new StanceSample(mob.tickCount, Stance.of(mob)));
			if (!this.live) {
				if (!mob.onGround()) {
					return false;
				}

				// The mob stands. The player can be a target.
				player.setPermanentlyInvulnerable(false);
				this.live = true;
				this.startDistance = mob.distanceTo(player);
				this.startPosition = mob.position();
			}

			// After the player turns vulnerable. The allies
			// then hunt before the mob reads its stance.
			mob.setTarget(player);
			for (Mob hunter : this.hunters) {
				hunter.setTarget(player);
			}

			switch (plan.look().apply(this.ticks)) {
				case AT_MOB -> MeleeKitFloor.lookAt(player, mob);
				case AWAY -> MeleeKitFloor.lookAway(player, mob);
				case KEEP -> {
				}
			}

			player.setKnownMovement(plan.chase() ? chaseMovement(player, mob) : Vec3.ZERO);
			double distance = mob.distanceTo(player);
			Stance stance = Stance.of(mob);
			this.distances.add(distance);
			this.seen.add(stance);
			this.closest = Math.min(this.closest, distance);
			this.maxGain = Math.max(this.maxGain, distance - this.startDistance);
			boolean retreating = running(mob, RetreatGoal.class);
			if (retreating) {
				this.retreatTicks++;
			}

			this.ticks++;
			if (this.ticks % HEARTBEAT_TICKS == 0) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test={}.{} t={} dist={} stance={} retreating={} mobHealth={} playerShare={} goals={}",
					TEST, this.name, this.ticks, number(distance), stance, retreating, number(mob.getHealth()),
					number(PlayerReadout.share(player)), runningGoals(mob)
				);
			}

			return true;
		}

		/** The smallest distance from tick `from` to `to`. */
		private double closestBetween(final int from, final int to) {
			double smallest = Double.MAX_VALUE;
			for (int tick = from; tick < Math.min(to, this.distances.size()); tick++) {
				smallest = Math.min(smallest, this.distances.get(tick));
			}

			return smallest;
		}

		private String describe() {
			return "ticks=" + this.ticks + " startDistance=" + number(this.startDistance) + " closest=" + number(this.closest)
				+ " maxGain=" + number(this.maxGain) + " retreatTicks=" + this.retreatTicks + " stances=" + this.seen
				+ " finalStance=" + (this.mob == null ? "none" : Stance.of(this.mob));
		}

		private void result(final String outcome) {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {} {}", TEST, this.name, outcome, this.describe());
		}
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void lowMobRetreatsFromChaser(final GameTestHelper helper) {
		helper.assertTrue(
			RetreatGoal.STANCE_GAP - START_GAP >= MIN_GAIN,
			"a retreat ends at " + RetreatGoal.STANCE_GAP + " blocks, so it cannot grow " + MIN_GAIN + " from " + START_GAP
		);
		Scene scene = meleeScene(helper, "lowMobRetreatsFromChaser", LOW_MOB, HIGH_PLAYER, START_GAP, 0);
		retreats(helper, scene, MIN_GAIN);
	}

	/**
	 * A hurt archer retreats from a chaser, past its own standoff. The standoff goal stops the navigation of a unit that
	 * stands in its band and sees its target. A retreat that left it running would never leave the spot.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void lowRangedMobRetreatsFromChaser(final GameTestHelper helper) {
		double gap = RoleSpec.RANGED_NEAR + 0.5;
		helper.assertTrue(
			RetreatGoal.STANCE_GAP - gap >= RANGED_RETREAT_GAIN,
			"a retreat ends at " + RetreatGoal.STANCE_GAP + " blocks, so it cannot grow " + RANGED_RETREAT_GAIN + " from " + gap
		);
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene("lowRangedMobRetreatsFromChaser");
		whenTicking(helper, spots(gap), () -> {
			scene.player = survivor(helper, arena, CENTER, CENTER, HIGH_PLAYER);
			scene.mob = spawnAt(arena, EntityTypes.SKELETON, CENTER, CENTER + gap, LOW_MOB, StanceGameTest::bow);
		});
		retreats(helper, scene, RANGED_RETREAT_GAIN);
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void lowMobFightsLowPlayer(final GameTestHelper helper) {
		Scene scene = meleeScene(helper, "lowMobFightsLowPlayer", LOW_MOB, LOW_PLAYER, START_GAP, 0);
		holdsItsGround(helper, scene, Plan.steady(LOW_PLAYER, true));
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void zombieNeverRetreats(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene("zombieNeverRetreats");
		whenTicking(helper, spots(START_GAP), () -> {
			scene.player = survivor(helper, arena, CENTER, CENTER, HIGH_PLAYER);
			scene.mob = spawnAt(arena, EntityTypes.ZOMBIE, CENTER, CENTER + START_GAP, LOW_ZOMBIE, mob -> { });
		});
		holdsItsGround(helper, scene, Plan.steady(HIGH_PLAYER, true));
	}

	/**
	 * A zombie with a shield keeps its guard at low health, because a zombie fights on (decision D1). The old rule dropped
	 * every guard at 35 percent health.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void lowShieldZombieKeepsItsGuard(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene("lowShieldZombieKeepsItsGuard");
		whenTicking(helper, spots(SHIELD_GAP), () -> {
			scene.player = survivor(helper, arena, CENTER, CENTER, HIGH_PLAYER);
			scene.mob = spawnAt(arena, EntityTypes.ZOMBIE, CENTER, CENTER + SHIELD_GAP, LOW_ZOMBIE, StanceGameTest::sword);
		});
		Plan plan = new Plan(tick -> HIGH_PLAYER, false, tick -> Look.AT_MOB);
		helper.onEachTick(() -> {
			if (!scene.step(plan)) {
				return;
			}

			if (scene.mob.isBlocking()) {
				scene.done = true;
				scene.result("guarded");
				helper.succeed();
			} else if (scene.ticks >= GUARD_WINDOW) {
				scene.done = true;
				scene.result("unguarded");
				helper.fail("the low zombie never raised its shield for a player who watched it: " + scene.describe());
			}
		});
	}

	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void packHoldsMorale(final GameTestHelper helper) {
		Scene scene = meleeScene(helper, "packHoldsMorale", LOW_MOB, HIGH_PLAYER, START_GAP, ALLY_OFFSETS.length);
		helper.assertTrue(
			StanceRules.mobLowLine(ALLY_OFFSETS.length) < LOW_MOB && StanceRules.mobLowLine(0) > LOW_MOB,
			"the mob is not under the lone line and over the pack line, so the case proves nothing"
		);
		Plan plan = Plan.steady(HIGH_PLAYER, true);
		helper.onEachTick(() -> {
			if (!scene.step(plan) || scene.ticks < FIGHT_WINDOW) {
				return;
			}

			scene.done = true;
			for (Mob ally : scene.hunters) {
				helper.assertTrue(ally.isAlive() && ally.getTarget() == scene.player, ally + " does not hunt the player, so it gives the mob no morale");
				helper.assertTrue(
					ally.distanceTo(scene.mob) <= StanceRules.MORALE_RADIUS, ally + " stands outside the morale radius of the mob"
				);
			}

			scene.result("held");
			helper.assertTrue(scene.retreatTicks == 0, "the mob retreated with " + scene.hunters.size() + " allies beside it: " + scene.describe());
			helper.assertTrue(
				scene.maxGain <= MAX_FIGHT_GAIN, "the mob gave ground with " + scene.hunters.size() + " allies beside it: " + scene.describe()
			);
			helper.succeed();
		});
	}

	/**
	 * A retreating mob runs toward the pack, and not away along the line from the player. The random flee spot of the old
	 * retreat lands toward the pack one time in three, so the case runs the retreat several times, each with a new mob,
	 * and needs every run to go toward the pack.
	 */
	@GameTest(maxTicks = PACK_MAX_TICKS, padding = WideArena.PADDING)
	public void retreatTowardPack(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		ServerPlayer[] player = new ServerPlayer[1];
		List<Mob> pack = new ArrayList<>();
		List<double[]> scores = new ArrayList<>();
		List<String> trials = new ArrayList<>();
		Scene[] current = new Scene[1];
		int[] rest = {0};
		List<Vec3> ready = new ArrayList<>(spots(START_GAP));
		for (double[] offset : PACK_OFFSETS) {
			ready.add(new Vec3(CENTER + offset[0], 1.0, CENTER + START_GAP + offset[1]));
		}

		whenTicking(helper, ready, () -> {
			player[0] = survivor(helper, arena, CENTER, CENTER, HIGH_PLAYER);
			for (double[] offset : PACK_OFFSETS) {
				pack.add(idle(arena.spawn(EntityTypes.ZOMBIE, CENTER + offset[0], CENTER + START_GAP + offset[1], mob -> { })));
			}
		});
		Plan plan = Plan.steady(HIGH_PLAYER, true);
		helper.onEachTick(() -> {
			if (player[0] == null || scores.size() >= PACK_TRIALS) {
				return;
			}

			if (current[0] == null) {
				if (rest[0]-- <= 0) {
					Scene next = new Scene("retreatTowardPack");
					next.player = player[0];
					next.player.setPermanentlyInvulnerable(true);
					next.mob = spawnAt(arena, EntityTypes.VINDICATOR, CENTER, CENTER + START_GAP, LOW_MOB, mob -> { });
					current[0] = next;
				}

				return;
			}

			Scene trial = current[0];
			if (!trial.step(plan)) {
				return;
			}

			boolean reached = trial.mob.distanceTo(trial.player) >= RetreatGoal.STANCE_GAP;
			if (!reached && trial.ticks < PACK_TRIAL_TICKS) {
				return;
			}

			Vec3 moved = flat(trial.mob.position().subtract(trial.startPosition));
			Vec3 toPack = flat(centroid(pack).subtract(trial.startPosition));
			double dot = moved.length() < MIN_TRIAL_MOVE ? -1.0 : moved.normalize().dot(toPack.normalize());
			scores.add(new double[] {dot, trial.maxGain});
			trials.add("dot=" + number(dot) + " gain=" + number(trial.maxGain) + " ticks=" + trial.ticks + " retreatTicks=" + trial.retreatTicks);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.retreatTowardPack TRIAL {} {}", TEST, trials.size(), trials.get(trials.size() - 1));
			trial.done = true;
			trial.mob.discard();
			current[0] = null;
			rest[0] = PACK_REST_TICKS;
			if (scores.size() < PACK_TRIALS) {
				return;
			}

			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.retreatTowardPack RESULT trials={}", TEST, trials);
			boolean allToward = scores.stream().allMatch(score -> score[0] >= MIN_PACK_DOT && score[1] >= MIN_GAIN);
			helper.assertTrue(allToward, "a retreat did not go toward the pack and away from the player: " + trials);
			helper.succeed();
		});
	}

	/**
	 * A hurt skeleton keeps out of the kite band and fires from it. The subject is a plain skeleton, whose volley goal
	 * stands still while it shoots. A stray's vanilla bow goal strafes in and out by itself, and it can walk a block or more
	 * inside the band while its retreat rests, so a stray cannot prove the distance.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void rangedKiteKeepsDistanceAndFires(final GameTestHelper helper) {
		helper.assertTrue(
			KITE_START > PlayerReadout.CHASE_RANGE && KITE_START < StanceBands.KITE_NEAR,
			"the start gap must lie between the chase range and the kite band, or the case proves nothing"
		);
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene("rangedKiteKeepsDistanceAndFires");
		whenTicking(helper, spots(KITE_START), () -> {
			scene.player = survivor(helper, arena, CENTER, CENTER, HIGH_PLAYER);
			scene.mob = spawnAt(arena, EntityTypes.SKELETON, CENTER, CENTER + KITE_START, LOW_MOB, StanceGameTest::bow);
			WideArena.watch(scene.mob, () -> scene.player);
		});
		Plan plan = Plan.steady(HIGH_PLAYER, false);
		helper.onEachTick(() -> {
			if (!scene.step(plan) || scene.ticks < KITE_WINDOW) {
				return;
			}

			scene.done = true;
			double settled = scene.closestBetween(KITE_SETTLED_FROM, KITE_WINDOW);
			int arrows = WideArena.shots(scene.mob).size();
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={}.rangedKiteKeepsDistanceAndFires RESULT settledClosest={} arrows={} {}",
				TEST, number(settled), arrows, scene.describe()
			);
			helper.assertTrue(
				settled >= StanceBands.KITE_NEAR - 1.0,
				"the low skeleton did not keep its distance: closest " + number(settled) + " after tick " + KITE_SETTLED_FROM + ": " + scene.describe()
			);
			helper.assertTrue(arrows >= 1, "the kiting skeleton loosed no arrow in " + KITE_WINDOW + " ticks: " + scene.describe());
			helper.succeed();
		});
	}

	/**
	 * A full health stray presses a player at 10 percent, and holds its far band against one at 90 percent. The two
	 * scenes share one floor, 36 blocks apart, so neither stray sees the other's player.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void rangedPursuesWeakPlayer(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene weak = new Scene("rangedPursuesWeakPlayer.weak");
		Scene healthy = new Scene("rangedPursuesWeakPlayer.healthy");
		double playerZ = CENTER;
		double strayZ = CENTER - PURSUE_START;
		whenTicking(
			helper,
			List.of(
				new Vec3(WEAK_X, 1.0, strayZ), new Vec3(WEAK_X, 1.0, playerZ),
				new Vec3(HEALTHY_X, 1.0, strayZ), new Vec3(HEALTHY_X, 1.0, playerZ)
			),
			() -> {
				weak.player = survivor(helper, arena, WEAK_X, playerZ, LOW_PLAYER);
				weak.mob = spawnAt(arena, EntityTypes.STRAY, WEAK_X, strayZ, FULL_MOB, StanceGameTest::bow);
				healthy.player = survivor(helper, arena, HEALTHY_X, playerZ, HIGH_PLAYER);
				healthy.mob = spawnAt(arena, EntityTypes.STRAY, HEALTHY_X, strayZ, FULL_MOB, StanceGameTest::bow);
			}
		);
		Plan weakPlan = Plan.steady(LOW_PLAYER, false);
		Plan healthyPlan = Plan.steady(HIGH_PLAYER, false);
		helper.onEachTick(() -> {
			weak.step(weakPlan);
			healthy.step(healthyPlan);
			if (weak.ticks < PURSUE_WINDOW || healthy.ticks < PURSUE_WINDOW || weak.done) {
				return;
			}

			weak.done = true;
			healthy.done = true;
			weak.result("weakPlayer");
			healthy.result("healthyPlayer");
			helper.assertTrue(
				weak.closest <= StanceBands.PURSUE_FAR,
				"the healthy stray did not close to " + StanceBands.PURSUE_FAR + " blocks on a player at 10 percent: " + weak.describe()
			);
			helper.assertTrue(
				healthy.closest >= HEALTHY_MIN, "the healthy stray closed on a player at 90 percent: " + healthy.describe()
			);
			helper.succeed();
		});
	}

	/**
	 * A low melee mob keeps its distance from a player who watches it, and strikes when the player looks away. The watch
	 * is shorter than the hold limit, so the mob has no reason to strike under the gaze yet.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void meleeKiteStrikesWhenUnwatched(final GameTestHelper helper) {
		helper.assertTrue(
			WATCH_TICKS < KiteGoal.HOLD_TICKS
				&& MELEE_KITE_START > PlayerReadout.CHASE_RANGE
				&& MELEE_KITE_START >= KiteGoal.NEAR
				&& MELEE_KITE_START <= KiteGoal.FAR,
			"the watch and the start gap do not sit inside the kite rules, so the case proves nothing"
		);
		Scene scene = meleeScene(helper, "meleeKiteStrikesWhenUnwatched", LOW_MOB, HIGH_PLAYER, MELEE_KITE_START, 0);
		Plan plan = new Plan(tick -> HIGH_PLAYER, false, tick -> tick < WATCH_TICKS ? Look.AT_MOB : Look.AWAY);
		double[] watchedClosest = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			if (!scene.step(plan)) {
				return;
			}

			if (scene.ticks == WATCH_TICKS) {
				watchedClosest[0] = scene.closest;
				helper.assertTrue(
					watchedClosest[0] >= KiteGoal.NEAR && watchedClosest[0] >= MELEE_KITE_START - HOLD_DRIFT,
					"the low mob closed to " + number(watchedClosest[0]) + " blocks of a player who watched it: " + scene.describe()
				);
			}

			if (scene.ticks > WATCH_TICKS && scene.mob.isWithinMeleeAttackRange(scene.player)) {
				scene.done = true;
				scene.result("struck watchedClosest=" + number(watchedClosest[0]));
				helper.assertTrue(scene.retreatTicks == 0, "the low mob turned its strike into a retreat: " + scene.describe());
				helper.succeed();
			} else if (scene.ticks >= WATCH_TICKS + STRIKE_WINDOW) {
				scene.done = true;
				scene.result("held");
				helper.fail("the low mob never closed to melee reach after the player looked away: " + scene.describe());
			}
		});
	}

	/**
	 * A low melee mob never stands still for good under a constant gaze: after the hold limit it strikes. This is the
	 * stall guard of the melee kite.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void meleeKiteStrikesUnderConstantGaze(final GameTestHelper helper) {
		Scene scene = meleeScene(helper, "meleeKiteStrikesUnderConstantGaze", LOW_MOB, HIGH_PLAYER, MELEE_KITE_START, 0);
		Plan plan = new Plan(tick -> HIGH_PLAYER, false, tick -> Look.AT_MOB);
		helper.onEachTick(() -> {
			if (!scene.step(plan)) {
				return;
			}

			if (scene.mob.isWithinMeleeAttackRange(scene.player)) {
				scene.done = true;
				double held = scene.closestBetween(0, WATCH_TICKS);
				scene.result("struck heldUntilTick=" + WATCH_TICKS + " closestBeforeThen=" + number(held));
				helper.assertTrue(
					held >= KiteGoal.NEAR && held >= MELEE_KITE_START - HOLD_DRIFT,
					"the low mob closed to " + number(held) + " blocks before the hold ran out: " + scene.describe()
				);
				helper.assertTrue(scene.retreatTicks == 0, "the low mob turned its strike into a retreat: " + scene.describe());
				helper.assertTrue(
					scene.ticks >= WATCH_TICKS, "the low mob struck on tick " + scene.ticks + ", before the hold limit: " + scene.describe()
				);
				helper.succeed();
			} else if (scene.ticks >= KiteGoal.HOLD_TICKS + STRIKE_WINDOW) {
				scene.done = true;
				scene.result("stalled");
				helper.fail("the low mob never struck under a constant gaze: " + scene.describe());
			}
		});
	}

	/**
	 * The pursuit holds for its dwell. The player starts at 10 percent and turns to 90 percent a few ticks later. The rules
	 * would then ask for a fight, and the stance must still stay in the pursuit until the dwell has run out.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void pursueHoldsItsDwell(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene("pursueHoldsItsDwell");
		whenTicking(helper, spots(PURSUE_START), () -> {
			scene.player = survivor(helper, arena, CENTER, CENTER, LOW_PLAYER);
			scene.mob = spawnAt(arena, EntityTypes.STRAY, CENTER, CENTER - PURSUE_START, FULL_MOB, StanceGameTest::bow);
		});
		Plan plan = new Plan(tick -> tick < DWELL_FLIP_TICKS ? LOW_PLAYER : HIGH_PLAYER, false, tick -> Look.KEEP);
		helper.onEachTick(() -> {
			if (!scene.step(plan)) {
				return;
			}

			List<StanceSample> trace = scene.stanceTrace;
			int entered = firstWith(trace, 0, Stance.PURSUE, true);
			if (entered < 0) {
				helper.assertTrue(scene.mob.tickCount < DWELL_ENTRY_TICKS, "the stray never began to pursue the weak player: " + scene.describe());
				return;
			}

			int left = firstWith(trace, entered, Stance.PURSUE, false);
			int enteredTick = trace.get(entered).mobTick();
			if (left < 0 && scene.mob.tickCount < enteredTick + StanceGoal.DWELL_TICKS + 4 * StanceGoal.ASSESS_TICKS) {
				return;
			}

			scene.done = true;
			int held = left < 0 ? scene.mob.tickCount - enteredTick : trace.get(left).mobTick() - enteredTick;
			scene.result("enteredTick=" + enteredTick + " heldTicks=" + held + " left=" + (left >= 0));
			helper.assertTrue(left >= 0, "the stance never left the pursuit after the player turned healthy: " + scene.describe());
			helper.assertTrue(
				held >= StanceGoal.DWELL_TICKS, "the pursuit lasted only " + held + " ticks, under the dwell of " + StanceGoal.DWELL_TICKS + ": " + scene.describe()
			);
			helper.succeed();
		});
	}

	/**
	 * A retreat is followed by a kite. The player still chases when the run ends, and the rules would ask for another
	 * retreat at once. The mob keeps the KITE stance for the kite window instead.
	 */
	@GameTest(maxTicks = MAX_TICKS, padding = WideArena.PADDING)
	public void retreatEndsInKite(final GameTestHelper helper) {
		Scene scene = meleeScene(helper, "retreatEndsInKite", LOW_MOB, HIGH_PLAYER, START_GAP, 0);
		Plan plan = Plan.steady(HIGH_PLAYER, true);
		helper.onEachTick(() -> {
			if (!scene.step(plan)) {
				return;
			}

			List<StanceSample> trace = scene.stanceTrace;
			int retreat = firstWith(trace, 0, Stance.RETREAT, true);
			int kite = retreat < 0 ? -1 : firstWith(trace, retreat, Stance.KITE, true);
			if (kite < 0) {
				helper.assertTrue(scene.ticks < RETREAT_WINDOW, "the retreat never ended in a kite: " + scene.describe());
				return;
			}

			int kiteTick = trace.get(kite).mobTick();
			boolean backToRetreat = firstWith(trace, kite, Stance.RETREAT, true) >= 0;
			helper.assertFalse(backToRetreat, "the stance went back to RETREAT inside the kite window: " + scene.describe());
			if (scene.mob.tickCount < kiteTick + StanceGoal.KITE_TICKS) {
				return;
			}

			scene.done = true;
			scene.result("kited kiteTick=" + kiteTick);
			helper.succeed();
		});
	}

	/** The retreat cases: the gap grows by `minGain`. */
	private static void retreats(final GameTestHelper helper, final Scene scene, final double minGain) {
		Plan plan = Plan.steady(HIGH_PLAYER, true);
		helper.onEachTick(() -> {
			if (!scene.step(plan)) {
				return;
			}

			if (scene.maxGain >= minGain) {
				scene.done = true;
				scene.result("retreated");
				helper.succeed();
			} else if (scene.ticks >= RETREAT_WINDOW) {
				scene.done = true;
				scene.result("stayed");
				helper.fail("the low mob did not retreat " + minGain + " blocks from a chaser in " + RETREAT_WINDOW + " ticks: " + scene.describe());
			}
		});
	}

	/** The fight cases: no retreat, no more than a block. */
	private static void holdsItsGround(final GameTestHelper helper, final Scene scene, final Plan plan) {
		helper.onEachTick(() -> {
			if (!scene.step(plan) || scene.ticks < FIGHT_WINDOW) {
				return;
			}

			scene.done = true;
			scene.result("fought");
			helper.assertTrue(scene.retreatTicks == 0, "the mob ran a retreat against a fight: " + scene.describe());
			helper.assertTrue(
				scene.maxGain <= MAX_FIGHT_GAIN, "the mob gave more than " + MAX_FIGHT_GAIN + " block: " + scene.describe()
			);
			helper.succeed();
		});
	}

	/** A vindicator, a survival player, and allies. */
	private static Scene meleeScene(
		final GameTestHelper helper, final String name, final double mobShare, final double playerShare, final double gap, final int allies
	) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		Scene scene = new Scene(name);
		List<Vec3> ready = new ArrayList<>(spots(gap));
		for (int index = 0; index < allies; index++) {
			ready.add(new Vec3(CENTER + ALLY_OFFSETS[index][0], 1.0, CENTER + gap + ALLY_OFFSETS[index][1]));
		}

		whenTicking(helper, ready, () -> {
			scene.player = survivor(helper, arena, CENTER, CENTER, playerShare);
			scene.mob = spawnAt(arena, EntityTypes.VINDICATOR, CENTER, CENTER + gap, mobShare, mob -> { });
			for (int index = 0; index < allies; index++) {
				double[] offset = ALLY_OFFSETS[index];
				scene.hunters.add(idle(arena.spawn(EntityTypes.ZOMBIE, CENTER + offset[0], CENTER + gap + offset[1], mob -> { })));
			}
		});
		return scene;
	}

	/** A survival player at its share. Not a target yet. */
	private static ServerPlayer survivor(
		final GameTestHelper helper, final WideArena arena, final double x, final double z, final double share
	) {
		ServerPlayer player = arena.survivalPlayer(x, z);
		player.setHealth((float) (share * player.getMaxHealth()));
		// The player stays in the server.
		// A hurt one would draw a later case's mobs.
		helper.runBeforeTestEnd(() -> player.setHealth(player.getMaxHealth()));
		// No mob may target the player while it falls.
		// A goal that starts in the air wastes a window.
		player.setPermanentlyInvulnerable(true);
		return player;
	}

	private static <T extends Mob> T spawnAt(
		final WideArena arena, final EntityType<T> type, final double x, final double z, final double share, final Consumer<T> gear
	) {
		T mob = arena.spawn(type, x, z, gear);
		mob.setHealth((float) (mob.getMaxHealth() * share));
		return mob;
	}

	private static <M extends Mob> M idle(final M mob) {
		mob.setNoAi(true);
		return mob;
	}

	private static void sword(final Mob mob) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	private static void bow(final Mob mob) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	/**
	 * Runs `populate` on the first tick when every spot stands in a chunk that ticks entities, and fails the case when that
	 * takes longer than {@link #READY_TICKS}. Register it from the test body: a tick hook added from a delayed callback
	 * corrupts the framework's tick table.
	 */
	static void whenTicking(final GameTestHelper helper, final List<Vec3> relativeSpots, final Runnable populate) {
		boolean[] done = new boolean[1];
		int[] waited = new int[1];
		helper.onEachTick(() -> {
			if (done[0]) {
				return;
			}

			ServerLevel level = helper.getLevel();
			boolean ready = relativeSpots.stream().allMatch(spot -> level.isPositionEntityTicking(helper.absolutePos(BlockPos.containing(spot))));
			if (!ready) {
				if (++waited[0] > READY_TICKS) {
					helper.fail("the arena's chunks never ticked entities in " + READY_TICKS + " ticks");
				}

				return;
			}

			done[0] = true;
			try {
				populate.run();
			} catch (RuntimeException failure) {
				// A plain exception stops the server.
				// A test failure stops only this case.
				helper.fail("the arena could not be set up: " + failure);
			}
		});
	}

	/** The spots on the line through the player. */
	private static List<Vec3> spots(final double gap) {
		return List.of(new Vec3(CENTER, 1.0, CENTER), new Vec3(CENTER, 1.0, CENTER + gap), new Vec3(CENTER, 1.0, CENTER - gap));
	}

	private static Vec3 chaseMovement(final ServerPlayer player, final Mob mob) {
		Vec3 toMob = flat(mob.position().subtract(player.position()));
		return toMob.length() < 1.0E-6 ? Vec3.ZERO : toMob.normalize().scale(CHASE_STEP);
	}

	/** The first index from `from` with, or without, the stance. */
	private static int firstWith(final List<StanceSample> trace, final int from, final Stance stance, final boolean equal) {
		for (int index = from; index < trace.size(); index++) {
			if ((trace.get(index).stance() == stance) == equal) {
				return index;
			}
		}

		return -1;
	}

	private static Vec3 flat(final Vec3 vector) {
		return new Vec3(vector.x, 0.0, vector.z);
	}

	private static Vec3 centroid(final List<Mob> mobs) {
		Vec3 sum = Vec3.ZERO;
		for (Mob mob : mobs) {
			sum = sum.add(mob.position());
		}

		return sum.scale(1.0 / mobs.size());
	}

	private static boolean running(final Mob mob, final Class<? extends Goal> type) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (type.isInstance(wrapped.getGoal()) && wrapped.isRunning()) {
				return true;
			}
		}

		return false;
	}

	private static String runningGoals(final Mob mob) {
		StringBuilder names = new StringBuilder();
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.isRunning()) {
				names.append(wrapped.getGoal().getClass().getSimpleName()).append(':').append(wrapped.getPriority()).append(' ');
			}
		}

		return names.length() == 0 ? "none" : names.toString().trim();
	}

	private static String number(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
