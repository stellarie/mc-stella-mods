package com.stella.tower;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * The kinds of biome floor. A floor of the tower is one of these, drawn from the seed and the floor number
 * ({@link FloorBiomes}). Each has a data biome of the same name, which holds its fog and its air.
 */
public enum FloorBiome {
	/** Open to a barrier roof: grass and mud, streams, tall trees, a ruined shrine, camps. */
	RAINY_FOREST("rainy_forest", true);

	private final String id;
	private final boolean openRoof;
	private final ResourceKey<Biome> key;

	FloorBiome(final String id, final boolean openRoof) {
		this.id = id;
		this.openRoof = openRoof;
		this.key = ResourceKey.create(Registries.BIOME, StellaTower.id("tower_" + id));
	}

	public String id() {
		return this.id;
	}

	/** Whether the room's roof is a barrier, so the sky shows through, rather than a wall. */
	public boolean openRoof() {
		return this.openRoof;
	}

	/** The data biome that paints the floor's fog and air. */
	public ResourceKey<Biome> key() {
		return this.key;
	}
}
