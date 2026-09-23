package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A clock, not a measurement. The goal claims MOVE and LOOK while a ranged unit's target is closer than its
 * near band, walks away from the target, and releases when the target is at or beyond the band or when the
 * window expires. The window is the only lifetime; nothing measures progress.
 */
public class KeepDistanceGoal<T extends PathfinderMob> extends Goal {
	public static final int WINDOW_TICKS = 60;

	private static final double SPEED_MODIFIER = 1.2;
	private static final double ENTRY_DEAD_ZONE = 1.0;
	private static final int COOLDOWN_TICKS = 40;
	private static final int REPATH_TICKS = 20;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final T mob;
	private long endTick;
	private long nextRepathTick;
	private boolean retreating;

	public KeepDistanceGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public boolean isRetreatExhausted() {
		return this.mob.level().getGameTime() < ReactiveState.get(this.mob).getKeepDistanceReadyTick();
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

		if (this.mob.getHealth() <= this.mob.getMaxHealth() * RetreatGoal.HEALTH_FRACTION) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		double distance = this.mob.distanceTo(target);

		if (distance < state.getNearDistance()) {
			this.retreating = true;
			return true;
		}

		if (distance > state.getFarDistance() + ENTRY_DEAD_ZONE) {
			this.retreating = false;
			return true;
		}

		return false;
	}

	@Override
	public boolean canContinueToUse() {
		if (this.mob.level().getGameTime() >= this.endTick) {
			return false;
		}

		if (this.mob.getHealth() <= this.mob.getMaxHealth() * RetreatGoal.HEALTH_FRACTION) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		ReactiveState state = ReactiveState.get(this.mob);

		if (this.retreating && this.mob.distanceTo(target) >= state.getNearDistance()) {
			return false;
		}

		return true;
	}

	@Override
	public void start() {
		this.endTick = this.mob.level().getGameTime() + WINDOW_TICKS;
		this.nextRepathTick = this.mob.level().getGameTime();
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		ReactiveState state = ReactiveState.get(this.mob);
		state.setKeepDistanceReadyTick(this.mob.level().getGameTime() + COOLDOWN_TICKS);
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

		long now = this.mob.level().getGameTime();
		if (now < this.nextRepathTick) {
			return;
		}

		this.nextRepathTick = now + REPATH_TICKS;

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

	private boolean isAirborne() {
		return !this.mob.onGround() && !this.mob.isInLiquid() && !this.mob.isPassenger();
	}
}
