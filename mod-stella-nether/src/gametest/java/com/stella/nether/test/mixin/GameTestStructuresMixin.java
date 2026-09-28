package com.stella.nether.test.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test harness only. The game test server builds its world with structures switched off, so the raid nether
 * would hold no fortress or bastion; this switches them on as a real server has them. The flat test
 * dimensions are kept bare by an override of their preset with no structure sets.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestStructuresMixin {
	@Shadow
	@Final
	@Mutable
	private static WorldOptions WORLD_OPTIONS;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void stellaNether$generateStructures(final CallbackInfo ci) {
		WORLD_OPTIONS = new WorldOptions(WORLD_OPTIONS.seed(), true, false);
	}
}
