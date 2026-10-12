package com.stella.tower;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The stone a lost castle is built from. The biome at the middle of its ring of ruins picks it: snowy
 * biomes give the snow palette, sandy and desert biomes the sandstone palette, and every other biome the
 * green palette. Every block is a full cube and none damages a stander.
 */
public enum LostCastlePalette {
	/** Mossy stone brick with grass and flowers. */
	GREEN(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.GRASS_BLOCK, "minecraft:zombie"),
	/** Pale diorite with snow and packed ice. */
	SNOW(Blocks.POLISHED_DIORITE, Blocks.DIORITE, Blocks.POLISHED_DIORITE_STAIRS, Blocks.SNOW_BLOCK, "minecraft:stray"),
	/** Sandstone on sand. */
	SANDSTONE(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SAND, "minecraft:husk");

	private final Block wall;
	private final Block worn;
	private final Block stairs;
	private final Block ground;
	private final String flavour;

	LostCastlePalette(final Block wall, final Block worn, final Block stairs, final Block ground, final String flavour) {
		this.wall = wall;
		this.worn = worn;
		this.stairs = stairs;
		this.ground = ground;
		this.flavour = flavour;
	}

	/** The block of every wall, floor and ceiling. */
	public Block wall() {
		return this.wall;
	}

	/** The block that takes the place of a wall block that has decayed. */
	public Block worn() {
		return this.worn;
	}

	/** The block of every stair, in the castle and at its entrance. */
	public Block stairs() {
		return this.stairs;
	}

	/** The surface of the ring's yard. */
	public Block ground() {
		return this.ground;
	}

	/** The mob this palette adds to its spawner tables. */
	public String flavour() {
		return this.flavour;
	}

	/** The palette a biome gives a castle. */
	public static LostCastlePalette of(final Holder<Biome> biome) {
		if (biome.is(ConventionalBiomeTags.IS_SNOWY) || biome.is(ConventionalBiomeTags.IS_ICY)) {
			return SNOW;
		}
		if (biome.is(ConventionalBiomeTags.IS_DESERT) || biome.is(ConventionalBiomeTags.IS_SANDY)) {
			return SANDSTONE;
		}
		return GREEN;
	}
}
