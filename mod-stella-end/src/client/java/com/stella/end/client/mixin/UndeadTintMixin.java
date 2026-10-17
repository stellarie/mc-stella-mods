package com.stella.end.client.mixin;

import com.stella.end.UndeadBlazes;
import com.stella.end.client.UndeadTint;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws the Sovereign and its undead blazes in grey: the mark the server syncs on the entity picks the tint. */
@Mixin(LivingEntityRenderer.class)
public abstract class UndeadTintMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void stellaEnd$markTint(final LivingEntity entity, final LivingEntityRenderState state, final float partialTick, final CallbackInfo ci) {
		Integer mark = entity.getAttached(UndeadBlazes.MARK);
		int tint = UndeadTint.NONE;
		if (mark != null) {
			tint = mark == UndeadBlazes.MARK_SOVEREIGN ? UndeadTint.SOVEREIGN : UndeadTint.BLAZE;
		}
		((UndeadTint) state).stellaEnd$setTint(tint);
	}

	@Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
	private void stellaEnd$tintTheBlaze(final LivingEntityRenderState state, final CallbackInfoReturnable<Integer> cir) {
		int tint = ((UndeadTint) state).stellaEnd$tint();
		if (tint != UndeadTint.NONE) {
			cir.setReturnValue(tint);
		}
	}
}
