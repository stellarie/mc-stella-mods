package com.stella.nether;

import com.mojang.serialization.Codec;

/** A raid's difficulty. One raid of each mode can run at the same time. */
public enum RaidMode {
	NORMAL,
	HARD;

	public static final Codec<RaidMode> CODEC = Codec.STRING.xmap(RaidMode::valueOf, RaidMode::name);
}
