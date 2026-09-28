package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A rune: its element, and the floor it dropped on, which sets how strong it is. */
public record RuneData(TowerElement element, int floor) {
	public static final Codec<RuneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		TowerElement.CODEC.fieldOf("element").forGetter(RuneData::element),
		Codec.INT.fieldOf("floor").forGetter(RuneData::floor)
	).apply(instance, RuneData::new));
	public static final StreamCodec<ByteBuf, RuneData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT.map(index -> TowerElement.values()[index], Enum::ordinal), RuneData::element,
		ByteBufCodecs.VAR_INT, RuneData::floor,
		RuneData::new
	);
}
