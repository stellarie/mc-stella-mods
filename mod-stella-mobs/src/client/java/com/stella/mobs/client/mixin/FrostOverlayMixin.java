package com.stella.mobs.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The powder snow frost overlay no longer draws. The bluish-white edge flash replaces it. */
@Mixin(Hud.class)
public abstract class FrostOverlayMixin {
	// A missed target must not stop the client from starting.
	@ModifyExpressionValue(
		method = "extractCameraOverlays",
		require = 0,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I")
	)
	private int stellaMobs$noFrostOverlay(final int ticksFrozen) {
		return 0;
	}
}
