package com.stella.spells.mixin;

import com.stella.spells.QuickSlots;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the four quick slots after every vanilla slot, so no vanilla slot index moves. */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {
	protected InventoryMenuMixin() {
		super(null, 0);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void stellaSpellsAddQuickSlots(final Inventory inventory, final boolean active, final Player owner, final CallbackInfo info) {
		Container view = QuickSlots.view(owner);
		for (int i = 0; i < QuickSlots.COUNT; i++) {
			this.addSlot(new Slot(view, i, QuickSlots.MENU_X, QuickSlots.MENU_Y + i * QuickSlots.MENU_STEP));
		}
	}
}
