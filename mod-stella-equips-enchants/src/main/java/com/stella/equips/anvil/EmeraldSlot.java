package com.stella.equips.anvil;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The enchanting table's currency slot. It takes emeralds and shows the emerald sprite. */
public class EmeraldSlot extends Slot {
	private static final Identifier EMERALD_SLOT = Identifier.withDefaultNamespace("container/slot/emerald");

	public EmeraldSlot(final Container container, final int slot, final int x, final int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(final ItemStack itemStack) {
		return itemStack.is(Items.EMERALD);
	}

	@Override
	public Identifier getNoItemIcon() {
		return EMERALD_SLOT;
	}
}
