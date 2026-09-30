package com.stella.mobs.scaling;

import com.stella.mobs.config.ScalingConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Levels an Overworld mob gains from its height: more per block below y=0 than above it. No cap. */
public final class AltitudeLevels {
	private AltitudeLevels() {
	}

	/** Absorbs rounding such as 64 / 6.4 = 9.999... */
	private static final double EPSILON = 1.0E-9;

	public static int levels(final ResourceKey<Level> dimension, final double y, final ScalingConfig config) {
		if (!Level.OVERWORLD.equals(dimension)) {
			return 0;
		}
		if (y < 0.0) {
			return (int) Math.floor(-y / config.depthBlocksPerLevel() + EPSILON);
		}
		return (int) Math.floor(y / config.heightBlocksPerLevel() + EPSILON);
	}
}
