package com.stella.equips.readout;

import com.stella.equips.power.PowerCurve;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * What the client draws for the mob its owner looks at. An entity id of {@link #CLEAR_ID}
 * means no mob is looked at. Absorption travels here because a client cannot read it from a mob.
 */
public record ReadoutPayload(
	int entityId,
	int threat,
	byte band,
	byte rank,
	boolean hostile,
	float health,
	float maxHealth,
	float absorption,
	String name
) implements CustomPacketPayload {
	public static final int CLEAR_ID = -1;
	public static final Type<ReadoutPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "readout")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ReadoutPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.INT, ReadoutPayload::entityId,
		ByteBufCodecs.INT, ReadoutPayload::threat,
		ByteBufCodecs.BYTE, ReadoutPayload::band,
		ByteBufCodecs.BYTE, ReadoutPayload::rank,
		ByteBufCodecs.BOOL, ReadoutPayload::hostile,
		ByteBufCodecs.FLOAT, ReadoutPayload::health,
		ByteBufCodecs.FLOAT, ReadoutPayload::maxHealth,
		ByteBufCodecs.FLOAT, ReadoutPayload::absorption,
		ByteBufCodecs.STRING_UTF8, ReadoutPayload::name,
		ReadoutPayload::new
	);

	public static ReadoutPayload clear() {
		return new ReadoutPayload(CLEAR_ID, 0, (byte) 0, (byte) 0, false, 0.0F, 0.0F, 0.0F, "");
	}

	public static ReadoutPayload of(
		final int entityId,
		final int threat,
		final PowerCurve.Band band,
		final ReadoutRank rank,
		final boolean hostile,
		final float health,
		final float maxHealth,
		final float absorption,
		final String name
	) {
		return new ReadoutPayload(
			entityId, threat, (byte) band.ordinal(), (byte) rank.ordinal(), hostile, health, maxHealth, absorption, name
		);
	}

	public boolean isClear() {
		return this.entityId == CLEAR_ID;
	}

	public PowerCurve.Band bandValue() {
		PowerCurve.Band[] bands = PowerCurve.Band.values();
		return bands[Math.floorMod(this.band, bands.length)];
	}

	public ReadoutRank rankValue() {
		ReadoutRank[] ranks = ReadoutRank.values();
		return ranks[Math.floorMod(this.rank, ranks.length)];
	}

	@Override
	public Type<ReadoutPayload> type() {
		return TYPE;
	}
}
