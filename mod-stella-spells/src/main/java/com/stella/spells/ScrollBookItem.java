package com.stella.spells;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Holds ten different scrolls. Use opens its row above the inventory. */
public final class ScrollBookItem extends Item {
	public ScrollBookItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!level.isClientSide()) {
			ItemStack book = player.getItemInHand(hand);
			player.openMenu(new SimpleMenuProvider(
				(id, inventory, opener) -> ScrollBookMenu.open(id, inventory, book, hand),
				Component.translatable("item." + Spellbooks.MOD_ID + ".scroll_book")
			));
		}
		return InteractionResult.SUCCESS;
	}
}
