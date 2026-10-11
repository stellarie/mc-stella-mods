package com.stella.nether.client.mixin;

import com.stella.nether.BlazeGuardian;
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

/** Draws the Blaze Sovereign in blue and the Blaze Guardian in green: the entity's kind tints the model. */
@Mixin(LivingEntityRenderer.class)
public abstract class SovereignTintMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void stellaNether$markTint(final LivingEntity entity, final LivingEntityRenderState state, final float partialTick, final CallbackInfo ci) {
		int tint = SovereignTint.NONE;
		if (BlazeSovereign.isSovereign(entity)) {
			tint = SovereignTint.TINT;
		} else if (entity instanceof BlazeGuardian) {
			tint = SovereignTint.GUARDIAN_TINT;
		}
		((SovereignTint) state).stellaNether$setTint(tint);
	}

	@Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
	private void stellaNether$tintBlaze(final LivingEntityRenderState state, final CallbackInfoReturnable<Integer> cir) {
		int tint = ((SovereignTint) state).stellaNether$tint();
		if (tint != SovereignTint.NONE) {
			cir.setReturnValue(tint);
		}
	}
}
