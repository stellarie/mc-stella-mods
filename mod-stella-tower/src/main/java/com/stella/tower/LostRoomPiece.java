package com.stella.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;

/**
 * The hut: a sealed box of tower wall, six long, three wide and three high inside, with an oak double
 * door. Inside: a blue portal one wide and two high in a lining of tower frame at the far end, a crafting
 * table with a candle, a chest of tower loot, an anvil, a furnace, and cobwebs.
 * <p>
 * Spots are in the piece's own frame: x along the length, z across, y up. The shell is x 0 and 7, z 0
 * and 4, y 0 and 4; the door is in the wall at z 0. States face as in a north-facing piece, where +z is
 * north, and the piece turns them with itself.
 */
public class LostRoomPiece extends ScatteredFeaturePiece {
	public static final int LENGTH = 8;
	public static final int WIDTH = 5;
	public static final int HEIGHT = 5;
	public static final int DOOR_X = 2;
	public static final int PORTAL_X = 6;
	public static final int PORTAL_Z = 2;
	public static final int CHEST_X = 1;
	public static final int CHEST_Z = 1;
	/** The deepest floor a hut's chest may be stocked as. */
	public static final int LOOT_FLOORS = 100;

	public LostRoomPiece(final RandomSource random, final int west, final int north) {
		this(west, 64, north, getRandomHorizontalDirection(random));
	}

	public LostRoomPiece(final int west, final int floor, final int north, final Direction facing) {
		super(LostRooms.PIECE, west, floor, north, LENGTH, HEIGHT, WIDTH, facing);
	}

	public LostRoomPiece(final CompoundTag tag) {
		super(LostRooms.PIECE, tag);
	}

	@Override
	public void postProcess(
		final WorldGenLevel level, final StructureManager structureManager, final ChunkGenerator generator, final RandomSource random,
		final BoundingBox chunkBB, final ChunkPos chunkPos, final BlockPos referencePos
	) {
		// The floor sinks into the ground's top layer.
		if (this.updateAverageGroundHeight(level, chunkBB, -1)) {
			build(level, chunkBB, random);
		}
	}

	/** Lays the hut where its box stands now: the seam a test places it by. */
	public void build(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource random) {
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		BlockState frame = TowerBlocks.TOWER_FRAME.defaultBlockState();
		this.generateBox(level, chunkBB, 0, 0, 0, LENGTH - 1, HEIGHT - 1, WIDTH - 1, wall, wall, false);
		this.generateAirBox(level, chunkBB, 1, 1, 1, LENGTH - 2, HEIGHT - 2, WIDTH - 2);
		for (int x = 0; x < LENGTH; x++) {
			for (int z = 0; z < WIDTH; z++) {
				this.fillColumnDown(level, Blocks.COBBLESTONE.defaultBlockState(), x, -1, z, chunkBB);
			}
		}
		door(level, chunkBB, DOOR_X, DoorHingeSide.LEFT);
		door(level, chunkBB, DOOR_X + 1, DoorHingeSide.RIGHT);
		// The far end: frame round the portal.
		for (int y = 1; y < HEIGHT - 1; y++) {
			for (int z = 1; z < WIDTH - 1; z++) {
				this.placeBlock(level, frame, PORTAL_X, y, z, chunkBB);
			}
		}
		this.placeBlock(level, frame, PORTAL_X, 0, PORTAL_Z, chunkBB);
		BlockState portal = TowerBlocks.TOWER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.Z);
		this.placeBlock(level, portal, PORTAL_X, 1, PORTAL_Z, chunkBB);
		this.placeBlock(level, portal, PORTAL_X, 2, PORTAL_Z, chunkBB);
		this.placeBlock(level, Blocks.CRAFTING_TABLE.defaultBlockState(), 1, 1, 3, chunkBB);
		this.placeBlock(level, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), 1, 2, 3, chunkBB);
		this.placeBlock(level, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.SOUTH), 3, 1, 3, chunkBB);
		this.placeBlock(level, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST), 4, 1, 3, chunkBB);
		this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), 1, 3, 2, chunkBB);
		this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), 5, 3, 1, chunkBB);
		this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), 4, 3, 3, chunkBB);
		this.placeBlock(level, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), CHEST_X, 1, CHEST_Z, chunkBB);
		stock(level, chunkBB, random);
	}

	private void door(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final DoorHingeSide hinge) {
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, hinge);
		this.placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), x, 1, 0, chunkBB);
		this.placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), x, 2, 0, chunkBB);
	}

	/** Stocks the chest as a tower chest of a random floor would be. */
	private void stock(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource random) {
		BlockPos at = this.getWorldPos(CHEST_X, 1, CHEST_Z);
		if (!chunkBB.isInside(at) || !(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
			return;
		}
		int floor = 1 + random.nextInt(LOOT_FLOORS);
		for (int i = 0; i < TowerChests.ROLLS; i++) {
			ItemStack stack = TowerChests.roll(level.getLevel(), floor, random).stack();
			if (!stack.isEmpty()) {
				chest.setItem(i, stack);
			}
		}
	}

	/** Where a spot of the piece's own frame lies in the world. */
	public BlockPos worldPos(final int x, final int y, final int z) {
		return this.getWorldPos(x, y, z).immutable();
	}
}
