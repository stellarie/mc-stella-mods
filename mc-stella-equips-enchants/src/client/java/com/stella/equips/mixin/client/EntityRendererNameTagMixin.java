package com.stella.equips.mixin.client;

import com.stella.equips.readout.ReadoutNames;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every renderer fills its name tag through this one method, so clearing the state here
 * hides the floating name of a mob. The readout plate carries the name instead.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererNameTagMixin {
	@Inject(
		method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
		at = @At("TAIL")
	)
	private void stellaEquipsHideMobNameTag(
		final Entity entity,
		final EntityRenderState state,
		final float partialTicks,
		final double nameTagDistance,
		final double belowNameDistance,
		final CallbackInfo callback
	) {
		if (ReadoutNames.hidesVanillaTag(entity)) {
			state.nameTag = null;
		}
	}
}
