package com.stella.end;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the End level remembers between saves. The clock counts the End's own ticks, and every long timer reads it:
 * the game clock barely moves in a test, and a test moves this one at will.
 */
public record EndState(boolean built, long clock, long sovereignDeadAt, long dragonDeadAt, boolean dragonDead) {
	/** The value of a death time that never happened. */
	public static final long NEVER = -1L;
	public static final EndState EMPTY = new EndState(false, 0L, NEVER, NEVER, false);
	public static final Codec<EndState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("built", false).forGetter(EndState::built),
		Codec.LONG.optionalFieldOf("clock", 0L).forGetter(EndState::clock),
		Codec.LONG.optionalFieldOf("sovereign_dead_at", NEVER).forGetter(EndState::sovereignDeadAt),
		Codec.LONG.optionalFieldOf("dragon_dead_at", NEVER).forGetter(EndState::dragonDeadAt),
		Codec.BOOL.optionalFieldOf("dragon_dead", false).forGetter(EndState::dragonDead)
	).apply(instance, EndState::new));

	public EndState withBuilt() {
		return new EndState(true, clock, sovereignDeadAt, dragonDeadAt, dragonDead);
	}

	public EndState withClock(final long next) {
		return new EndState(built, next, sovereignDeadAt, dragonDeadAt, dragonDead);
	}

	public EndState withSovereignDeadAt(final long at) {
		return new EndState(built, clock, at, dragonDeadAt, dragonDead);
	}

	/** The dragon died at {@code at}: no dragon stands until one revives or is summoned. */
	public EndState withDragonDead(final long at) {
		return new EndState(built, clock, sovereignDeadAt, at, true);
	}

	/** A dragon stands again. The time of the last death stays: the ban on rooftop spawns runs from it. */
	public EndState withDragonRisen() {
		return new EndState(built, clock, sovereignDeadAt, dragonDeadAt, false);
	}
}
