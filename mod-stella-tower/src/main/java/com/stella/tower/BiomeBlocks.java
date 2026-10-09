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
	public static final byte COBBLESTONE = 29;
	public static final byte COARSE_DIRT = 30;
	public static final byte BLACKSTONE = 31;
	public static final byte COAL_BLOCK = 32;
	public static final byte DEEPSLATE_BRICKS = 33;
	public static final byte CRACKED_DEEPSLATE = 34;
	public static final byte POLISHED_ANDESITE = 35;
	public static final byte STRIPPED_SPRUCE = 36;
	public static final byte SPRUCE_LOG = 37;
	public static final byte HAY = 38;
	public static final byte SOUL_SOIL = 39;
	public static final byte SOUL_FIRE = 40;
	public static final byte MAGMA = 41;
	public static final byte COBBLE_WALL = 42;
	public static final byte ANVIL = 43;
	public static final byte JUNGLE_LOG = 44;
	public static final byte JUNGLE_LEAVES = 45;
	public static final byte PODZOL = 46;
	public static final byte BROWN_MUSHROOM = 47;
	public static final byte RED_MUSHROOM = 48;
	public static final byte SEA_LANTERN = 49;
	public static final byte LAVA = 50;
	public static final byte POWDER_SNOW = 51;
	public static final byte SNOW_BLOCK = 52;
	public static final byte PACKED_ICE = 53;
	public static final byte BLUE_ICE = 54;
	public static final byte WHITE_WOOL = 55;
	public static final byte SAND = 56;
	public static final byte RED_SAND = 57;
	public static final byte SANDSTONE = 58;
	public static final byte CUT_SANDSTONE = 59;
	public static final byte CACTUS = 60;
	public static final byte SOUL_SAND = 61;
	public static final byte BASALT = 62;
	public static final byte NETHER_BRICK_FENCE = 63;
	public static final byte SOUL_LANTERN = 64;
	public static final byte TUFF = 65;
	public static final byte CALCITE = 66;
	public static final byte SMOOTH_BASALT = 67;
	public static final byte AMETHYST_BLOCK = 68;
	public static final byte BUDDING_AMETHYST = 69;
	public static final byte DRIPSTONE_BLOCK = 70;
	public static final byte BONE_BLOCK = 71;
	public static final byte COUNT = 72;

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
		define(COBBLESTONE, Blocks.COBBLESTONE.defaultBlockState(), TowerWalk.SOLID);
		define(COARSE_DIRT, Blocks.COARSE_DIRT.defaultBlockState(), TowerWalk.SOLID);
		define(BLACKSTONE, Blocks.BLACKSTONE.defaultBlockState(), TowerWalk.SOLID);
		define(COAL_BLOCK, Blocks.COAL_BLOCK.defaultBlockState(), TowerWalk.SOLID);
		define(DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), TowerWalk.SOLID);
		define(CRACKED_DEEPSLATE, Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), TowerWalk.SOLID);
		define(POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE.defaultBlockState(), TowerWalk.SOLID);
		define(STRIPPED_SPRUCE, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), TowerWalk.SOLID);
		define(SPRUCE_LOG, Blocks.SPRUCE_LOG.defaultBlockState(), TowerWalk.SOLID);
		define(HAY, Blocks.HAY_BLOCK.defaultBlockState(), TowerWalk.SOLID);
		define(SOUL_SOIL, Blocks.SOUL_SOIL.defaultBlockState(), TowerWalk.SOLID);
		define(SOUL_FIRE, Blocks.SOUL_FIRE.defaultBlockState(), TowerWalk.OPEN);
		define(MAGMA, Blocks.MAGMA_BLOCK.defaultBlockState(), TowerWalk.SOLID);
		define(COBBLE_WALL, Blocks.COBBLESTONE_WALL.defaultBlockState(), TowerWalk.TALL);
		define(ANVIL, Blocks.CHIPPED_ANVIL.defaultBlockState(), TowerWalk.SOLID);
		define(JUNGLE_LOG, Blocks.JUNGLE_LOG.defaultBlockState(), TowerWalk.SOLID);
		define(JUNGLE_LEAVES, Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), TowerWalk.SOLID);
		define(PODZOL, Blocks.PODZOL.defaultBlockState(), TowerWalk.SOLID);
		define(BROWN_MUSHROOM, Blocks.BROWN_MUSHROOM.defaultBlockState(), TowerWalk.OPEN);
		define(RED_MUSHROOM, Blocks.RED_MUSHROOM.defaultBlockState(), TowerWalk.OPEN);
		define(SEA_LANTERN, Blocks.SEA_LANTERN.defaultBlockState(), TowerWalk.SOLID);
		define(LAVA, Blocks.LAVA.defaultBlockState(), TowerWalk.TALL);
		define(POWDER_SNOW, Blocks.POWDER_SNOW.defaultBlockState(), TowerWalk.TALL);
		define(SNOW_BLOCK, Blocks.SNOW_BLOCK.defaultBlockState(), TowerWalk.SOLID);
		define(PACKED_ICE, Blocks.PACKED_ICE.defaultBlockState(), TowerWalk.SOLID);
		define(BLUE_ICE, Blocks.BLUE_ICE.defaultBlockState(), TowerWalk.SOLID);
		define(WHITE_WOOL, Blocks.WOOL.white().defaultBlockState(), TowerWalk.SOLID);
		define(SAND, Blocks.SAND.defaultBlockState(), TowerWalk.SOLID);
		define(RED_SAND, Blocks.RED_SAND.defaultBlockState(), TowerWalk.SOLID);
		define(SANDSTONE, Blocks.SANDSTONE.defaultBlockState(), TowerWalk.SOLID);
		define(CUT_SANDSTONE, Blocks.CUT_SANDSTONE.defaultBlockState(), TowerWalk.SOLID);
		define(CACTUS, Blocks.CACTUS.defaultBlockState(), TowerWalk.SOLID);
		define(SOUL_SAND, Blocks.SOUL_SAND.defaultBlockState(), TowerWalk.SOLID);
		define(BASALT, Blocks.BASALT.defaultBlockState(), TowerWalk.SOLID);
		define(NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_FENCE.defaultBlockState(), TowerWalk.TALL);
		define(SOUL_LANTERN, Blocks.SOUL_LANTERN.defaultBlockState(), TowerWalk.SOLID);
		define(TUFF, Blocks.TUFF.defaultBlockState(), TowerWalk.SOLID);
		define(CALCITE, Blocks.CALCITE.defaultBlockState(), TowerWalk.SOLID);
		define(SMOOTH_BASALT, Blocks.SMOOTH_BASALT.defaultBlockState(), TowerWalk.SOLID);
		define(AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState(), TowerWalk.SOLID);
		define(BUDDING_AMETHYST, Blocks.BUDDING_AMETHYST.defaultBlockState(), TowerWalk.SOLID);
		define(DRIPSTONE_BLOCK, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), TowerWalk.SOLID);
		define(BONE_BLOCK, Blocks.BONE_BLOCK.defaultBlockState(), TowerWalk.SOLID);
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
		return code == GRASS || code == MUD || code == MOSS || code == PATH || code == DIRT || code == STONE || code == PLANKS || code == COBBLE
			|| code == COBBLESTONE || code == COARSE_DIRT || code == BLACKSTONE || code == STONE_BRICKS || code == DEEPSLATE_BRICKS || code == PODZOL
			|| code == SNOW_BLOCK || code == PACKED_ICE || code == SAND || code == RED_SAND || code == SANDSTONE || code == CUT_SANDSTONE || code == BASALT || code == TUFF || code == CALCITE;
	}

	/** Whether the entry is a block that shuts out the light, so that grass under it would die. */
	public static boolean opaque(final byte code) {
		return switch (code) {
			case NONE, WATER, FERN, FENCE, LANTERN, HANGING_LANTERN, SOUL_CAMPFIRE, SKULL, SOUL_FIRE, CHAIN, COBBLE_WALL, ANVIL, BARRIER,
				OAK_LEAVES, DARK_OAK_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES, BROWN_MUSHROOM, RED_MUSHROOM, LAVA, POWDER_SNOW, CACTUS, NETHER_BRICK_FENCE, SOUL_LANTERN -> false;
			default -> true;
		};
	}

	/** Whether the entry gives light. */
	public static boolean lights(final byte code) {
		return code == LANTERN || code == HANGING_LANTERN || code == SOUL_CAMPFIRE || code == SOUL_FIRE || code == SEA_LANTERN || code == LAVA || code == SOUL_LANTERN;
	}
}
