package com.stella.ruins.world;

import java.util.List;

/** The waves of a wave objective, in order. */
public record WavePlan(List<Roster> waves) {
	public WavePlan {
		waves = List.copyOf(waves);
	}

	public static WavePlan of(final Roster... waves) {
		return new WavePlan(List.of(waves));
	}
}
