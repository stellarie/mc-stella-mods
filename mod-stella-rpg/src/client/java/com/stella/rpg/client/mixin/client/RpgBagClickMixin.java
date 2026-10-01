package com.stella.rpg.client.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stella.rpg.bag.Bag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The inventory screen with the bag panel. Centering uses the taller panel. The final {@code imageHeight} field
 * keeps its vanilla value, so only the read in {@code init} changes. A click in the bag panel is inside the screen,
 * so a carried stack is not thrown between bag slots.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class RpgBagClickMixin {
	@ModifyExpressionValue(
		method = "init",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;imageHeight:I")
	)
	private int stellaRpg$centerWithBag(final int height) {
		return (Object) this instanceof InventoryScreen && bagShown() ? Bag.PANEL_BOTTOM : height;
	}

	private static boolean bagShown() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && !client.player.hasInfiniteMaterials();
	}

	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$bagIsInside(
		final double mouseX, final double mouseY, final int left, final int top, final CallbackInfoReturnable<Boolean> info
	) {
		if (!((Object) this instanceof InventoryScreen)) {
			return;
		}
		if (!bagShown()) {
			return;
		}
		if (mouseX >= left && mouseX < left + 176 && mouseY >= top + Bag.PANEL_TOP && mouseY < top + Bag.PANEL_BOTTOM) {
			info.setReturnValue(false);
		}
	}
}
