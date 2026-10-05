package com.stella.ruins.site.wayshrine;

import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One block table of the shrine. The plan names a role (floor, wall, accent) and the palette gives the block. A
 * block with no cracked or mossy form (chiseled, slab, stairs) becomes the plain cracked or mossy block when decay
 * hits it.
 */
record WayshrinePalette(
	String id,
	BlockState floor,
	BlockState floorCracked,
	BlockState floorMossy,
	BlockState wall,
	BlockState shaft,
	BlockState wallCracked,
	BlockState wallMossy,
	BlockState accent,
	BlockState slab,
	BlockState stairs,
	BlockState foundation
) {
	static final WayshrinePalette STONE_BRICK = new WayshrinePalette(
		"stone_brick",
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
		Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
		Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
		Blocks.STONE_BRICK_SLAB.defaultBlockState(),
		Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
		Blocks.COBBLESTONE.defaultBlockState()
	);
	static final WayshrinePalette CALCITE = new WayshrinePalette(
		"calcite",
		Blocks.QUARTZ_BRICKS.defaultBlockState(),
		Blocks.TUFF_BRICKS.defaultBlockState(),
		Blocks.MOSS_BLOCK.defaultBlockState(),
		Blocks.CALCITE.defaultBlockState(),
		Blocks.QUARTZ_PILLAR.defaultBlockState(),
		Blocks.TUFF_BRICKS.defaultBlockState(),
		Blocks.MOSS_BLOCK.defaultBlockState(),
		Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(),
		Blocks.QUARTZ_SLAB.defaultBlockState(),
		Blocks.QUARTZ_STAIRS.defaultBlockState(),
		Blocks.CALCITE.defaultBlockState()
	);
	static final WayshrinePalette DEEPSLATE = new WayshrinePalette(
		"deepslate",
		Blocks.DEEPSLATE_TILES.defaultBlockState(),
		Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(),
		Blocks.MOSS_BLOCK.defaultBlockState(),
		Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
		Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
		Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
		Blocks.MOSS_BLOCK.defaultBlockState(),
		Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
		Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState(),
		Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState(),
		Blocks.COBBLED_DEEPSLATE.defaultBlockState()
	);

	static final List<WayshrinePalette> ALL = List.of(STONE_BRICK, CALCITE, DEEPSLATE);
}
