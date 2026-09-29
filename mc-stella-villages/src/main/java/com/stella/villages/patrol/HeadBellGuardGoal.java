package com.stella.villages.patrol;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Keeps the village head and its fights inside eight blocks of the bell. */
final class HeadBellGuardGoal extends Goal {
	private final Mob head;
	private final BlockPos bell;

	HeadBellGuardGoal(final Mob head, final BlockPos bell) {
		this.head = head;
		this.bell = bell.immutable();
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return outside(this.head.position()) || this.head.getTarget() != null && outside(this.head.getTarget().position());
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		this.head.setTarget(null);
		this.head.getNavigation().stop();
		if (outside(this.head.position()) && this.head.level() instanceof ServerLevel level) {
			BlockPos feet = BellRally.safeSpot(level, this.bell, this.head);
			if (feet != null) {
				Vec3 at = Vec3.atBottomCenterOf(feet);
				this.head.teleportTo(at.x, at.y, at.z);
			}
		}
	}

	private boolean outside(final Vec3 position) {
		return position.distanceToSqr(Vec3.atCenterOf(this.bell))
			> PatrolService.HEAD_BELL_RADIUS * PatrolService.HEAD_BELL_RADIUS;
	}
}
