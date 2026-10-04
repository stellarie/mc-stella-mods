package com.stella.equips.unique;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.equips.StellaEquips;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jspecify.annotations.Nullable;

/** Which unique loosed an arrow, and at what refine level. The mark lives on the arrow, so a hit reads it later. */
public final class UniqueArrows {
	/** The unique id and the refine level at the moment of the shot. */
	public record Mark(String id, int refine) {
		public static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Mark::id),
			Codec.INT.fieldOf("refine").forGetter(Mark::refine)
		).apply(i, Mark::new));
	}

	public static final AttachmentType<Mark> MARK = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "unique_arrow"), builder -> builder.persistent(Mark.CODEC));

	private UniqueArrows() {
	}

	public static void mark(final AbstractArrow arrow, final String id, final int refine) {
		((AttachmentTarget) arrow).setAttached(MARK, new Mark(id, refine));
	}

	public static @Nullable Mark of(final AbstractArrow arrow) {
		return ((AttachmentTarget) arrow).getAttached(MARK);
	}
}
