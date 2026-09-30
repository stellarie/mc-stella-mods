package com.stella.rpg.tomb;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Uses the vanilla 9x1 chest menu type, so a vanilla client needs no screen from this mod. */
public final class TombstoneMenu extends AbstractContainerMenu {
	private static final int SLOTS = 9;
	private static final int INVENTORY_END = SLOTS + 36;
	private final ContainerLevelAccess access;

	public TombstoneMenu(final int containerId, final Inventory inventory, final Container tomb, final ContainerLevelAccess access) {
		super(MenuType.GENERIC_9x1, containerId);
		this.access = access;
		for (int column = 0; column < SLOTS; column++) {
			this.addSlot(new Slot(tomb, column, 8 + column * 18, 18) {
				@Override
				public boolean mayPlace(final ItemStack stack) {
					return false;
				}
			});
		}
		this.addStandardInventorySlots(inventory, 8, 49);
	}

	@Override
	public boolean stillValid(final Player player) {
		return stillValid(this.access, player, TombstoneBlock.BLOCK);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		if (slotIndex >= SLOTS) {
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (!this.moveItemStackTo(stack, SLOTS, INVENTORY_END, true)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}
}
