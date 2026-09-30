package com.stella.rpg;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

public final class RpgDeath {
	private RpgDeath() {
	}

	public static void register() {
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (alive) {
				return;
			}
			newPlayer.experienceLevel = oldPlayer.experienceLevel;
			newPlayer.experienceProgress = oldPlayer.experienceProgress;
			newPlayer.totalExperience = oldPlayer.totalExperience;
			newPlayer.setAttached(StatSheet.ATTACHMENT, StatPoints.sheet(oldPlayer));
		});
	}
}
