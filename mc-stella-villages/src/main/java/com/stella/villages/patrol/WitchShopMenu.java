package com.stella.villages.patrol;

import com.stella.villages.data.VillageWorldData.ShopOffer;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The village witch's shop, a one-row chest menu that any client can show without a mod. The five offers sit
 * in slots 1 to 5 as enchanted books, the reroll control in slot 7, and the rest is glass. A click asks
 * {@link WitchShop}, which stays the only authority, and no item ever leaves the menu.
 */
public final class WitchShopMenu extends ChestMenu {
	public static final int[] OFFER_SLOTS = {1, 2, 3, 4, 5};
	public static final int REROLL_SLOT = 7;
	public static final String TITLE = "Mage Shop";
	private static final int ROW = 9;

	private final Mob witch;

	private WitchShopMenu(final int id, final Inventory inventory, final Mob witch, final ServerLevel level, final List<ShopOffer> offers) {
		super(MenuType.GENERIC_9x1, id, inventory, new SimpleContainer(ROW), 1);
		this.witch = witch;
		this.show(level, offers);
	}

	static void open(final ServerPlayer player, final Mob witch, final ServerLevel level, final List<ShopOffer> offers) {
		if (player.openMenu(new SimpleMenuProvider(
			(id, inventory, opener) -> new WitchShopMenu(id, inventory, witch, level, offers), Component.literal(TITLE)
		)).isPresent()) {
			Conversation.begin(witch, player);
		}
	}

	@Override
	public void removed(final Player player) {
		super.removed(player);
		if (player instanceof ServerPlayer serverPlayer) {
			Conversation.linger(this.witch, serverPlayer);
		}
	}

	/** The offer a slot sells, or -1 for glass, for the reroll control and for any slot outside the shop. */
	public static int offerIndex(final int slot) {
		for (int i = 0; i < OFFER_SLOTS.length; i++) {
			if (OFFER_SLOTS[i] == slot) {
				return i;
			}
		}
		return -1;
	}

	public Mob witch() {
		return this.witch;
	}

	/** Lays the shelves out again, after a purchase or a reroll changed what the village holds. */
	void show(final ServerLevel level, final List<ShopOffer> offers) {
		ItemStack glass = new ItemStack(Items.STAINED_GLASS_PANE.gray());
		glass.set(DataComponents.ITEM_NAME, Component.literal(" "));
		for (int slot = 0; slot < ROW; slot++) {
			int index = offerIndex(slot);
			ItemStack shown = glass.copy();
			if (index >= 0 && index < offers.size()) {
				shown = WitchShop.icon(level, offers.get(index));
			} else if (slot == REROLL_SLOT) {
				shown = rerollIcon();
			}
			this.getContainer().setItem(slot, shown);
		}
	}

	static ItemStack rerollIcon() {
		ItemStack icon = new ItemStack(Items.CLOCK);
		icon.set(DataComponents.ITEM_NAME, Component.literal("Reroll the offers"));
		icon.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal(WitchShop.REROLL_PRICE + " emeralds"),
			Component.literal("Click to replace all five books")
		)));
		return icon;
	}

	/** No click moves anything; a click on an offer or the reroll control asks the shop. */
	@Override
	public void clicked(final int slot, final int button, final ContainerInput input, final Player player) {
		if (player instanceof ServerPlayer serverPlayer
			&& (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
			int index = offerIndex(slot);
			if (index >= 0) {
				WitchShop.buy(serverPlayer, this.witch, index);
			} else if (slot == REROLL_SLOT) {
				WitchShop.reroll(serverPlayer, this.witch);
			}
		}
		// The client predicted a pickup, so it is told the shop is unchanged.
		WitchShop.refresh(this);
		this.sendAllDataToRemote();
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.witch.isAlive()
			&& player.distanceToSqr(this.witch) <= (double) WitchShop.SHOP_RANGE * WitchShop.SHOP_RANGE;
	}
}
