package com.stella.end;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** What the End level remembers between saves. A later slice adds its own fields with defaults. */
public record EndState(boolean built) {
	public static final EndState EMPTY = new EndState(false);
	public static final Codec<EndState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("built", false).forGetter(EndState::built)
	).apply(instance, EndState::new));

	public EndState withBuilt() {
		return new EndState(true);
	}
}
