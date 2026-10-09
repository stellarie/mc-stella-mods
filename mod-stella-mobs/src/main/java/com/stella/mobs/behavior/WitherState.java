package com.stella.mobs.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * The durable half of an encounter Wither: whether its charge-up is over and the height it stood at then.
 * A Wither with no state is a legacy one and stays vanilla. Every field is optional in the codec, so a save written
 * before a later field existed still decodes.
 */
public record WitherState(boolean activated, double anchorY) {
	public static final Codec<WitherState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("activated", false).forGetter(WitherState::activated),
		Codec.DOUBLE.optionalFieldOf("anchor_y", 0.0).forGetter(WitherState::anchorY)
	).apply(instance, WitherState::new));
	public static final AttachmentType<WitherState> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "wither_state"), CODEC
	);

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the wither state attachment {}", TYPE.identifier());
	}

	/** The state, or null for a legacy Wither. It never creates one. */
	public static @Nullable WitherState find(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(TYPE);
	}

	/** Read by the rpg mod through a method handle, so it must stay public static and create nothing. */
	public static boolean hasWitherFlag(final Mob mob) {
		return find(mob) != null;
	}

	public static void set(final Mob mob, final WitherState state) {
		((AttachmentTarget) mob).setAttached(TYPE, state);
	}
}
