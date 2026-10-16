package com.stella.tower;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A shard of one element, broken from deepslate ore; nine make a rune ({@link RuneShardRecipe}). */
public class RuneShardItem extends Item {
	public RuneShardItem(final Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(final ItemStack stack) {
		TowerElement element = stack.get(TowerItems.SHARD);
		if (element == null) {
			return super.getName(stack);
		}
		return Component.translatable("item.mod-stella-tower.rune_shard.of", Component.literal(element.label()).withStyle(element.color()));
	}
}
