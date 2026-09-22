package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class KeepDistanceGoal<T extends PathfinderMob> extends Goal {
	private static final double SPEED_MODIFIER = 1.2;
	private static final double ENTRY_DEAD_ZONE = 1.0;
	private static final int REPOSITION_TICKS = 60;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final T mob;
	private @Nullable Vec3 awayPos;
	private long endTick;

	public KeepDistanceGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.mob);
		if (!state.playerInRange(this.mob)) {
			return false;
		}

		if (this.mob.getHealth() <= this.mob.getMaxHealth() * RetreatGoal.HEALTH_FRACTION) {
			return false;
		}

		if (!this.mob.onGround() && !this.mob.isInLiquid() && !this.mob.isPassenger()) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		double distance = this.mob.distanceTo(target);
		if (distance < state.getNearDistance() - ENTRY_DEAD_ZONE) {
			Vec3 away = FleePositions.away(this.mob, target.position());
			if (away == null) {
				return false;
			}

			this.awayPos = away;
			return true;
		}

		if (distance > state.getFarDistance() + ENTRY_DEAD_ZONE) {
			this.awayPos = null;
			return true;
		}

		return false;
	}

	@Override
	public boolean canContinueToUse() {
		if (this.mob.level().getGameTime() >= this.endTick) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		ReactiveState state = ReactiveState.get(this.mob);
		double distance = this.mob.distanceTo(target);
		return distance < state.getNearDistance() || distance > state.getFarDistance();
	}

	@Override
	public void start() {
		this.endTick = this.mob.level().getGameTime() + REPOSITION_TICKS;
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}

		Vec3 away = this.awayPos;
		if (away != null) {
			this.mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED_MODIFIER);
		} else {
			this.mob.getNavigation().moveTo(target, SPEED_MODIFIER);
		}
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		this.awayPos = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target != null) {
			this.mob.getLookControl().setLookAt(target, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		}
	}
}
