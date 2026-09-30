package com.stella.rpg.net;

import com.stella.rpg.StellaRpg;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A request to spend points on several stats at once. The server validates the length and every entry. */
public record AllocateBatchPayload(int[] amounts) implements CustomPacketPayload {
	public static final Type<AllocateBatchPayload> TYPE = new Type<>(StellaRpg.id("allocate_batch"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AllocateBatchPayload> CODEC = StreamCodec.ofMember(
		AllocateBatchPayload::write, AllocateBatchPayload::read
	);

	private static final int MAX_LENGTH = 64;

	private static void write(final AllocateBatchPayload payload, final RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(payload.amounts.length);
		for (int amount : payload.amounts) {
			buf.writeVarInt(amount);
		}
	}

	private static AllocateBatchPayload read(final RegistryFriendlyByteBuf buf) {
		int length = buf.readVarInt();
		if (length < 0 || length > MAX_LENGTH) {
			throw new DecoderException("Batch length " + length + " is outside [0, " + MAX_LENGTH + "]");
		}
		int[] amounts = new int[length];
		for (int i = 0; i < length; i++) {
			amounts[i] = buf.readVarInt();
		}
		return new AllocateBatchPayload(amounts);
	}

	@Override
	public Type<AllocateBatchPayload> type() {
		return TYPE;
	}
}
