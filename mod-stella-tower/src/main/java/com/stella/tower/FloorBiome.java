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
	SCORCHED_BATTLEFIELD("scorched_battlefield", true, TowerSkin.POLISHED_BLACKSTONE_BRICKS, false),
	/** Giant trees, thickets, ponds and overgrown temples. */
	JUNGLE("jungle", true, TowerSkin.MOSSY_STONE_BRICKS, false),
	/** Mud, still water, dead trunks and stilt huts under a fog six blocks deep, crossed by a boardwalk. */
	MISTBOUND_MARSH("mistbound_marsh", true, TowerSkin.MUD_BRICKS, false),
	/** Lava lakes, magma and basalt columns under a closed roof. */
	BASALT_DELTAS("basalt_deltas", false, TowerSkin.POLISHED_BLACKSTONE_BRICKS, false),
	/** Drifts, powder snow, ice and a frost that builds on a body in the open. */
	ETERNAL_SNOWSTORM("eternal_snowstorm", true, TowerSkin.BLUE_ICE, false, Weather.SNOW),
	/** Dunes, cacti, ruins and an oasis under a gust that slows a body in the open. */
	DESERT_STORM("desert_storm", true, TowerSkin.CUT_SANDSTONE, false, Weather.SAND),
	/** Caves of tuff under a rock roof, with geodes of amethyst, stalagmites and pools. */
	AMETHYST_CAVE("amethyst_cave", false, TowerSkin.CALCITE, true),
	/** Rooms of stone brick cut from rock, joined by halls, some flooded, under a low roof. */
	SUNKEN_CATACOMBS("sunken_catacombs", false, TowerSkin.STONE_BRICKS, false);

	/** What blows across a floor of this biome, in the open. */
	public enum Weather {
		NONE,
		SNOW,
		SAND
	}

	/** The biomes a floor is drawn among. */
	public static final java.util.List<FloorBiome> DRAWN = java.util.List.of(values());

	private final Weather weather;
	private final String id;
	private final boolean openRoof;
	private final TowerSkin daisSkin;
	private final boolean gentle;
	private final ResourceKey<Biome> key;

	FloorBiome(final String id, final boolean openRoof, final TowerSkin daisSkin, final boolean gentle) {
		this(id, openRoof, daisSkin, gentle, Weather.NONE);
	}

	FloorBiome(final String id, final boolean openRoof, final TowerSkin daisSkin, final boolean gentle, final Weather weather) {
		this.weather = weather;
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

	public Weather weather() {
		return this.weather;
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
