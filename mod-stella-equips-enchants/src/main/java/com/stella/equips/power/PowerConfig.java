package com.stella.equips.power;

import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

public record PowerConfig(
	int version,
	int levelCap,
	PointsPerLevel pointsPerLevel,
	Distance distance,
	Depth depth,
	Kills kills,
	MobLevel mobLevel,
	XpMultiplier xpMultiplier,
	Bands bands,
	Tracker tracker,
	Attributes attributes
) {
	public record PointsPerLevel(long base, long perLevel) {
	}

	public record Distance(double blocksPerPoint, long maxPoints) {
	}

	public record Depth(double blocksPerPoint, double minY, long maxPoints) {
	}

	public record Kills(long pointsPerKill, long pointsPerBoss, Set<Identifier> bossTypes) {
		public Kills {
			bossTypes = Set.copyOf(bossTypes);
		}

		public boolean isBoss(final Identifier entityType) {
			return entityType != null && this.bossTypes.contains(entityType);
		}
	}

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

	public record XpMultiplier(double evenFight, double perLevelGap, double min, double max) {
	}

	public record Bands(int redAbove, int yellowAbove, int orangeAbove, int greenWithin) {
	}

	public record Tracker(int scanIntervalTicks, int heartbeatTicks) {
	}

	public record Attributes(Survivability distance, Combat combat) {
	}

	public record Survivability(double maxHealthPerPoint, double armorPerLevel, double knockbackResistancePerLevel) {
	}

	public record Combat(double attackDamageMax, double attackSpeedMax) {
	}
}
