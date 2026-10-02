package com.stella.mobs.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Holds a ranged unit at its firing band while it sees its target. The goal carries no flags: it stops the
 * navigation only, so the bow or the spell goal stays free to fire, and the mob cannot walk in. A unit that
 * cannot see its target is not held, and its weapon goal walks it to a spot with a clear line. A unit that walks to cover or
 * waits there is not held either, so the hide walk is not stopped.
 */
public class HoldStandoffGoal<T extends PathfinderMob> extends Goal {
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final T mob;

	public HoldStandoffGoal(final T mob) {
		this.mob = mob;
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.mob);
		if (!state.playerInRange(this.mob)) {
			return false;
		}

		if (Stance.suppresses(this.mob)) {
			return false;
		}

		// The hide walk starts inside the band.
		// This goal would stop it on every tick.
		if (AmbushGoal.repositioning(this.mob)) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		double distance = this.mob.distanceTo(target);
		return distance >= StanceBands.near(this.mob, state.getNearDistance())
			&& distance <= StanceBands.far(this.mob, state.getFarDistance())
			&& this.mob.getSensing().hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		this.mob.getNavigation().stop();
		LivingEntity target = this.mob.getTarget();

		if (target != null) {
			this.mob.getLookControl().setLookAt(target, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		}
	}
}
