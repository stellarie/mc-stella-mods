package com.stella.spells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Whether a spot can hold a body standing on it. */
final class CastingSpots {
	private CastingSpots() {
	}

	static boolean standing(final ServerLevel level, final Vec3 at) {
		BlockPos feet = BlockPos.containing(at);
		return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}
}
