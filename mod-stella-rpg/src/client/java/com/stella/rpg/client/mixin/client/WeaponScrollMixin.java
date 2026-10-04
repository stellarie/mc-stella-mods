package com.stella.rpg.client.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stella.rpg.client.WeaponSlotClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The wheel selects nothing: hotkeys go through the server rule, so a weapon is never picked by scrolling. */
@Mixin(MouseHandler.class)
public abstract class WeaponScrollMixin {
	@WrapOperation(
		method = "onScroll",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V")
	)
	private void stellaRpg$noScrollSelect(final Inventory inventory, final int slot, final Operation<Void> original) {
		if (!WeaponSlotClient.active()) {
			original.call(inventory, slot);
		}
	}
}
