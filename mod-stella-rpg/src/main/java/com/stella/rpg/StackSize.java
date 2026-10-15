package com.stella.rpg;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;

/** Every stackable item stacks to 99, the vanilla codec cap. Unstackable items keep size 1. */
public final class StackSize {
	public static final int MAX = 99;

	private StackSize() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(
			item -> item.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1,
			(builder, item) -> builder.set(DataComponents.MAX_STACK_SIZE, MAX)
		));
	}
}
