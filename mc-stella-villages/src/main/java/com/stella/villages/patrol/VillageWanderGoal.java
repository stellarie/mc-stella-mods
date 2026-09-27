package com.stella.villages.patrol;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A patrol member walks its beat: now and then it strolls to a random spot within its radius of the
 * village's centre, and when it finds itself outside that radius it heads straight back. The head's beat
 * is the few blocks around the bell; every other member's is the whole village.
 */
final class VillageWanderGoal extends Goal {
	/** One chance in this many, each tick, that an idle member sets off for a new spot. */
	private static final int STROLL_CHANCE = 60;
	private final Mob mob;
	private final BlockPos center;
	private final int radius;
	private final double speed;
	private BlockPos destination;

	VillageWanderGoal(final Mob mob, final BlockPos center, final int radius, final double speed) {
		this.mob = mob;
		this.center = center.immutable();
		this.radius = radius;
		this.speed = speed;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	BlockPos center() {
		return this.center;
	}

	int radius() {
		return this.radius;
	}

	@Override
	public boolean canUse() {
		if (this.mob.isPassenger() || this.mob.getTarget() != null) {
			return false;
		}
		if (outside()) {
			this.destination = this.center.above();
			return true;
		}
		if (this.mob.getRandom().nextInt(STROLL_CHANCE) != 0) {
			return false;
		}
		for (int attempt = 0; attempt < 12; attempt++) {
			int x = this.center.getX() + this.mob.getRandom().nextInt(2 * this.radius + 1) - this.radius;
			int z = this.center.getZ() + this.mob.getRandom().nextInt(2 * this.radius + 1) - this.radius;
			BlockPos column = new BlockPos(x, this.center.getY(), z);
			if (!this.mob.level().hasChunkAt(column)) {
				continue;
			}
			BlockPos surface = new BlockPos(x, this.mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Vec3.atCenterOf(surface).distanceToSqr(Vec3.atCenterOf(this.center)) <= (double) this.radius * this.radius) {
				this.destination = surface;
				return true;
			}
		}
		return false;
	}

	private boolean outside() {
		return this.mob.position().distanceToSqr(Vec3.atCenterOf(this.center)) > (double) this.radius * this.radius;
	}

	@Override
	public boolean canContinueToUse() {
		return this.destination != null && !this.mob.getNavigation().isDone() && this.mob.getTarget() == null;
	}

	@Override
	public void start() {
		if (this.destination != null) {
			this.mob.getNavigation().moveTo(this.destination.getX() + 0.5, this.destination.getY(), this.destination.getZ() + 0.5, this.speed);
		}
	}

	@Override
	public void stop() {
		this.destination = null;
	}
}
