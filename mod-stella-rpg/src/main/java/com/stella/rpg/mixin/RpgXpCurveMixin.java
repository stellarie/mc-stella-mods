package com.stella.rpg.mixin;

import com.stella.rpg.XpCurve;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class RpgXpCurveMixin {
	@Shadow
	public int experienceLevel;

	@Inject(method = "getXpNeededForNextLevel", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$xpCurve(final CallbackInfoReturnable<Integer> callback) {
		callback.setReturnValue(XpCurve.needFor(this.experienceLevel));
	}
}
