package com.stella.villages.mission;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemLore;

/** The duke's board sells one potion for {@link #PRICE} slime balls. */
public final class SlimeTrade {
	public static final int PRICE = 32;
	/** The board slot of each trade, in the order of {@link #POTIONS}. */
	public static final int[] SLOTS = {9, 13, 17};
	public static final List<Holder<Potion>> POTIONS = List.of(Potions.HEALING, Potions.SWIFTNESS, Potions.STRENGTH);

	private SlimeTrade() {}

	/** The trade a board slot shows, or -1 for any other slot. */
	public static int tradeIndex(final int slot) {
		for (int i = 0; i < SLOTS.length; i++) {
			if (SLOTS[i] == slot) {
				return i;
			}
		}
		return -1;
	}

	public static ItemStack icon(final int index) {
		ItemStack icon = PotionContents.createItemStack(Items.POTION, POTIONS.get(index));
		icon.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Trade: " + PRICE + " slime balls"),
			Component.literal("Click to trade")
		)));
		return icon;
	}

	/** Takes the price and gives the potion; false, with no change, when the player has too few slime balls. */
	public static boolean trade(final ServerPlayer player, final int index) {
		if (player.getInventory().countItem(Items.SLIME_BALL) < PRICE) {
			player.sendSystemMessage(Component.literal("The duke wants " + PRICE + " slime balls for that potion."));
			return false;
		}
		int owed = PRICE;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && owed > 0; slot++) {
			ItemStack held = player.getInventory().getItem(slot);
			if (held.is(Items.SLIME_BALL)) {
				int taken = Math.min(owed, held.getCount());
				held.shrink(taken);
				owed -= taken;
			}
		}
		ItemStack potion = PotionContents.createItemStack(Items.POTION, POTIONS.get(index));
		player.getInventory().add(potion);
		if (!potion.isEmpty()) {
			player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), potion));
		}
		return true;
	}
}
