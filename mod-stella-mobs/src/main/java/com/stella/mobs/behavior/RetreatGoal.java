package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RetreatGoal<T extends PathfinderMob> extends Goal {
	public static final double HEALTH_FRACTION = 0.35;
	private static final int DURATION_TICKS = 100;
	private static final int REARM_TICKS = 100;
	private static final double SPEED_MODIFIER = 1.2;
	private static final double MINIMUM_AWAY_DISTANCE = 1.0;
	private static final double MAXIMUM_LEVEL_DIFFERENCE = 1.0;
	private static final int[][] FLEE_DISTANCES = {{16, 7}, {8, 4}, {4, 2}};

	private final T mob;
	private @Nullable Vec3 awayPos;
	private long endTick;

	public RetreatGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.mob);
		if (!state.playerInRange(this.mob)) {
			return false;
		}

		if (this.mob.level().getGameTime() < state.getRetreatReadyTick()) {
			return false;
		}

		if (this.mob.getHealth() > this.mob.getMaxHealth() * HEALTH_FRACTION) {
			return false;
		}

		if (!this.mob.onGround() && !this.mob.isInLiquid() && !this.mob.isPassenger()) {
			return false;
		}

		LivingEntity attacker = this.mob.getTarget();
		if (attacker == null || !attacker.isAlive()) {
			return false;
		}

		Vec3 away = this.findAwayPos(attacker);
		if (away == null) {
			return false;
		}

		this.awayPos = away;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.mob.level().getGameTime() < this.endTick && !this.mob.getNavigation().isDone();
	}

	@Override
	public void start() {
		this.endTick = this.mob.level().getGameTime() + DURATION_TICKS;
		Vec3 away = this.awayPos;
		if (away != null) {
			this.mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED_MODIFIER);
		}
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		this.awayPos = null;
		ReactiveState state = ReactiveState.get(this.mob);
		state.setRetreatReadyTick(this.mob.level().getGameTime() + REARM_TICKS);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	private @Nullable Vec3 findAwayPos(final LivingEntity attacker) {
		Vec3 attackerPosition = attacker.position();
		double currentDistance = this.mob.position().distanceTo(attackerPosition);

		for (int[] distance : FLEE_DISTANCES) {
			Vec3 candidate = LandRandomPos.getPosAway(this.mob, distance[0], distance[1], attackerPosition);
			if (candidate != null
				&& Math.abs(candidate.y - this.mob.getY()) <= MAXIMUM_LEVEL_DIFFERENCE
				&& candidate.distanceTo(attackerPosition) > currentDistance + MINIMUM_AWAY_DISTANCE) {
				return candidate;
			}
		}

		return null;
	}
}
