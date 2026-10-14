package com.stella.rpg;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record StatSheet(int str, int agi, int vit, int dex, int intel, int luk) {
	public static final int CEILING = 1024;
	public static final StatSheet EMPTY = new StatSheet(0, 0, 0, 0, 0, 0);

	private static final Codec<Integer> VALUE = Codec.intRange(0, CEILING);
	public static final Codec<StatSheet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		VALUE.optionalFieldOf("str", 0).forGetter(StatSheet::str),
		VALUE.optionalFieldOf("agi", 0).forGetter(StatSheet::agi),
		VALUE.optionalFieldOf("vit", 0).forGetter(StatSheet::vit),
		VALUE.optionalFieldOf("dex", 0).forGetter(StatSheet::dex),
		VALUE.optionalFieldOf("int", 0).forGetter(StatSheet::intel),
		VALUE.optionalFieldOf("luk", 0).forGetter(StatSheet::luk)
	).apply(instance, StatSheet::new));

	private static final StreamCodec<ByteBuf, StatSheet> NETWORK_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, StatSheet::str,
		ByteBufCodecs.VAR_INT, StatSheet::agi,
		ByteBufCodecs.VAR_INT, StatSheet::vit,
		ByteBufCodecs.VAR_INT, StatSheet::dex,
		ByteBufCodecs.VAR_INT, StatSheet::intel,
		ByteBufCodecs.VAR_INT, StatSheet::luk,
		StatSheet::new
	);

	public static final AttachmentType<StatSheet> ATTACHMENT = AttachmentRegistry.create(
		StellaRpg.id("stats"),
		builder -> builder.persistent(CODEC).copyOnDeath()
			.syncWith(NETWORK_CODEC, AttachmentSyncPredicate.targetOnly())
	);

	public int get(final Stat stat) {
		return switch (stat) {
			case STR -> this.str;
			case AGI -> this.agi;
			case VIT -> this.vit;
			case DEX -> this.dex;
			case INT -> this.intel;
			case LUK -> this.luk;
		};
	}

	public StatSheet with(final Stat stat, final int value) {
		return switch (stat) {
			case STR -> new StatSheet(value, this.agi, this.vit, this.dex, this.intel, this.luk);
			case AGI -> new StatSheet(this.str, value, this.vit, this.dex, this.intel, this.luk);
			case VIT -> new StatSheet(this.str, this.agi, value, this.dex, this.intel, this.luk);
			case DEX -> new StatSheet(this.str, this.agi, this.vit, value, this.intel, this.luk);
			case INT -> new StatSheet(this.str, this.agi, this.vit, this.dex, value, this.luk);
			case LUK -> new StatSheet(this.str, this.agi, this.vit, this.dex, this.intel, value);
		};
	}

	public int allocated() {
		return this.str + this.agi + this.vit + this.dex + this.intel + this.luk;
	}
}
