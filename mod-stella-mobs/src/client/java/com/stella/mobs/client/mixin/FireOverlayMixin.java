package com.stella.mobs.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The first person fire overlay no longer draws. The orange edge flash replaces it. */
@Mixin(ScreenEffectRenderer.class)
public abstract class FireOverlayMixin {
	@ModifyExpressionValue(
		method = "submit",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;isOnFire:Z")
	)
	private boolean stellaMobs$noFireOverlay(final boolean onFire) {
		return false;
	}
}
