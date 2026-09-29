package com.stella.learn;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** Each player's profile, saved on the player. */
public final class Profiles {
	public static final AttachmentType<PlayerProfile> TYPE = AttachmentRegistry.createPersistent(
		StellaLearn.id("profile"), PlayerProfile.CODEC
	);

	private Profiles() {
	}

	public static void register() {
		StellaLearn.LOGGER.debug("Registered the profile attachment {}", TYPE.identifier());
	}

	public static PlayerProfile get(final ServerPlayer player) {
		PlayerProfile profile = ((AttachmentTarget) player).getAttached(TYPE);
		return profile == null ? PlayerProfile.EMPTY : profile;
	}

	public static void set(final ServerPlayer player, final PlayerProfile profile) {
		((AttachmentTarget) player).setAttached(TYPE, profile);
	}

	public static double value(final ServerPlayer player, final Habit habit) {
		return get(player).value(habit, LearnClock.now(), LearnConfigStore.get().profileHalfLifeTicks());
	}

	public static void record(final ServerPlayer player, final Habit habit) {
		((AttachmentTarget) player).setAttached(
			TYPE, get(player).record(habit, 1.0, LearnClock.now(), LearnConfigStore.get().profileHalfLifeTicks())
		);
	}
}
