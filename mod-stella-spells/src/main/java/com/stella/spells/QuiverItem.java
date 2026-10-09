package com.stella.spells;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Holds nine stacks of ammunition. Use opens its slots; in the off hand it feeds bows, crossbows and archer books. */
public final class QuiverItem extends Item {
	public QuiverItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!level.isClientSide()) {
			ItemStack quiver = player.getItemInHand(hand);
			player.openMenu(new SimpleMenuProvider(
				(id, inventory, opener) -> QuiverMenu.open(id, inventory, quiver, hand),
				Component.translatable("item." + Spellbooks.MOD_ID + ".quiver")
			));
		}
		return InteractionResult.SUCCESS;
	}
}
