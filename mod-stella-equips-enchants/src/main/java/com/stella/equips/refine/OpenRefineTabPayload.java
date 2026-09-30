package com.stella.equips.refine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: show the open anvil on its Refine tab. */
public record OpenRefineTabPayload() implements CustomPacketPayload {
	public static final OpenRefineTabPayload INSTANCE = new OpenRefineTabPayload();
	public static final Type<OpenRefineTabPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "open_refine_tab")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenRefineTabPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<OpenRefineTabPayload> type() {
		return TYPE;
	}
}
