package com.stella.mobs;

import com.stella.mobs.api.ContainerLevelProvider;
import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import com.stella.mobs.scaling.AltitudeLevels;
import com.stella.mobs.scaling.LevelFloor;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The container level share: published, and the mob floor plus altitude, never below the dimension minimum. */
public final class ContainerLevelGameTest {
	@GameTest
	@SuppressWarnings("unchecked")
	public void published(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(ContainerLevelProvider.OBJECT_SHARE_KEY);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		ServerLevel level = helper.getLevel();
		ScalingConfig config = ScalingConfigStore.get();
		int expected = Math.max(0, LevelFloor.floorOf(at, level.players(), config) + AltitudeLevels.levels(level.dimension(), at.y, config));
		int value = shared instanceof BiFunction<?, ?, ?> function ? ((BiFunction<ServerLevel, Vec3, Integer>) function).apply(level, at) : -1;
		StellaMobs.LOGGER.info("[BEHAVIOR] test=published RESULT value={} expected={}", value, expected);
		helper.assertTrue(value == expected, "the share gave " + value + ", want " + expected);
		helper.succeed();
	}

	@GameTest
	public void formula(final GameTestHelper helper) {
		ScalingConfig config = ScalingConfigStore.get();
		int deep = ContainerLevelProvider.compute(Level.OVERWORLD, -64.0, 28, config);
		int deepExpected = 28 + AltitudeLevels.levels(Level.OVERWORLD, -64.0, config);
		int surface = ContainerLevelProvider.compute(Level.OVERWORLD, 0.0, 0, config);
		int nether = ContainerLevelProvider.compute(Level.NETHER, 70.0, 3, config);
		int end = ContainerLevelProvider.compute(Level.END, 70.0, 100, config);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=formula RESULT deep={} deepExpected={} surface={} nether={} end={}",
			deep, deepExpected, surface, nether, end);
		helper.assertTrue(deep == deepExpected && deepExpected > 28, "depth did not add levels: " + deep);
		helper.assertTrue(surface == 0, "an empty surface spot gave " + surface);
		helper.assertTrue(nether == 50 && end == 100, "dimension minimum wrong: nether=" + nether + " end=" + end);
		helper.succeed();
	}
}
