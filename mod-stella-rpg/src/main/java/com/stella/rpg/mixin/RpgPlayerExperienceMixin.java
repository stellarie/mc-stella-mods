package com.stella.rpg.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class RpgPlayerExperienceMixin {
	// Death keeps the level, so orbs would duplicate it.
	@Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$noOrbs(final ServerLevel level, final CallbackInfoReturnable<Integer> callback) {
		callback.setReturnValue(0);
	}
}
