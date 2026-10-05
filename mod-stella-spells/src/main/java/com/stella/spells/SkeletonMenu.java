package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * A summoned skeleton's own screen, laid out as a plain three-row chest so any client shows it. The top row
 * holds its head, chest, legs, feet, main hand and off hand, two locked panes, and a name slot: a named item
 * left there renames the skeleton when the screen closes, and goes back to the player. The two rows below
 * are its pack.
 */
public class SkeletonMenu extends AbstractContainerMenu {
	public static final EquipmentSlot[] GEAR = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};
	public static final int NAME_SLOT = 8;
	public static final int PACK_START = 9;
	public static final int SIZE = 27;
	/** The grey pane beside the gear, which carries the target mode the master last chose. */
	public static final int MODE_SLOT = 6;

	private final Skeleton skeleton;
	private final ServantContainer container;

	public SkeletonMenu(final int id, final Inventory inventory, final Skeleton skeleton) {
		super(MenuType.GENERIC_9x3, id);
		this.skeleton = skeleton;
		this.container = new ServantContainer(skeleton);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				int index = row * 9 + column;
				this.addSlot(new ServantSlot(this.container, index, 8 + column * 18, 18 + row * 18));
			}
		}
		this.addStandardInventorySlots(inventory, 8, 84);
	}

	public Container container() {
		return this.container;
	}

	/** The skeleton this screen belongs to. */
	public Skeleton skeleton() {
		return this.skeleton;
	}

	/** The target mode shown in the pane slot: search the ground, or mirror the master's own target. */
	public static SummonSkeleton.Mode mode(final Skeleton skeleton) {
		ServerPlayer master = SummonSkeleton.owner(skeleton);
		return master == null ? SummonSkeleton.Mode.SEARCH : SummonSkeleton.targetMode(master);
	}

	// The grey pane beside the gear answers a click by turning to the other mode.
	@Override
	public void clicked(final int slotId, final int button, final ContainerInput input, final Player player) {
		if (slotId == MODE_SLOT && player instanceof net.minecraft.server.level.ServerPlayer master) {
			SummonSkeleton.Mode next = mode(this.skeleton) == SummonSkeleton.Mode.SEARCH
				? SummonSkeleton.Mode.MIRROR : SummonSkeleton.Mode.SEARCH;
			SummonSkeleton.setTargetMode(master, next);
			return;
		}
		super.clicked(slotId, button, input, player);
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.skeleton.isAlive() && player.distanceToSqr(this.skeleton) <= 64.0;
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem() || index == 6 || index == 7) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < SIZE) {
			if (!this.moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, PACK_START, SIZE, false)) {
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
	public void removed(final Player player) {
		super.removed(player);
		ItemStack name = this.container.name;
		if (!name.isEmpty()) {
			Component custom = name.get(DataComponents.CUSTOM_NAME);
			if (custom != null) {
				this.skeleton.setCustomName(custom);
			}
			if (!player.getInventory().add(name)) {
				if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
					player.spawnAtLocation(level, name);
				}
			}
			this.container.name = ItemStack.EMPTY;
		}
		SummonInventoryStore.save(this.skeleton);
	}

	private static final class ServantSlot extends Slot {
		ServantSlot(final Container container, final int index, final int x, final int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(final ItemStack stack) {
			int index = this.getContainerSlot();
			if (index == 6 || index == 7) {
				return false;
			}
			if (index < GEAR.length && GEAR[index].isArmor() && GEAR[index] != EquipmentSlot.HEAD) {
				Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
				return equippable != null && equippable.slot() == GEAR[index];
			}
			return true;
		}

		@Override
		public boolean mayPickup(final Player player) {
			int index = this.getContainerSlot();
			return index != 6 && index != 7;
		}

		@Override
		public int getMaxStackSize() {
			return this.getContainerSlot() < GEAR.length ? 1 : super.getMaxStackSize();
		}
	}

	/** The skeleton's gear, two panes, the name slot and its pack, as one container. */
	static final class ServantContainer implements Container {
		private final Skeleton skeleton;
		private ItemStack name = ItemStack.EMPTY;

		ServantContainer(final Skeleton skeleton) {
			this.skeleton = skeleton;
		}

		@Override
		public int getContainerSize() {
			return SIZE;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public ItemStack getItem(final int index) {
			if (index < GEAR.length) {
				return this.skeleton.getItemBySlot(GEAR[index]);
			}
			if (index == MODE_SLOT) {
				return SummonSkeleton.Mode.MIRROR == mode(this.skeleton)
					? new ItemStack(Items.STAINED_GLASS_PANE.red()) : new ItemStack(Items.STAINED_GLASS_PANE.lime());
			}
			if (index == 7) {
				return new ItemStack(Items.STAINED_GLASS_PANE.gray());
			}
			if (index == NAME_SLOT) {
				return this.name;
			}
			return SummonSkeleton.pack(this.skeleton).get(index - PACK_START);
		}

		@Override
		public ItemStack removeItem(final int index, final int count) {
			ItemStack current = this.getItem(index);
			if (current.isEmpty() || index == 6 || index == 7) {
				return ItemStack.EMPTY;
			}
			ItemStack taken = current.split(count);
			this.setItem(index, current);
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(final int index) {
			ItemStack current = this.getItem(index);
			this.setItem(index, ItemStack.EMPTY);
			return current;
		}

		@Override
		public void setItem(final int index, final ItemStack stack) {
			if (index < GEAR.length) {
				SummonSkeleton.equip(this.skeleton, GEAR[index], stack);
			} else if (index == NAME_SLOT) {
				this.name = stack;
			} else if (index >= PACK_START) {
				List<ItemStack> pack = new ArrayList<>(SummonSkeleton.pack(this.skeleton));
				pack.set(index - PACK_START, stack);
				SummonSkeleton.setPack(this.skeleton, pack);
			}
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(final Player player) {
			return this.skeleton.isAlive();
		}

		@Override
		public void clearContent() {
		}
	}
}
