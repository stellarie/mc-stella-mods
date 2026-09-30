package com.stella.spells.mixin;

import com.stella.spells.QuickSlots;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A death drops the quick slots with the rest of the inventory, and keepInventory, which skips this, keeps them. */
@Mixin(Inventory.class)
public abstract class InventoryDropAllMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	public net.minecraft.world.entity.player.Player player;

	@Inject(method = "dropAll", at = @At("TAIL"))
	private void stellaSpellsDropQuickSlots(final CallbackInfo info) {
		QuickSlots slots = QuickSlots.of(this.player);
		for (int i = 0; i < QuickSlots.COUNT; i++) {
			ItemStack stack = slots.get(i);
			if (!stack.isEmpty()) {
				ItemEntity entity = this.player.createItemStackToDrop(stack, true, false);
				if (entity != null) {
					this.player.level().addFreshEntity(entity);
				}
				slots.set(i, ItemStack.EMPTY);
			}
		}
	}
}
