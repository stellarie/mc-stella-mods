package com.stella.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** The one raid instance: which run it is, whether it is open, who hosts it, its mode, and how long it has run. */
public record RaidState(int run, boolean active, Optional<UUID> host, RaidMode mode, long elapsed) {
	public static final RaidState EMPTY = new RaidState(0, false, Optional.empty(), RaidMode.NORMAL, 0L);
	public static final Codec<RaidState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("run").forGetter(RaidState::run),
		Codec.BOOL.fieldOf("active").forGetter(RaidState::active),
		UUIDUtil.CODEC.optionalFieldOf("host").forGetter(RaidState::host),
		Codec.STRING.xmap(RaidMode::valueOf, RaidMode::name).fieldOf("mode").forGetter(RaidState::mode),
		Codec.LONG.fieldOf("elapsed").forGetter(RaidState::elapsed)
	).apply(instance, RaidState::new));
}
