package com.stella.spells;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A quick slot key press, sent to the server, which validates the number and uses the slot. */
public record QuickSlotUsePayload(int slot) implements CustomPacketPayload {
	public static final Type<QuickSlotUsePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "quick_slot_use"));
	public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, QuickSlotUsePayload> CODEC =
		ByteBufCodecs.VAR_INT.map(QuickSlotUsePayload::new, QuickSlotUsePayload::slot).cast();

	@Override
	public Type<QuickSlotUsePayload> type() {
		return TYPE;
	}
}
