package com.stella.rpg;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class RpgSleep {
	private RpgSleep() {
	}

	/** Heals every player still in bed when the night is skipped. */
	public static void healSleepers(final ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.isSleeping()) {
				player.setHealth(player.getMaxHealth());
			}
		}
	}
}
