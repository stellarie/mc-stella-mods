package com.stella.equips.refine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: refine the piece in the open anvil with this container id. */
public record RefineRequestPayload(int containerId) implements CustomPacketPayload {
	public static final Type<RefineRequestPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "refine_request")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, RefineRequestPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RefineRequestPayload::containerId,
		RefineRequestPayload::new
	);

	@Override
	public Type<RefineRequestPayload> type() {
		return TYPE;
	}
}
