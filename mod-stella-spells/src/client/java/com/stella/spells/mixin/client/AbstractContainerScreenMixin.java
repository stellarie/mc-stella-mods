package com.stella.spells.mixin.client;

import com.stella.spells.QuickSlots;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The quick slots stand outside the inventory panel, and a click outside the panel drops the carried stack: not on these slots. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void stellaSpellsQuickSlotsAreInside(
		final double mouseX, final double mouseY, final int left, final int top, final CallbackInfoReturnable<Boolean> info
	) {
		if (!(((AbstractContainerScreen<?>) (Object) this).getMenu() instanceof InventoryMenu menu)
			|| menu.slots.size() < QuickSlots.MENU_FIRST_SLOT + QuickSlots.COUNT) {
			return;
		}
		for (int i = 0; i < QuickSlots.COUNT; i++) {
			Slot slot = menu.slots.get(QuickSlots.MENU_FIRST_SLOT + i);
			if (mouseX >= left + slot.x - 1 && mouseX < left + slot.x + 17 && mouseY >= top + slot.y - 1 && mouseY < top + slot.y + 17) {
				info.setReturnValue(false);
				return;
			}
		}
	}
}
