package com.stella.equips.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.equips.StellaEquips;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public record PowerState(double combatPoints, int level) {
	public static final Codec<PowerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.DOUBLE.fieldOf("combat_points").forGetter(PowerState::combatPoints),
		Codec.INT.fieldOf("level").forGetter(PowerState::level)
	).apply(instance, PowerState::new));
	public static final AttachmentType<PowerState> TYPE = AttachmentRegistry.<PowerState>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.initializer(PowerState::new)
		.buildAndRegister(Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_state"));
	public static final int FIRST_LEVEL = 1;

	public PowerState() {
		this(0.0, FIRST_LEVEL);
	}

	public static PowerState get(final ServerPlayer player) {
		return ((AttachmentTarget) player).getAttachedOrCreate(TYPE);
	}

	public static void set(final ServerPlayer player, final PowerState state) {
		((AttachmentTarget) player).setAttached(TYPE, state);
	}
}
