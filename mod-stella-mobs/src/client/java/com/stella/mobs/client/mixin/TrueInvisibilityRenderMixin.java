package com.stella.mobs.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.mobs.behavior.TrueInvisibility;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An invisible player shows no armor, held item, arrow, stinger, elytra, cape or head item. A revealed player, one who
 * attacked in the last {@link TrueInvisibility#REVEAL_TICKS} ticks, draws in full.
 */
@Mixin(AvatarRenderer.class)
public abstract class TrueInvisibilityRenderMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL")
	)
	private void stellaMobs$showRevealed(final Avatar avatar, final AvatarRenderState state, final float partialTicks, final CallbackInfo ci) {
		boolean revealed = ((AttachmentTarget) avatar).getAttachedOrElse(TrueInvisibility.REVEALED, false);
		if (revealed && avatar.hasEffect(MobEffects.INVISIBILITY)) {
			state.isInvisible = false;
			state.isInvisibleToPlayer = false;
		}
	}

	@ModifyReturnValue(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z", at = @At("RETURN"))
	private boolean stellaMobs$hideEquipmentWhileInvisible(final boolean original, final AvatarRenderState state) {
		return original && !state.isInvisible;
	}
}
