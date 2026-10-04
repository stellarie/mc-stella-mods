package com.stella.rpg.mixin;

import com.stella.rpg.WeaponSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the weapon slot out of the grid to stand above the shield slot. Only the client draws the position. */
@Mixin(InventoryMenu.class)
public abstract class RpgWeaponMenuMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void stellaRpg$placeWeaponSlot(final Inventory inventory, final boolean active, final Player owner, final CallbackInfo info) {
		for (Slot slot : ((InventoryMenu) (Object) this).slots) {
			if (slot.container == inventory && WeaponSlot.isSlot(slot.getContainerSlot())) {
				SlotPositionAccessor position = (SlotPositionAccessor) slot;
				position.stellaRpg$setX(WeaponSlot.MENU_X);
				position.stellaRpg$setY(WeaponSlot.MENU_Y);
			}
		}
	}
}
