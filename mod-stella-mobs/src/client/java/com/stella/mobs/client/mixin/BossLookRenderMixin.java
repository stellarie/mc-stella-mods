package com.stella.mobs.client.mixin;

import com.stella.mobs.behavior.BossLook;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A boss draws 2.5 times its base size while its hitbox stays smaller; the name tag rises with it. */
@Mixin(LivingEntityRenderer.class)
public abstract class BossLookRenderMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL")
	)
	private void stellaMobs$bossLook(final LivingEntity entity, final LivingEntityRenderState state, final float partialTicks, final CallbackInfo ci) {
		if (!BossLook.has(entity)) {
			return;
		}
		float factor = BossLook.renderFactor();
		state.scale *= factor;
		if (state.nameTagAttachment != null) {
			state.nameTagAttachment = state.nameTagAttachment.scale(factor);
		}
	}
}
