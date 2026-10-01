package com.stella.rpg;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.world.level.gamerules.GameRules;

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
			// Tombstone cancelled dropAll, so copy the rest.
			if (!oldPlayer.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
				newPlayer.getInventory().replaceWith(oldPlayer.getInventory());
				oldPlayer.getInventory().clearContent();
			}
		});
		// After the respawn, so an overflow drop lands at the respawn point.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				com.stella.rpg.tomb.GraveCompass.give(oldPlayer, newPlayer);
			}
		});
	}
}
