package com.stella.mobs.behavior;

import com.mojang.serialization.Codec;
import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Marks a boss for every client, so the client draws its model at {@link #VISUAL_SCALE} times the base size. The
 * hitbox keeps the elite and boss scale of {@link Elite}; only the drawing grows.
 */
public final class BossLook {
	public static final double VISUAL_SCALE = 2.5;
	public static final AttachmentType<Boolean> TYPE = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
		.buildAndRegister(Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "boss_look"));

	private BossLook() {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the boss look attachment {}", TYPE.identifier());
	}

	public static boolean has(final Entity entity) {
		return ((AttachmentTarget) entity).getAttachedOrElse(TYPE, false);
	}

	public static void mark(final Entity entity) {
		if (!has(entity)) {
			((AttachmentTarget) entity).setAttached(TYPE, true);
		}
	}

	/** How much more the client draws a boss than its hitbox scale: 2.5 over 1.8. */
	public static float renderFactor() {
		return (float) (VISUAL_SCALE / (1.0 + Elite.SCALE_BONUS + Elite.BOSS_SCALE_BONUS));
	}
}
