package com.stella.villages.patrol;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;

final class PatrolFollowGoal extends Goal {
	private final Mob follower;
	private final UUID villageId;
	private final double speed;
	private Mob head;

	PatrolFollowGoal(final Mob follower, final UUID villageId, final double speed) {
		this.follower = follower;
		this.villageId = villageId;
		this.speed = speed;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (this.follower.isPassenger() || !this.follower.isAlive()) {
			return false;
		}

		this.head = PatrolService.headFor(this.follower.level(), this.villageId);
		return this.head != null && this.follower.distanceToSqr(this.head)
			> PatrolService.PATROL_FOLLOW_DISTANCE * PatrolService.PATROL_FOLLOW_DISTANCE;
	}

	@Override
	public boolean canContinueToUse() {
		return this.head != null && this.head.isAlive() && !this.follower.isPassenger()
			&& this.follower.distanceToSqr(this.head)
			> PatrolService.PATROL_FOLLOW_DISTANCE * PatrolService.PATROL_FOLLOW_DISTANCE;
	}

	@Override
	public void start() {
		moveToHead();
	}

	@Override
	public void tick() {
		if (this.follower.tickCount % 10 == 0) {
			moveToHead();
		}
	}

	@Override
	public void stop() {
		this.head = null;
		this.follower.getNavigation().stop();
	}

	private void moveToHead() {
		if (this.head != null) {
			this.follower.getLookControl().setLookAt(this.head);
			this.follower.getNavigation().moveTo(this.head, this.speed);
		}
	}
}
