package com.stella.food;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public final class CookingStationMenu extends AbstractContainerMenu {
	public static final int RESULT_SLOT = 0;
	public static final int INPUT_START = 1;
	public static final int INPUT_END = 10;
	private static final int INVENTORY_START = 10;
	private static final int INVENTORY_END = 46;
	public static final MenuType<CookingStationMenu> TYPE = Registry.register(
		BuiltInRegistries.MENU,
		StellaFood.id("cooking_station"),
		new MenuType<>(CookingStationMenu::new, FeatureFlags.VANILLA_SET)
	);

	private final ContainerLevelAccess access;
	private final Inventory inventory;
	private final SimpleContainer input;
	private final SimpleContainer result = new SimpleContainer(1);
	private final DataSlot selectedEntry = DataSlot.standalone();
	private boolean updating;
	private Roll roll;

	public CookingStationMenu(final int containerId, final Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public CookingStationMenu(final int containerId, final Inventory inventory, final ContainerLevelAccess access) {
		super(TYPE, containerId);
		this.access = access;
		this.inventory = inventory;
		this.input = new SimpleContainer(9) {
			@Override
			public void setChanged() {
				super.setChanged();
				CookingStationMenu.this.slotsChanged(this);
			}
		};
		this.addSlot(new Slot(this.result, 0, 124, 35) {
			@Override
			public boolean mayPlace(final ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(final Player player, final ItemStack stack) {
				CookingStationMenu.this.takeResult(player, stack);
				super.onTake(player, stack);
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				int slot = column + row * 3;
				this.addSlot(new Slot(this.input, slot, 30 + column * 18, 17 + row * 18));
			}
		}
		this.addStandardInventorySlots(inventory, 8, 84);
		this.addDataSlot(this.selectedEntry);
	}

	public static void register() {
	}

	public int selectedEntry() {
		return this.selectedEntry.get();
	}

	@Override
	public void slotsChanged(final Container container) {
		if (!this.updating) {
			this.updateResult();
		}
	}

	@Override
	public boolean stillValid(final Player player) {
		return stillValid(this.access, player, CookingStationBlock.BLOCK);
	}

	@Override
	public boolean clickMenuButton(final Player player, final int buttonId) {
		if (buttonId < 0 || buttonId >= 8) {
			return false;
		}
		this.selectedEntry.set(buttonId);
		if (!player.level().isClientSide() && buttonId < CookingStation.dishRecipes().size()) {
			this.fillDish(player, CookingStation.dishRecipes().get(buttonId));
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (slotIndex == RESULT_SLOT) {
			if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else if (slotIndex >= INPUT_START && slotIndex < INPUT_END) {
			if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, INPUT_START, INPUT_END, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, slotIndex == RESULT_SLOT ? original : stack);
		if (slotIndex == RESULT_SLOT) {
			player.drop(stack, false, Prediction.PREDICTED);
		}
		return original;
	}

	@Override
	public boolean canTakeItemForPickAll(final ItemStack carried, final Slot target) {
		return target.container != this.result && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public void removed(final Player player) {
		super.removed(player);
		this.result.clearContent();
		this.access.execute((level, pos) -> this.clearContainer(player, this.input));
	}

	// The server alone fills the result slot: it rolls the seasoning, and a client guess would differ.
	private void updateResult() {
		if (this.inventory.player.level().isClientSide()) {
			return;
		}
		CookingStation.Match match = CookingStation.findMatch(this.input);
		this.result.setItem(0, match == null ? ItemStack.EMPTY : this.seasoned(match.result().copy()));
		this.broadcastChanges();
	}

	// One roll per dish and potion pair, kept until the output is taken, so changing the grid cannot reroll it.
	private ItemStack seasoned(final ItemStack output) {
		PotionContents contents = output.get(DataComponents.POTION_CONTENTS);
		if (contents == null || !FoodItems.isDish(output.getItem())) {
			return output;
		}
		if (this.roll == null || this.roll.dish() != output.getItem() || !this.roll.contents().equals(contents)) {
			this.roll = new Roll(output.getItem(), contents, Seasoning.roll(this.inventory.player.getRandom()));
		}
		output.set(StellaFood.SEASONING, this.roll.seasoning());
		return output;
	}

	private void takeResult(final Player player, final ItemStack taken) {
		CookingStation.Match match = CookingStation.findMatch(this.input);
		ItemStack plain = taken.copy();
		plain.remove(StellaFood.SEASONING);
		if (match == null || !ItemStack.isSameItemSameComponents(match.result(), plain)) {
			this.result.clearContent();
			return;
		}
		this.updating = true;
		try {
			for (int slot : match.inputSlots()) {
				ItemStack source = this.input.getItem(slot);
				source.shrink(1);
				if (match.bottleSlots().contains(slot)) {
					this.returnBottle(player, slot, source);
				} else if (source.isEmpty()) {
					this.input.setItem(slot, ItemStack.EMPTY);
				}
			}
		} finally {
			this.updating = false;
		}
		taken.onCraftedBy(player, taken.getCount());
		this.roll = null;
		this.updateResult();
	}

	private record Roll(Item dish, PotionContents contents, Seasoning seasoning) {
	}

	private void returnBottle(final Player player, final int slot, final ItemStack source) {
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		if (source.isEmpty()) {
			this.input.setItem(slot, ItemStack.EMPTY);
		}
		if (!this.inventory.add(bottle)) {
			player.drop(bottle, false, Prediction.PREDICTED);
		}
	}

	private void fillDish(final Player player, final CookingStation.DishRecipe recipe) {
		List<Item> selected = this.selectIngredients(recipe);
		if (selected.isEmpty()) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("screen.mod-stella-food.missing_ingredients"));
			}
			return;
		}
		this.updating = true;
		try {
			List<ItemStack> ingredients = new ArrayList<>();
			for (Item item : selected) {
				ingredients.add(this.removeOne(item));
			}
			for (int slot = 0; slot < this.input.getContainerSize(); slot++) {
				ItemStack remaining = this.input.removeItemNoUpdate(slot);
				if (!remaining.isEmpty() && !this.inventory.add(remaining)) {
					player.drop(remaining, false, Prediction.PREDICTED);
				}
			}
			for (int slot = 0; slot < ingredients.size(); slot++) {
				this.input.setItem(slot, ingredients.get(slot));
			}
		} finally {
			this.updating = false;
		}
		this.updateResult();
	}

	private List<Item> selectIngredients(final CookingStation.DishRecipe recipe) {
		Map<Item, Integer> available = new IdentityHashMap<>();
		this.countItems(this.input, available);
		this.countItems(this.inventory, available);
		List<Item> selected = new ArrayList<>();
		for (List<Item> choice : recipe.choices()) {
			Item found = null;
			for (Item item : choice) {
				if (available.getOrDefault(item, 0) > 0) {
					found = item;
					break;
				}
			}
			if (found == null) {
				return List.of();
			}
			available.put(found, available.get(found) - 1);
			selected.add(found);
		}
		return selected;
	}

	private void countItems(final Container container, final Map<Item, Integer> counts) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
	}

	private ItemStack removeOne(final Item item) {
		for (int slot = 0; slot < this.input.getContainerSize(); slot++) {
			ItemStack stack = this.input.getItem(slot);
			if (stack.is(item)) {
				return this.input.removeItem(slot, 1);
			}
		}
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (stack.is(item)) {
				return this.inventory.removeItem(slot, 1);
			}
		}
		throw new IllegalStateException("Planned station ingredient disappeared");
	}
}
