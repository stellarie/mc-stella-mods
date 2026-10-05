package com.stella.tower;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a floor is made of: its walls', pillars' and upper floors' {@link TowerSkin}s, its lamps' look, and the
 * vanilla blocks of its ground, its stairs and its rails. A floor takes the theme of its archetype and its band
 * of ten floors (see {@link TowerArchetype#theme(int)}). Where a part lists several, each block draws one.
 */
public record TowerTheme(
	TowerSkin[] wall,
	TowerSkin pillar,
	TowerSkin[] floor,
	TowerLampSkin lamp,
	BlockState[] ground,
	BlockState runner,
	BlockState stair,
	BlockState step,
	BlockState rail,
	BlockState lantern,
	boolean crystals
) {
	/** The old floors' band of stone: overworld for the first twenty floors, then nether, end and overworld by tens. */
	static int oldBand(final int floor) {
		return floor <= 20 ? 0 : ((floor - 21) / 10) % 3;
	}

	/** Which of three looks a floor's band of ten takes, for the archetypes that cycle three. */
	static int band(final int floor) {
		return Math.floorMod((Math.max(floor, 1) - 1) / 10, 3);
	}

	/** The maze's look: a hall floor's and a labyrinth's. */
	static TowerTheme maze(final int floor) {
		return switch (oldBand(floor)) {
			case 1 -> new TowerTheme(skins(TowerSkin.NETHER_BRICKS), TowerSkin.RED_NETHER_BRICKS, skins(TowerSkin.POLISHED_BLACKSTONE_BRICKS),
				TowerLampSkin.SHROOMLIGHT,
				states(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
					Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()),
				Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICK_STAIRS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(),
				Blocks.NETHER_BRICK_WALL.defaultBlockState(), Blocks.SOUL_LANTERN.defaultBlockState(), false);
			case 2 -> new TowerTheme(skins(TowerSkin.END_STONE_BRICKS), TowerSkin.PURPUR_BLOCK, skins(TowerSkin.PURPUR_BLOCK),
				TowerLampSkin.PEARLESCENT_FROGLIGHT,
				states(Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
					Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.END_STONE.defaultBlockState()),
				Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.PURPUR_STAIRS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
				Blocks.END_STONE_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), false);
			default -> new TowerTheme(skins(TowerSkin.BLACKSTONE), TowerSkin.POLISHED_BLACKSTONE_BRICKS, skins(TowerSkin.POLISHED_DEEPSLATE),
				TowerLampSkin.SEA_LANTERN,
				states(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
					Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState()),
				Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
				Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), false);
		};
	}

	/** The great hall's looks: a deepslate cathedral, a gilded blackstone one, and a pale stone one. */
	static TowerTheme greatHall(final int floor) {
		return switch (band(floor)) {
			case 1 -> new TowerTheme(skins(TowerSkin.POLISHED_BLACKSTONE_BRICKS), TowerSkin.GILDED_BLACKSTONE, skins(TowerSkin.POLISHED_BLACKSTONE),
				TowerLampSkin.GLOWSTONE,
				states(Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()),
				Blocks.GILDED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState(),
				Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(),
				Blocks.SOUL_LANTERN.defaultBlockState(), false);
			case 2 -> new TowerTheme(skins(TowerSkin.QUARTZ_BRICKS), TowerSkin.STONE_BRICKS, skins(TowerSkin.STONE_BRICKS, TowerSkin.POLISHED_TUFF),
				TowerLampSkin.SEA_LANTERN,
				states(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState()),
				Blocks.MUD_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
				Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), false);
			default -> new TowerTheme(skins(TowerSkin.DEEPSLATE_TILES), TowerSkin.POLISHED_DEEPSLATE, skins(TowerSkin.DEEPSLATE_TILES, TowerSkin.POLISHED_DEEPSLATE),
				TowerLampSkin.SHROOMLIGHT,
				states(Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
				Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
				Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), false);
		};
	}

	/** The cavern's looks: amethyst in calcite, deep slate, and a sea cave. */
	static TowerTheme cavern(final int floor) {
		return switch (band(floor)) {
			case 1 -> new TowerTheme(skins(TowerSkin.COBBLED_DEEPSLATE, TowerSkin.TUFF, TowerSkin.SMOOTH_BASALT), TowerSkin.SMOOTH_BASALT,
				skins(TowerSkin.COBBLED_DEEPSLATE, TowerSkin.TUFF), TowerLampSkin.VERDANT_FROGLIGHT,
				states(Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()),
				Blocks.TUFF.defaultBlockState(), Blocks.COBBLED_DEEPSLATE_STAIRS.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
				Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState(), Blocks.SOUL_LANTERN.defaultBlockState(), true);
			case 2 -> new TowerTheme(skins(TowerSkin.PRISMARINE_BRICKS, TowerSkin.DARK_PRISMARINE), TowerSkin.DARK_PRISMARINE,
				skins(TowerSkin.DARK_PRISMARINE), TowerLampSkin.SEA_LANTERN,
				states(Blocks.PRISMARINE.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState()),
				Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(),
				Blocks.PRISMARINE_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), false);
			default -> new TowerTheme(skins(TowerSkin.CALCITE, TowerSkin.TUFF, TowerSkin.SMOOTH_BASALT), TowerSkin.AMETHYST_BLOCK,
				skins(TowerSkin.TUFF, TowerSkin.CALCITE), TowerLampSkin.PEARLESCENT_FROGLIGHT,
				states(Blocks.CALCITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState()),
				Blocks.CALCITE.defaultBlockState(), Blocks.TUFF_STAIRS.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
				Blocks.TUFF_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), true);
		};
	}

	private static TowerSkin[] skins(final TowerSkin... skins) {
		return skins;
	}

	private static BlockState[] states(final BlockState... states) {
		return states;
	}
}
