package com.stella.equips.power;

public record PowerConfig(
	int version,
	int levelCap,
	Bands bands,
	Tracker tracker,
	Attributes attributes,
	Readout readout
) {
	public record Bands(int redAbove, int yellowAbove, int orangeAbove, int greenWithin) {
	}

	public record Tracker(int scanIntervalTicks, int heartbeatTicks) {
	}

	public record Attributes(
		double healthBase,
		double healthPerLevel
	) {
	}

	public record Readout(
		boolean enabledByDefault,
		double radius,
		int maxPerPlayer,
		double heightOffset,
		double viewRange
	) {
	}
}
