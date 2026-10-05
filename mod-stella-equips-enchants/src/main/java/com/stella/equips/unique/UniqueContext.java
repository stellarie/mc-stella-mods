package com.stella.equips.unique;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The holder, the stack and the refine level that a hook of a unique runs for. */
public record UniqueContext(ServerPlayer holder, ItemStack stack, int refine) {
	public boolean atLeast(final int level) {
		return this.refine >= level;
	}
}
