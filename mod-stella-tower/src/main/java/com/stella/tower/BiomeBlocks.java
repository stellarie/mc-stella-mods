package com.stella.tower;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The palette of a biome floor: each entry is a block of the world and the way a walking body counts it.
 * <p>
 * The plan and the volume hold a one-byte code. Only a full block, a block with no collision, or a block higher
 * than a block is used, so {@link TowerWalk#classify} reads a built floor the way {@link #walk} reads the plan.
 * A leaf is placed persistent, so it does not decay with no log near.
 */
public final class BiomeBlocks {
	public static final byte NONE = 0;
	public static final byte STONE = 1;
	public static final byte DIRT = 2;
	public static final byte GRASS = 3;
	public static final byte MUD = 4;
	public static final byte MOSS = 5;
	public static final byte PATH = 6;
	public static final byte PLANKS = 7;
	public static final byte WATER = 8;
	public static final byte OAK_LOG = 9;
	public static final byte DARK_OAK_LOG = 10;
	public static final byte BIRCH_LOG = 11;
	public static final byte OAK_LEAVES = 12;
	public static final byte DARK_OAK_LEAVES = 13;
	public static final byte BIRCH_LEAVES = 14;
	public static final byte FERN = 15;
	public static final byte FENCE = 16;
	public static final byte LANTERN = 17;
	public static final byte MOSSY_BRICKS = 18;
	public static final byte COBBLE = 19;
	public static final byte BARREL = 20;
	public static final byte BARRIER = 21;
	public static final byte OAK_LOG_X = 22;
	public static final byte OAK_LOG_Z = 23;
	public static final byte STONE_BRICKS = 24;
	public static final byte CHAIN = 25;
	public static final byte HANGING_LANTERN = 26;
	public static final byte SOUL_CAMPFIRE = 27;
	public static final byte SKULL = 28;
	public static final byte COUNT = 29;

	private static final BlockState[] STATES = new BlockState[COUNT];
	private static final byte[] WALK = new byte[COUNT];

	static {
		define(STONE, Blocks.STONE.defaultBlockState(), TowerWalk.SOLID);
		define(DIRT, Blocks.DIRT.defaultBlockState(), TowerWalk.SOLID);
		define(GRASS, Blocks.GRASS_BLOCK.defaultBlockState(), TowerWalk.SOLID);
		define(MUD, Blocks.MUD.defaultBlockState(), TowerWalk.SOLID);
		define(MOSS, Blocks.MOSS_BLOCK.defaultBlockState(), TowerWalk.SOLID);
		define(PATH, Blocks.DIRT_PATH.defaultBlockState(), TowerWalk.SOLID);
		define(PLANKS, Blocks.SPRUCE_PLANKS.defaultBlockState(), TowerWalk.SOLID);
		define(WATER, Blocks.WATER.defaultBlockState(), TowerWalk.OPEN);
		define(OAK_LOG, Blocks.OAK_LOG.defaultBlockState(), TowerWalk.SOLID);
		define(DARK_OAK_LOG, Blocks.DARK_OAK_LOG.defaultBlockState(), TowerWalk.SOLID);
		define(BIRCH_LOG, Blocks.BIRCH_LOG.defaultBlockState(), TowerWalk.SOLID);
		define(OAK_LEAVES, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), TowerWalk.SOLID);
		define(DARK_OAK_LEAVES, Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), TowerWalk.SOLID);
		define(BIRCH_LEAVES, Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), TowerWalk.SOLID);
		define(FERN, Blocks.FERN.defaultBlockState(), TowerWalk.OPEN);
		define(FENCE, Blocks.OAK_FENCE.defaultBlockState(), TowerWalk.TALL);
		define(LANTERN, Blocks.LANTERN.defaultBlockState(), TowerWalk.SOLID);
		define(MOSSY_BRICKS, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), TowerWalk.SOLID);
		define(COBBLE, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), TowerWalk.SOLID);
		define(BARREL, Blocks.BARREL.defaultBlockState(), TowerWalk.SOLID);
		define(BARRIER, Blocks.BARRIER.defaultBlockState(), TowerWalk.SOLID);
		define(OAK_LOG_X, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), TowerWalk.SOLID);
		define(OAK_LOG_Z, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), TowerWalk.SOLID);
		define(STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), TowerWalk.SOLID);
		define(CHAIN, Blocks.IRON_CHAIN.defaultBlockState(), TowerWalk.OPEN);
		define(HANGING_LANTERN, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), TowerWalk.SOLID);
		define(SOUL_CAMPFIRE, Blocks.SOUL_CAMPFIRE.defaultBlockState(), TowerWalk.SOLID);
		define(SKULL, Blocks.SKELETON_SKULL.defaultBlockState(), TowerWalk.SOLID);
	}

	private BiomeBlocks() {
	}

	private static void define(final byte code, final BlockState state, final byte walk) {
		STATES[code] = state;
		WALK[code] = walk;
	}

	/** The block of a palette entry. */
	public static BlockState state(final byte code) {
		return code > NONE && code < COUNT ? STATES[code] : Blocks.AIR.defaultBlockState();
	}

	/** How a walking body counts the entry. */
	public static byte walk(final byte code) {
		return code > NONE && code < COUNT ? WALK[code] : TowerWalk.OPEN;
	}

	/** Whether the entry is a ground to trap, to chest and to raise a mob on: bare, level, and full. */
	public static boolean ground(final byte code) {
		return code == GRASS || code == MUD || code == MOSS || code == PATH || code == DIRT || code == STONE || code == PLANKS;
	}

	/** Whether the entry gives light. */
	public static boolean lights(final byte code) {
		return code == LANTERN || code == HANGING_LANTERN || code == SOUL_CAMPFIRE;
	}
}
