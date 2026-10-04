package com.stella.rpg.client.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stella.rpg.client.WeaponSlotClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A hotbar key asks the server for the weapon slot rule instead of selecting on the client. */
@Mixin(Minecraft.class)
public abstract class WeaponHotkeyMixin {
	@WrapOperation(
		method = "handleKeybinds",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V")
	)
	private void stellaRpg$sendHotkey(final Inventory inventory, final int slot, final Operation<Void> original) {
		if (!WeaponSlotClient.sendHotkey(slot)) {
			original.call(inventory, slot);
		}
	}
}
