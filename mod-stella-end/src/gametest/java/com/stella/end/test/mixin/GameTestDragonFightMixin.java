package com.stella.end.test.mixin;

import com.stella.end.EndGameTestInit;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test harness only. The vanilla dragon fight starts once a player stands in the End and puts a dragon at the castle's
 * centre every 1200 ticks, and a flying dragon hurts mobs at random in tests that count them. The fight runs only in
 * the one test that asks for it.
 */
@Mixin(EnderDragonFight.class)
public abstract class GameTestDragonFightMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void stellaEnd$holdTheFight(final CallbackInfo ci) {
		if (!EndGameTestInit.dragonFightRuns) {
			ci.cancel();
		}
	}
}
