package com.stella.food;

import java.util.List;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Potions of the same kind stack. Stacks compare the potion contents, so different potions never merge. */
public final class PotionStacks {
	public static final int MAX_STACK = 16;
	private static final List<Item> POTIONS = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

	private PotionStacks() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context ->
			context.modify(POTIONS, (builder, item) -> builder.set(DataComponents.MAX_STACK_SIZE, MAX_STACK)));
	}
}
