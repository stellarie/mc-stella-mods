package com.stella.learn;

import net.minecraft.world.entity.Mob;

/** What one callout type asks of a mob: whether it can, and what it does once it has accepted. */
public interface CalloutHandler {
	boolean canPerform(Mob mob, Callout callout);

	void perform(Mob mob, Callout callout);
}
