package com.stella.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** One mode's raid instance: which run it is, whether it is open, who hosts it, and how long it has run. */
public record RaidState(int run, boolean active, Optional<UUID> host, RaidMode mode, long elapsed, boolean sovereignRisen) {
	public static final Codec<RaidState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("run").forGetter(RaidState::run),
		Codec.BOOL.fieldOf("active").forGetter(RaidState::active),
		UUIDUtil.CODEC.optionalFieldOf("host").forGetter(RaidState::host),
		RaidMode.CODEC.fieldOf("mode").forGetter(RaidState::mode),
		Codec.LONG.fieldOf("elapsed").forGetter(RaidState::elapsed),
		Codec.BOOL.optionalFieldOf("sovereign_risen", false).forGetter(RaidState::sovereignRisen)
	).apply(instance, RaidState::new));

	/** The state of a mode whose raid has never run. */
	public static RaidState empty(final RaidMode mode) {
		return new RaidState(0, false, Optional.empty(), mode, 0L, false);
	}

	public RaidState withElapsed(final long ticks) {
		return new RaidState(run, active, host, mode, ticks, sovereignRisen);
	}

	/** The run's Sovereign has risen; it never rises again in this run, slain or not. */
	public RaidState withSovereignRisen() {
		return new RaidState(run, active, host, mode, elapsed, true);
	}
}
