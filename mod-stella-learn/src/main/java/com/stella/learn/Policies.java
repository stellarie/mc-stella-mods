package com.stella.learn;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** Each player's learned policy, saved on the player beside their profile. */
public final class Policies {
	public static final AttachmentType<Policy> TYPE = AttachmentRegistry.createPersistent(StellaLearn.id("policy"), Policy.CODEC);

	private Policies() {
	}

	public static void register() {
		StellaLearn.LOGGER.debug("Registered the policy attachment {}", TYPE.identifier());
	}

	public static Policy get(final ServerPlayer player) {
		Policy policy = ((AttachmentTarget) player).getAttached(TYPE);
		return policy == null ? Policy.EMPTY : policy;
	}

	public static void set(final ServerPlayer player, final Policy policy) {
		((AttachmentTarget) player).setAttached(TYPE, policy);
	}
}
