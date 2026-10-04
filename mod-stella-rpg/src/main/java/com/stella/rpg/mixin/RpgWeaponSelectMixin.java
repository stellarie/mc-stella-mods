package com.stella.rpg.mixin;

import com.stella.rpg.WeaponSlot;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The weapon slot is a legal selection, so the main hand reads it. Loading a save and a respawn copy pass here too. */
@Mixin(Inventory.class)
public abstract class RpgWeaponSelectMixin {
	@Shadow
	private int selected;

	@Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$allowWeaponSlot(final int slot, final CallbackInfo info) {
		if (WeaponSlot.isSlot(slot)) {
			this.selected = slot;
			info.cancel();
		}
	}
}
