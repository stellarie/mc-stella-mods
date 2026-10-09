package com.stella.spells;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A quiver menu: one vanilla chest row of nine ammunition slots above the player inventory, read from and
 * written to the quiver stack {@link DataComponents#CONTAINER}. Only arrows and fireworks go in. While it is
 * open, no quiver can move. The client opens a vanilla one-row chest screen with the same slot order, so the
 * server alone enforces the rules, as the Scroll Book does.
 */
public final class QuiverMenu extends AbstractContainerMenu {
	public static final int SIZE = 9;
	private static final int LEFT = 8;
	private static final int ROW_TOP = 18;
	private static final int INVENTORY_TOP = ROW_TOP + 18 + 13;
	private static final int OFFHAND_BUTTON = 40;

	private final ItemStack quiver;
	private final InteractionHand hand;

	private QuiverMenu(
		final int id, final Inventory inventory, final Container row, final ItemStack quiver, final InteractionHand hand
	) {
		super(MenuType.GENERIC_9x1, id);
		this.quiver = quiver;
		this.hand = hand;
		for (int i = 0; i < SIZE; i++) {
			this.addSlot(new AmmoSlot(row, i, LEFT + i * 18, ROW_TOP));
		}
		this.addStandardInventorySlots(inventory, LEFT, INVENTORY_TOP);
	}

	/** The server side, backed by the held quiver. */
	public static QuiverMenu open(final int id, final Inventory inventory, final ItemStack quiver, final InteractionHand hand) {
		return new QuiverMenu(id, inventory, new QuiverContainer(quiver), quiver, hand);
	}

	/** True for what a quiver holds: any arrow, and a firework rocket. */
	public static boolean isAmmunition(final ItemStack stack) {
		return stack.is(ItemTags.ARROWS) || stack.is(Items.FIREWORK_ROCKET);
	}

	private static boolean isQuiver(final ItemStack stack) {
		return stack.getItem() instanceof QuiverItem;
	}

	@Override
	public void clicked(final int slotId, final int button, final ContainerInput input, final Player player) {
		if (slotId >= 0 && slotId < this.slots.size() && isQuiver(this.slots.get(slotId).getItem())) {
			return;
		}
		if (input == ContainerInput.SWAP) {
			ItemStack swapped = button == OFFHAND_BUTTON ? player.getOffhandItem() : player.getInventory().getItem(button);
			if (isQuiver(swapped)) {
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
		} else if (!isAmmunition(stack) || !this.moveItemStackTo(stack, 0, SIZE, false)) {
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
		return player.getItemInHand(this.hand) == this.quiver;
	}

	private static final class AmmoSlot extends Slot {
		AmmoSlot(final Container container, final int index, final int x, final int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(final ItemStack stack) {
			return isAmmunition(stack);
		}
	}

	private static final class QuiverContainer extends SimpleContainer {
		private final ItemStack quiver;

		QuiverContainer(final ItemStack quiver) {
			super(SIZE);
			this.quiver = quiver;
			NonNullList<ItemStack> items = Quiver.contents(quiver);
			for (int i = 0; i < SIZE; i++) {
				super.setItem(i, items.get(i));
			}
		}

		@Override
		public void setChanged() {
			super.setChanged();
			this.quiver.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
		}
	}
}
