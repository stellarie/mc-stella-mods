package com.stella.mobs.api;

import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import com.stella.mobs.scaling.AltitudeLevels;
import com.stella.mobs.scaling.LevelFloor;
import com.stella.mobs.scaling.ThreatState;
import java.util.function.BiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Publishes the level of a spot for container loot under {@code mod-stella-mobs:container-level} as a
 * {@code BiFunction<ServerLevel, Vec3, Integer>}. It is the mob floor plus the altitude levels, never below the
 * dimension minimum. It has no random variation, so a container keeps one level.
 */
public final class ContainerLevelProvider {
	public static final String OBJECT_SHARE_KEY = "mod-stella-mobs:container-level";

	private ContainerLevelProvider() {
	}

	public static void register() {
		BiFunction<ServerLevel, Vec3, Integer> provider = ContainerLevelProvider::level;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, provider);
	}

	public static int level(final ServerLevel level, final Vec3 at) {
		ScalingConfig config = ScalingConfigStore.get();
		return compute(level.dimension(), at.y, LevelFloor.floorOf(at, level.players(), config), config);
	}

	public static int compute(final ResourceKey<Level> dimension, final double y, final int floor, final ScalingConfig config) {
		int minimum = dimension.equals(Level.NETHER) ? ThreatState.NETHER_MINIMUM
			: dimension.equals(Level.END) ? ThreatState.END_MINIMUM : 0;
		return Math.max(minimum, floor + AltitudeLevels.levels(dimension, y, config));
	}
}
