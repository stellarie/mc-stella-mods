package com.stella.rpg.client.mixin.client;

import com.stella.rpg.bag.Bag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A click in the bag panel is inside the screen, so a carried stack is not thrown between bag slots. */
@Mixin(AbstractContainerScreen.class)
public abstract class RpgBagClickMixin {
	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$bagIsInside(
		final double mouseX, final double mouseY, final int left, final int top, final CallbackInfoReturnable<Boolean> info
	) {
		if (!((Object) this instanceof InventoryScreen)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.hasInfiniteMaterials()) {
			return;
		}
		if (mouseX >= left && mouseX < left + 176 && mouseY >= top + Bag.PANEL_TOP && mouseY < top + Bag.PANEL_BOTTOM) {
			info.setReturnValue(false);
		}
	}
}
