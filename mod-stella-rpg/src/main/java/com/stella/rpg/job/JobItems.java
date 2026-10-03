package com.stella.rpg.job;

import net.minecraft.server.level.ServerPlayer;

/** The items of the jobs. */
public final class JobItems {
	private JobItems() {
	}

	/** Gives the item of the job. False while no item exists. */
	static boolean grant(final ServerPlayer player, final Job job) {
		return false;
	}
}
