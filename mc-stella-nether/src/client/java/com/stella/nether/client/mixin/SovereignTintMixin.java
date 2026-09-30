package com.stella.nether.client.mixin;

import com.stella.nether.BlazeSovereign;
import com.stella.nether.client.SovereignTint;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws the Blaze Sovereign in blue: its mark, synced from the server, tints the model. */
@Mixin(LivingEntityRenderer.class)
public abstract class SovereignTintMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void stellaNether$markSovereign(final LivingEntity entity, final LivingEntityRenderState state, final float partialTick, final CallbackInfo ci) {
		((SovereignTint) state).stellaNether$setSovereign(BlazeSovereign.isSovereign(entity));
	}

	@Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
	private void stellaNether$tintSovereign(final LivingEntityRenderState state, final CallbackInfoReturnable<Integer> cir) {
		if (((SovereignTint) state).stellaNether$sovereign()) {
			cir.setReturnValue(SovereignTint.TINT);
		}
	}
}
