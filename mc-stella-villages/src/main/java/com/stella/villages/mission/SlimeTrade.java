package com.stella.villages.mission;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemLore;

/**
 * The duke's board sells goods for slime balls: three potions for {@link #POTION_PRICE} each, and a Scroll of
 * Return for {@link #SCROLL_PRICE}. The scroll comes from the spells mod by id, so the trade is absent without it.
 */
public final class SlimeTrade {
	public static final int POTION_PRICE = 32;
	public static final int SCROLL_PRICE = 16;
	public static final Identifier RETURN_SCROLL = Identifier.parse("mod-stella-spells:return_scroll");
	/** Test seam: how the scroll is found. The default reads the item registry. */
	private static volatile Supplier<Optional<ItemStack>> scrollSource = SlimeTrade::registryScroll;

	/** One trade: its board slot, its price in slime balls, and the goods, empty when the goods do not exist. */
	public record Trade(int slot, int price, Supplier<Optional<ItemStack>> goods) {
	}

	public static final List<Trade> TRADES = List.of(
		new Trade(9, POTION_PRICE, () -> Optional.of(PotionContents.createItemStack(Items.POTION, Potions.HEALING))),
		new Trade(13, POTION_PRICE, () -> Optional.of(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS))),
		new Trade(17, POTION_PRICE, () -> Optional.of(PotionContents.createItemStack(Items.POTION, Potions.STRENGTH))),
		new Trade(8, SCROLL_PRICE, () -> scrollSource.get())
	);
	/** The board slot of each trade, in the order of {@link #TRADES}. */
	public static final int[] SLOTS = TRADES.stream().mapToInt(Trade::slot).toArray();

	private SlimeTrade() {}

	public static void setScrollSource(final Supplier<Optional<ItemStack>> source) {
		scrollSource = source == null ? SlimeTrade::registryScroll : source;
	}

	private static Optional<ItemStack> registryScroll() {
		return BuiltInRegistries.ITEM.getOptional(RETURN_SCROLL).map(ItemStack::new);
	}

	/** The trade a board slot shows, or -1 for any other slot. */
	public static int tradeIndex(final int slot) {
		for (int i = 0; i < TRADES.size(); i++) {
			if (TRADES.get(i).slot() == slot) {
				return i;
			}
		}
		return -1;
	}

	/** The icon of a trade, or empty when its goods do not exist. */
	public static Optional<ItemStack> icon(final int index) {
		Trade trade = TRADES.get(index);
		return trade.goods().get().map(icon -> {
			icon.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Trade: " + trade.price() + " slime balls"),
				Component.literal("Click to trade")
			)));
			return icon;
		});
	}

	/** Takes the price and gives the goods; false, with no change, when the player has too few balls or the goods do not exist. */
	public static boolean trade(final ServerPlayer player, final int index) {
		Trade trade = TRADES.get(index);
		Optional<ItemStack> goods = trade.goods().get();
		if (goods.isEmpty()) {
			return false;
		}
		if (player.getInventory().countItem(Items.SLIME_BALL) < trade.price()) {
			player.sendSystemMessage(Component.literal("The duke wants " + trade.price() + " slime balls for that."));
			return false;
		}
		int owed = trade.price();
		for (int slot = 0; slot < player.getInventory().getContainerSize() && owed > 0; slot++) {
			ItemStack held = player.getInventory().getItem(slot);
			if (held.is(Items.SLIME_BALL)) {
				int taken = Math.min(owed, held.getCount());
				held.shrink(taken);
				owed -= taken;
			}
		}
		ItemStack bought = goods.get();
		player.getInventory().add(bought);
		if (!bought.isEmpty()) {
			player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), bought));
		}
		return true;
	}
}
