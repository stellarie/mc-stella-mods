package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Follows one specific mob. It is installed on the follower's own selector, it holds the mob it follows
 * by identity rather than by class, and it drives the follower's own navigation with moveTo, so any
 * navigation type carries it: an amphibious or a water bound follower is covered by construction.
 */
public class FollowAllyGoal extends Goal {
	private static final int RECALC_TICKS = 10;

	private final Mob follower;
	private final Mob leader;
	private final double speedModifier;
	private final float stopDistance;
	private final float areaSize;
	private int timeToRecalcPath;
	private float oldWaterCost;

	public FollowAllyGoal(
		final Mob follower, final Mob leader, final double speedModifier, final float stopDistance, final float areaSize
	) {
		this.follower = follower;
		this.leader = leader;
		this.speedModifier = speedModifier;
		this.stopDistance = stopDistance;
		this.areaSize = areaSize;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public Mob leader() {
		return this.leader;
	}

	@Override
	public boolean canUse() {
		if (!this.isLeaderThere()) {
			return false;
		}

		double distanceSqr = this.follower.distanceToSqr(this.leader);
		// It takes up the chase from inside the area and keeps it until it is close, so a follower that
		// has fallen behind still catches up instead of wandering where it stands.
		return distanceSqr > (double) (this.stopDistance * this.stopDistance)
			&& distanceSqr <= (double) (this.areaSize * this.areaSize);
	}

	@Override
	public boolean canContinueToUse() {
		return this.isLeaderThere()
			&& this.follower.distanceToSqr(this.leader) > (double) (this.stopDistance * this.stopDistance);
	}

	@Override
	public void start() {
		this.timeToRecalcPath = 0;
		this.oldWaterCost = this.follower.getPathfindingMalus(PathType.WATER);
		this.follower.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	@Override
	public void stop() {
		this.follower.getNavigation().stop();
		this.follower.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
	}

	@Override
	public void tick() {
		this.follower.getLookControl().setLookAt(this.leader, 10.0F, this.follower.getMaxHeadXRot());

		if (--this.timeToRecalcPath > 0) {
			return;
		}

		this.timeToRecalcPath = this.adjustedTickDelay(RECALC_TICKS);

		if (this.follower.distanceToSqr(this.leader) > (double) (this.stopDistance * this.stopDistance)) {
			this.follower.getNavigation().moveTo(this.leader, this.speedModifier);
		} else {
			this.follower.getNavigation().stop();
		}
	}

	private boolean isLeaderThere() {
		return this.leader.isAlive() && !this.leader.isRemoved();
	}
}
