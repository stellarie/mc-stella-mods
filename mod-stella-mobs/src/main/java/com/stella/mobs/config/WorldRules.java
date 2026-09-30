package com.stella.mobs.config;

import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The world-shaped rules of threat scaling, all read from the {@code rolls} object of the config: where elites and
 * bosses may first appear, how the elite chance grows with distance, how far an elite or boss quiets the natural
 * spawns around it, and the flat threat level each biome adds. The code default is {@link #OFF}, so a rig that parses
 * its own config is never gated. The shipped file switches every rule on.
 */
public record WorldRules(
	double eliteMinDistance,
	double bossMinDistance,
	double eliteBonusPer100,
	double eliteBonusCap,
	double eliteQuietRadius,
	double bossQuietRadius,
	double quietFactor,
	Map<Identifier, Integer> biomeLevels
) {
	public static final WorldRules OFF = new WorldRules(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, Map.of());

	public WorldRules {
		biomeLevels = Map.copyOf(biomeLevels);
	}

	/** The flat threat levels a biome adds, zero for a biome the file does not list. */
	public int biomeLevel(final Identifier biome) {
		return this.biomeLevels.getOrDefault(biome, 0);
	}
}
