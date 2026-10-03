package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A clock and an edge. The goal claims MOVE and LOOK while a ranged unit's target is closer than its near
 * band or beyond its far band. It walks away from a close target and toward a far one. It releases when the
 * window expires. An approach also ends the moment the target is inside the far edge, because the weapon goal
 * cannot fire while this goal holds MOVE and LOOK. A retreat ends at the near edge.
 *
 * <p>A retreat walks a path that reaches its end. A unit is cornered when no such path exists, or when it moves less than
 * {@link #PROGRESS_BLOCKS} in {@link #PROGRESS_TICKS}. A cornered unit stops and rests for {@link #CORNERED_REST_TICKS},
 * so its weapon goal fires instead of the unit standing still. A pursuer that keeps pace does not corner it.
 *
 * <p>An expired window rests the goal for {@link #REST_TICKS}, so the weapon goal fires in the rest. A goal that reaches
 * the band does not rest: a pursuer that presses in again meets a new retreat. The edges come from {@link StanceBands},
 * and a RETREAT stance suppresses the goal. In KITE the unit moves for 30 ticks and rests for 30. Every rest counts the
 * mob's own ticks.
 */
public class KeepDistanceGoal<T extends PathfinderMob> extends Goal {
	public static final int WINDOW_TICKS = 60;
	/** KITE: move 30 ticks, then rest 30. */
	public static final int KITE_WINDOW_TICKS = 30;
	public static final int KITE_REST_TICKS = 30;
	/** Longer than one bow draw, so a shot fits in. */
	public static final int REST_TICKS = 40;
	public static final int CORNERED_REST_TICKS = 60;
	public static final int PROGRESS_TICKS = 20;
	public static final double PROGRESS_BLOCKS = 0.5;

	private static final double SPEED_MODIFIER = 1.2;
	private static final double ENTRY_DEAD_ZONE = 1.0;
	private static final int REPATH_TICKS = 20;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final T mob;
	private int ticksRunning;
	private int ticksToRepath;
	private boolean retreating;
	private boolean cornered;
	private int progressTick;
	private @Nullable Vec3 progressFrom;

	public KeepDistanceGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public boolean isRetreatExhausted() {
		return ReactiveState.get(this.mob).isRetreatCoolingDown(this.mob);
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.mob);
		if (!state.playerInRange(this.mob)) {
			return false;
		}

		if (this.isRetreatExhausted()) {
			return false;
		}

		if (Stance.suppresses(this.mob)) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		double distance = this.mob.distanceTo(target);

		if (distance < StanceBands.near(this.mob, state.getNearDistance())) {
			this.retreating = true;
			return true;
		}

		if (distance > StanceBands.far(this.mob, state.getFarDistance()) + ENTRY_DEAD_ZONE) {
			this.retreating = false;
			return true;
		}

		return false;
	}

	@Override
	public boolean canContinueToUse() {
		// The window counts the goal's own ticks, not the level clock: a goal ticks once per game tick, so
		// the two agree in play, and this one still ends where a stopped clock would freeze it.
		if (this.ticksRunning >= this.windowTicks()) {
			return false;
		}

		if (Stance.suppresses(this.mob)) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		ReactiveState state = ReactiveState.get(this.mob);

		if (!this.retreating && this.mob.distanceTo(target) <= StanceBands.far(this.mob, state.getFarDistance())) {
			return false;
		}

		if (this.retreating) {
			if (this.mob.distanceTo(target) >= StanceBands.near(this.mob, state.getNearDistance())) {
				return false;
			}

			// Nowhere to go: the weapon goal fights.
			if (this.cornered) {
				return false;
			}
		}

		return true;
	}

	@Override
	public void start() {
		this.ticksRunning = 0;
		this.ticksToRepath = 0;
		this.cornered = false;
		this.progressFrom = null;
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		int rest = this.restTicks();
		if (rest > 0) {
			ReactiveState.get(this.mob).armRetreatCooldown(this.mob, rest);
		}
	}

	/** No rest when the goal reached its band. */
	private int restTicks() {
		if (this.cornered) {
			return CORNERED_REST_TICKS;
		}

		if (this.ticksRunning < this.windowTicks()) {
			return 0;
		}

		return this.isKiting() ? KITE_REST_TICKS : REST_TICKS;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();

		if (target != null) {
			this.mob.getLookControl().setLookAt(target, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		}

		this.ticksRunning++;
		if (this.retreating && this.stuck()) {
			this.cornered = true;
			this.mob.getNavigation().stop();
			return;
		}

		// A short flee path ends before the timer.
		if (--this.ticksToRepath > 0 && !(this.retreating && this.mob.getNavigation().isDone())) {
			return;
		}

		this.ticksToRepath = REPATH_TICKS;

		if (this.isAirborne() || target == null) {
			this.mob.getNavigation().stop();
			return;
		}

		if (!this.retreating) {
			this.mob.getNavigation().moveTo(target, SPEED_MODIFIER);
			return;
		}

		Path away = FleePositions.reachableAway(this.mob, target.position());
		if (away == null) {
			this.cornered = true;
			this.mob.getNavigation().stop();
			return;
		}

		this.mob.getNavigation().moveTo(away, SPEED_MODIFIER);
	}

	/** A path can exist that the unit cannot walk. A hovering unit is stuck too. */
	private boolean stuck() {
		Vec3 now = this.mob.position();
		if (this.progressFrom == null) {
			this.progressTick = this.mob.tickCount + PROGRESS_TICKS;
			this.progressFrom = now;
			return false;
		}

		if (this.mob.tickCount < this.progressTick) {
			return false;
		}

		if (now.distanceTo(this.progressFrom) < PROGRESS_BLOCKS) {
			return true;
		}

		this.progressTick = this.mob.tickCount + PROGRESS_TICKS;
		this.progressFrom = now;
		return false;
	}

	private int windowTicks() {
		return this.isKiting() ? KITE_WINDOW_TICKS : WINDOW_TICKS;
	}

	private boolean isKiting() {
		return Stance.of(this.mob) == Stance.KITE;
	}

	private boolean isAirborne() {
		return !this.mob.onGround() && !this.mob.isInLiquid() && !this.mob.isPassenger();
	}
}
