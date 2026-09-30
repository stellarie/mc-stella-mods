package com.stella.equips.power;

import java.util.Map;
import net.minecraft.resources.Identifier;

public record PowerConfig(
	int version,
	int levelCap,
	MobLevel mobLevel,
	Bands bands,
	Tracker tracker,
	Attributes attributes,
	Readout readout
) {
	public record MobLevel(
		double blocksPerLevel,
		int cap,
		int depthBonusCap,
		double depthBonusBlocks,
		double depthReferenceY,
		int levelCap,
		double otherDimensionScale,
		Map<Identifier, Double> dimensionScale
	) {
		public MobLevel {
			dimensionScale = Map.copyOf(dimensionScale);
		}

		public double scaleFor(final Identifier dimension) {
			return this.dimensionScale.getOrDefault(dimension, this.otherDimensionScale);
		}
	}

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
