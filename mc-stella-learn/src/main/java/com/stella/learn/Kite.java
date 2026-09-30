package com.stella.learn;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** Remembers when a player last hit something with a projectile, so a retreat right after a shot reads as a kite. */
final class Kite {
	/** Ticks after a shot within which a retreat counts as kiting. */
	static final long WINDOW_TICKS = 60L;
	private static final AttachmentType<Long> LAST_SHOT = AttachmentRegistry.create(StellaLearn.id("last_shot"));

	private Kite() {
	}

	static void markShot(final ServerPlayer player) {
		markShot(player, LearnClock.now());
	}

	static void markShot(final ServerPlayer player, final long tick) {
		((AttachmentTarget) player).setAttached(LAST_SHOT, tick);
	}

	static boolean recentShot(final ServerPlayer player) {
		Long last = ((AttachmentTarget) player).getAttached(LAST_SHOT);
		return last != null && LearnClock.now() - last <= WINDOW_TICKS;
	}
}
