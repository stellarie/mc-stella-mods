package com.stella.spells;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A Scroll Book's menu: one vanilla chest row of nine scroll slots above the player inventory. Each slot holds one
 * scroll type, and no two slots hold the same type. The row reads from and writes to the held book's
 * {@link DataComponents#CONTAINER}. While the menu is open, no Scroll Book can move.
 * The client opens a vanilla one-row chest menu with the same slot order, so the server alone enforces the rules.
 */
public final class ScrollBookMenu extends AbstractContainerMenu {
	public static final int SIZE = 9;
	private static final int LEFT = 8;
	private static final int ROW_TOP = 18;
	private static final int INVENTORY_TOP = ROW_TOP + 18 + 13;
	private static final int OFFHAND_BUTTON = 40;

	private final ItemStack book;
	private final InteractionHand hand;

	private ScrollBookMenu(
		final int id, final Inventory inventory, final Container row, final ItemStack book, final InteractionHand hand
	) {
		super(MenuType.GENERIC_9x1, id);
		this.book = book;
		this.hand = hand;
		for (int i = 0; i < SIZE; i++) {
			this.addSlot(new ScrollSlot(row, i, LEFT + i * 18, ROW_TOP));
		}
		this.addStandardInventorySlots(inventory, LEFT, INVENTORY_TOP);
	}

	/** The server side, backed by the held book. */
	public static ScrollBookMenu open(final int id, final Inventory inventory, final ItemStack book, final InteractionHand hand) {
		return new ScrollBookMenu(id, inventory, new BookContainer(book), book, hand);
	}

	public static boolean isScroll(final ItemStack stack) {
		return stack.getItem() instanceof SpellScrollItem || stack.getItem() instanceof ReturnScrollItem;
	}

	private static boolean isBook(final ItemStack stack) {
		return stack.getItem() instanceof ScrollBookItem;
	}

	@Override
	public void clicked(final int slotId, final int button, final ContainerInput input, final Player player) {
		if (slotId >= 0 && slotId < this.slots.size() && isBook(this.slots.get(slotId).getItem())) {
			return;
		}
		if (input == ContainerInput.SWAP) {
			ItemStack swapped = button == OFFHAND_BUTTON ? player.getOffhandItem() : player.getInventory().getItem(button);
			if (isBook(swapped)) {
				return;
			}
		}
		super.clicked(slotId, button, input, player);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < SIZE) {
			if (!this.moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!isScroll(stack) || !this.moveItemStackTo(stack, 0, SIZE, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(final Player player) {
		return player.getItemInHand(this.hand) == this.book;
	}

	private final class ScrollSlot extends Slot {
		ScrollSlot(final Container container, final int index, final int x, final int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(final ItemStack stack) {
			if (!isScroll(stack)) {
				return false;
			}
			for (int i = 0; i < SIZE; i++) {
				Slot other = ScrollBookMenu.this.slots.get(i);
				if (other != this && other.getItem().is(stack.getItem())) {
					return false;
				}
			}
			return true;
		}
	}

	private static final class BookContainer extends SimpleContainer {
		private final ItemStack book;

		BookContainer(final ItemStack book) {
			super(SIZE);
			this.book = book;
			NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
			book.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
			for (int i = 0; i < SIZE; i++) {
				super.setItem(i, items.get(i));
			}
		}

		@Override
		public void setChanged() {
			super.setChanged();
			this.book.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
		}
	}
}
