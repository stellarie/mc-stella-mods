package com.stella.rpg.client.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stella.rpg.WeaponSlot;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The weapon slot is not on the bar; its box draws its own highlight, so the bar draws none. */
@Mixin(Hud.class)
public abstract class HotbarSelectionMixin {
	private static final int OFF_SCREEN = -1000;

	@ModifyExpressionValue(
		method = "extractItemHotbar",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I")
	)
	private int stellaRpg$hideWeaponSelection(final int selected) {
		return WeaponSlot.isSlot(selected) ? OFF_SCREEN : selected;
	}
}
