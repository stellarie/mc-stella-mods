package com.stella.spells.mixin.client;

import com.stella.spells.SpellbookItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses third-person spellbook geometry while retaining the avatar arms. */
@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerItemInHandLayerMixin {
	@Inject(
		method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void stellaSpellsHideHeldBooks(
		final AvatarRenderState state, final ItemStackRenderState renderState, final ItemStack stack,
		final HumanoidArm arm, final PoseStack pose, final SubmitNodeCollector collector, final int light,
		final CallbackInfo info
	) {
		if (stack.getItem() instanceof SpellbookItem) {
			info.cancel();
		}
	}
}
