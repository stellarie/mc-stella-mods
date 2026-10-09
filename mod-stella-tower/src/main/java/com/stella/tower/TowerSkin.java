package com.stella.tower;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * The look of a tower wall block. Each value draws the wall with a vanilla block's model (see the blockstate),
 * so a floor's walls take its archetype's and band's stone while staying {@link TowerBlocks#TOWER_MAZE_WALL}.
 * The first value is the default: the look every wall had before skins.
 */
public enum TowerSkin implements StringRepresentable {
	BLACKSTONE,
	POLISHED_BLACKSTONE_BRICKS,
	POLISHED_BLACKSTONE,
	GILDED_BLACKSTONE,
	DEEPSLATE_TILES,
	DEEPSLATE_BRICKS,
	POLISHED_DEEPSLATE,
	COBBLED_DEEPSLATE,
	STONE_BRICKS,
	MOSSY_STONE_BRICKS,
	QUARTZ_BRICKS,
	TUFF,
	TUFF_BRICKS,
	POLISHED_TUFF,
	CALCITE,
	SMOOTH_BASALT,
	AMETHYST_BLOCK,
	NETHER_BRICKS,
	RED_NETHER_BRICKS,
	END_STONE_BRICKS,
	PURPUR_BLOCK,
	PRISMARINE_BRICKS,
	DARK_PRISMARINE,
	MUD_BRICKS,
	COBBLESTONE;

	private final String name = this.name().toLowerCase(Locale.ROOT);

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
