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
	RAINY_FOREST("rainy_forest", true, TowerSkin.MOSSY_STONE_BRICKS, true),
	/** Roads and ruined houses, wells, farms and burnt trunks. */
	DESTROYED_VILLAGE("destroyed_village", true, TowerSkin.COBBLESTONE, true),
	/** A broken curtain wall, four towers and a keep round the portal. */
	DESTROYED_CASTLE("destroyed_castle", true, TowerSkin.DEEPSLATE_BRICKS, false),
	/** Fenced plots of graves, tombs and a chapel under dead trees. */
	ADVENTURERS_GRAVEYARD("adventurers_graveyard", true, TowerSkin.DEEPSLATE_TILES, false),
	/** Craters, trenches, siege wrecks and little fires on scorched ground. */
	SCORCHED_BATTLEFIELD("scorched_battlefield", true, TowerSkin.POLISHED_BLACKSTONE_BRICKS, false);

	private final String id;
	private final boolean openRoof;
	private final TowerSkin daisSkin;
	private final boolean gentle;
	private final ResourceKey<Biome> key;

	FloorBiome(final String id, final boolean openRoof, final TowerSkin daisSkin, final boolean gentle) {
		this.id = id;
		this.openRoof = openRoof;
		this.daisSkin = daisSkin;
		this.gentle = gentle;
		this.key = ResourceKey.create(Registries.BIOME, StellaTower.id("tower_" + id));
	}

	public String id() {
		return this.id;
	}

	/** Whether the room's roof is a barrier, so the sky shows through, rather than a wall. */
	public boolean openRoof() {
		return this.openRoof;
	}

	/** Whether floor 1 may be this biome: a gentle start. */
	public boolean gentle() {
		return this.gentle;
	}

	/** The stone the portal's stair is faced with. */
	public TowerSkin daisSkin() {
		return this.daisSkin;
	}

	/** The data biome that paints the floor's fog and air. */
	public ResourceKey<Biome> key() {
		return this.key;
	}
}
