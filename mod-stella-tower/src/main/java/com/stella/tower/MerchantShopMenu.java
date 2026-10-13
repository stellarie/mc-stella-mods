package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The ghost merchant's shop: a chest menu that any client can show, the goods on its shelf with their prices in the lore. A
 * click asks {@link TowerMerchants#buy}, which stays the only authority, and no item ever leaves the shelf.
 */
public final class MerchantShopMenu extends ChestMenu {
	/** How far a buyer may stand from the merchant before the shop closes. */
	public static final double REACH = 8.0;
	private static final int ROW = 9;

	private final GhostMerchant merchant;
	private final List<MerchantGoods.Good> goods;
	private final int first;

	private MerchantShopMenu(final int id, final Inventory inventory, final GhostMerchant merchant, final List<MerchantGoods.Good> goods) {
		super(typeFor(rowsFor(goods.size())), id, inventory, new SimpleContainer(rowsFor(goods.size()) * ROW), rowsFor(goods.size()));
		this.merchant = merchant;
		this.goods = goods;
		// A single row of goods sits in the middle of it.
		this.first = goods.size() < ROW ? (ROW - goods.size()) / 2 : 0;
		this.show();
	}

	static void open(final ServerPlayer player, final GhostMerchant merchant) {
		List<MerchantGoods.Good> goods = merchant.goods();
		if (player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new MerchantShopMenu(id, inventory, merchant, goods),
			Component.translatable("entity.mod-stella-tower.ghost_merchant"))).isPresent()) {
			merchant.setTradingPlayer(player);
		}
	}

	private static int rowsFor(final int goods) {
		return Math.max(1, Math.min(6, (goods + ROW - 1) / ROW));
	}

	private static MenuType<ChestMenu> typeFor(final int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	public GhostMerchant merchant() {
		return this.merchant;
	}

	/** The slot a good stands in. */
	public int slotOf(final int index) {
		return this.first + index;
	}

	/** The good a slot sells, or -1 for glass. */
	public int goodAt(final int slot) {
		int index = slot - this.first;
		return index >= 0 && index < this.goods.size() ? index : -1;
	}

	private void show() {
		ItemStack glass = new ItemStack(Items.STAINED_GLASS_PANE.lightBlue());
		glass.set(DataComponents.ITEM_NAME, Component.literal(" "));
		for (int slot = 0; slot < this.getContainer().getContainerSize(); slot++) {
			int index = goodAt(slot);
			this.getContainer().setItem(slot, index >= 0 ? shelf(this.goods.get(index)) : glass.copy());
		}
	}

	/** A good as the shelf shows it: its item, with its price under what the item says of itself. */
	static ItemStack shelf(final MerchantGoods.Good good) {
		ItemStack icon = good.shown().get().copy();
		List<Component> lines = new ArrayList<>();
		ItemLore own = icon.get(DataComponents.LORE);
		if (own != null) {
			lines.addAll(own.lines());
		}
		DescriptionLines.add(lines, Component.literal(good.price() + " emeralds").withStyle(ChatFormatting.GOLD));
		DescriptionLines.add(lines, Component.literal("Click to buy").withStyle(ChatFormatting.DARK_GRAY));
		icon.set(DataComponents.LORE, new ItemLore(lines));
		return icon;
	}

	/** No click moves anything; a click on a good asks the merchant to sell it. */
	@Override
	public void clicked(final int slot, final int button, final ContainerInput input, final Player player) {
		int index = goodAt(slot);
		if (index >= 0 && player instanceof ServerPlayer serverPlayer && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
			TowerMerchants.buy(serverPlayer, this.merchant, index);
		}
		// The client predicted a pickup, so it is told the shelf is unchanged.
		this.show();
		this.sendAllDataToRemote();
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void removed(final Player player) {
		super.removed(player);
		if (this.merchant.getTradingPlayer() == player) {
			this.merchant.setTradingPlayer(null);
		}
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.merchant.isAlive() && player.distanceToSqr(this.merchant) <= REACH * REACH;
	}
}
