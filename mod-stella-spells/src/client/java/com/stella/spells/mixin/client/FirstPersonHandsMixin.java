package com.stella.spells.mixin.client;

import com.stella.spells.SpellbookItem;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps first-person hands visible after the hand-context model hides the book. */
@Mixin(FirstPersonHandsAndItems.class)
public final class FirstPersonHandsMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/client/player/LocalPlayer;FLnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
		at = @At("TAIL")
	)
	private void stellaSpellsHideHeldBooks(
		final LocalPlayer player, final float tickDelta, final FirstPersonHandsAndItemsRenderState state, final CallbackInfo info
	) {
		if (state.mainHandItem.getItem() instanceof SpellbookItem) {
			state.mainHandItem = ItemStack.EMPTY;
			state.mainHandRenderState.clear();
		}
		if (state.offHandItem.getItem() instanceof SpellbookItem) {
			state.offHandItem = ItemStack.EMPTY;
			state.offHandRenderState.clear();
		}
	}
}
