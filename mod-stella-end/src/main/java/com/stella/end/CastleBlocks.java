package com.stella.end;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** The castle's palette and the few block states that need properties set by hand. */
final class CastleBlocks {
	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	static final BlockState BRICKS = Blocks.END_STONE_BRICKS.defaultBlockState();
	static final BlockState END_STONE = Blocks.END_STONE.defaultBlockState();
	static final BlockState PURPUR = Blocks.PURPUR_BLOCK.defaultBlockState();
	static final BlockState PILLAR = Blocks.PURPUR_PILLAR.defaultBlockState();
	static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();
	static final BlockState BRICK_WALL = Blocks.END_STONE_BRICK_WALL.defaultBlockState();
	static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
	static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
	static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
	static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();
	static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
	static final BlockState PANE = Blocks.GLASS_PANE.defaultBlockState();
	static final BlockState STAINED_PANE = Blocks.STAINED_GLASS_PANE.purple().defaultBlockState();
	static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState();
	static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
	static final BlockState DARK_PLANKS = Blocks.DARK_OAK_PLANKS.defaultBlockState();
	static final BlockState FENCE = Blocks.DARK_OAK_FENCE.defaultBlockState();
	static final BlockState BOOKSHELF = Blocks.BOOKSHELF.defaultBlockState();
	static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();
	static final BlockState SHORT_GRASS = Blocks.SHORT_GRASS.defaultBlockState();
	static final BlockState WATER = Blocks.WATER.defaultBlockState();
	static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
	static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	static final BlockState HAY = Blocks.HAY_BLOCK.defaultBlockState();
	static final BlockState CAULDRON = Blocks.CAULDRON.defaultBlockState();

	private CastleBlocks() {
	}

	static BlockState stair(final Block block, final Direction facing, final Half half) {
		return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	static BlockState slab(final Block block, final SlabType type) {
		return block.defaultBlockState().setValue(SlabBlock.TYPE, type);
	}

	static BlockState pillar(final Direction.Axis axis) {
		return Blocks.PURPUR_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	static BlockState candle(final int count) {
		return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, count).setValue(CandleBlock.LIT, true);
	}

	static BlockState chest(final Direction facing, final ChestType type) {
		return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, type);
	}
}
