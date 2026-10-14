package com.stella.learn;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** What one callout type asks of a mob: whether it can, and what it does once it has accepted. */
public interface CalloutHandler {
	boolean canPerform(Mob mob, ServerPlayer player, Callout callout);

	/** `index` is how many receivers accepted before this one, so a handler can give each its own share. */
	void perform(Mob mob, ServerPlayer player, Callout callout, int index);

	/** False for a callout that is told once and leaves no request for the mob to hold, so it never blocks another. */
	default boolean holds() {
		return true;
	}
}
