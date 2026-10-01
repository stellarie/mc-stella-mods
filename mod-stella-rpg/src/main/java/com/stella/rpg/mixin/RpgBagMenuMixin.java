package com.stella.rpg.mixin;

import com.stella.rpg.bag.Bag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Priority 1500: after the spells quick slots.
@Mixin(value = InventoryMenu.class, priority = 1500)
public abstract class RpgBagMenuMixin extends AbstractContainerMenu {
	protected RpgBagMenuMixin() {
		super(null, 0);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void stellaRpg$bagRows(final Inventory inventory, final boolean active, final Player owner, final CallbackInfo info) {
		Container view = Bag.view(owner);
		for (int row = 0; row < Bag.ROWS; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Bag.BagSlot(owner, view, row * 9 + column, Bag.SLOT_X + column * 18, Bag.SLOT_Y + row * 18));
			}
		}
	}
}
