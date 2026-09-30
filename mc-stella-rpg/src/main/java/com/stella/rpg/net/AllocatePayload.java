package com.stella.rpg.net;

import com.stella.rpg.StellaRpg;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A request to spend points. The stat is an ordinal of {@code Stat}; the server validates both fields. */
public record AllocatePayload(int stat, int amount) implements CustomPacketPayload {
	public static final Type<AllocatePayload> TYPE = new Type<>(StellaRpg.id("allocate"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AllocatePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, AllocatePayload::stat,
		ByteBufCodecs.VAR_INT, AllocatePayload::amount,
		AllocatePayload::new
	);

	@Override
	public Type<AllocatePayload> type() {
		return TYPE;
	}
}
