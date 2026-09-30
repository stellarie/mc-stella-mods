package com.stella.tower.test.mixin;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Test harness only. The game test server bakes its world from an empty dimension registry, so a mod's
 * datapack dimension never loads there; this hands it the datapack's dimensions, as a real server does.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestDimensionsMixin {
	@ModifyVariable(method = "lambda$create$1", at = @At("STORE"), ordinal = 0)
	private static Registry<LevelStem> stellaTower$keepDatapackDimensions(
		final Registry<LevelStem> empty, final LevelSettings settings, final WorldLoader.DataLoadContext context
	) {
		return context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
	}
}
