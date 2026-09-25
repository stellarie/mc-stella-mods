package com.stella.villages.patrol;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

final class VillageWanderGoal extends Goal {
	private final Mob head;
	private final BlockPos anchor;
	private final double speed;
	private BlockPos destination;

	VillageWanderGoal(final Mob head, final BlockPos anchor, final double speed) {
		this.head = head;
		this.anchor = anchor.immutable();
		this.speed = speed;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (this.head.position().distanceToSqr(Vec3.atCenterOf(this.anchor)) > (double) PatrolService.PATROL_RADIUS * PatrolService.PATROL_RADIUS) {
			this.destination = this.anchor.above();
			return true;
		}

		if (this.head.getRandom().nextInt(80) != 0) {
			return false;
		}

		int minX = SectionPos.blockToSectionCoord(this.anchor.getX()) * 16;
		int minZ = SectionPos.blockToSectionCoord(this.anchor.getZ()) * 16;
		int maxX = minX + 15;
		int maxZ = minZ + 15;
		for (int attempt = 0; attempt < 12; attempt++) {
			int x = Math.max(minX, Math.min(maxX, this.anchor.getX() + this.head.getRandom().nextInt(17) - 8));
			int z = Math.max(minZ, Math.min(maxZ, this.anchor.getZ() + this.head.getRandom().nextInt(17) - 8));
			int y = this.head.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos surface = new BlockPos(x, y, z);
			if (Vec3.atCenterOf(surface).distanceToSqr(Vec3.atCenterOf(this.anchor)) <= (double) PatrolService.PATROL_RADIUS * PatrolService.PATROL_RADIUS
				&& this.head.level().hasChunkAt(surface)) {
				this.destination = surface;
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return this.destination != null && !this.head.getNavigation().isDone();
	}

	@Override
	public void start() {
		if (this.destination != null) {
			this.head.getNavigation().moveTo(
				this.destination.getX() + 0.5,
				this.destination.getY(),
				this.destination.getZ() + 0.5,
				this.speed
			);
		}
	}

	@Override
	public void stop() {
		this.destination = null;
	}
}
