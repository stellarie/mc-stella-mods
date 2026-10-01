package com.stella.rpg.client.mixin.client;

import com.stella.rpg.bag.Bag;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Centers the inventory screen for the taller panel, then restores the vanilla height for the background. */
@Mixin(InventoryScreen.class)
public abstract class RpgBagScreenMixin {
	private static final int VANILLA_HEIGHT = 166;

	@Inject(method = "init", at = @At("HEAD"))
	private void stellaRpg$tallForCentering(final CallbackInfo info) {
		((ContainerScreenPositionAccessor) (Object) this).stellaRpgSetImageHeight(Bag.PANEL_BOTTOM);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void stellaRpg$vanillaForBackground(final CallbackInfo info) {
		((ContainerScreenPositionAccessor) (Object) this).stellaRpgSetImageHeight(VANILLA_HEIGHT);
	}
}
