package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A clock and an edge. The goal claims MOVE and LOOK while a ranged unit's target is closer than its near
 * band or beyond its far band. It walks away from a close target and toward a far one. It releases when the
 * window expires. An approach also ends the moment the target is inside the far edge, because the weapon goal
 * cannot fire while this goal holds MOVE and LOOK. A retreat measures no progress.
 *
 * <p>The edges come from {@link StanceBands}, and a RETREAT stance suppresses the goal. In KITE the unit moves for 30
 * ticks and rests for 30, so its weapon goal fires in the rests.
 */
public class KeepDistanceGoal<T extends PathfinderMob> extends Goal {
	public static final int WINDOW_TICKS = 60;
	/** KITE: move 30 ticks, then rest 30. */
	public static final int KITE_WINDOW_TICKS = 30;
	// The rearm budget drops once per two ticks.
	public static final int KITE_COOLDOWN_TICKS = 15;

	private static final double SPEED_MODIFIER = 1.2;
	private static final double ENTRY_DEAD_ZONE = 1.0;
	private static final int COOLDOWN_TICKS = 40;
	private static final int REPATH_TICKS = 20;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final T mob;
	private int ticksRunning;
	private int ticksToRepath;
	private boolean retreating;

	public KeepDistanceGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public boolean isRetreatExhausted() {
		return ReactiveState.get(this.mob).isRetreatCoolingDown();
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

			// Nowhere left to go: release MOVE so the ranged goal can fire. The spec is that a unit which
			// cannot get away fights instead of standing still, and holding the flags is what froze it.
			if (FleePositions.away(this.mob, target.position()) == null) {
				return false;
			}
		}

		return true;
	}

	@Override
	public void start() {
		this.ticksRunning = 0;
		this.ticksToRepath = 0;
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		ReactiveState state = ReactiveState.get(this.mob);
		state.armRetreatCooldown(this.isKiting() ? KITE_COOLDOWN_TICKS : COOLDOWN_TICKS);
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

		if (--this.ticksToRepath > 0) {
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

		Vec3 away = FleePositions.away(this.mob, target.position());
		if (away == null) {
			this.mob.getNavigation().stop();
			return;
		}

		this.mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED_MODIFIER);
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
