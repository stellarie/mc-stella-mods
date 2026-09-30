package com.stella.rpg.net;

import com.stella.rpg.StellaRpg;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A request to reset every base stat. The server checks the emerald cost. */
public record RespecPayload() implements CustomPacketPayload {
	public static final Type<RespecPayload> TYPE = new Type<>(StellaRpg.id("respec"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RespecPayload> CODEC = StreamCodec.unit(new RespecPayload());

	@Override
	public Type<RespecPayload> type() {
		return TYPE;
	}
}
