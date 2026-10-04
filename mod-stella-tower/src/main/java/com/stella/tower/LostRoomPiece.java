package com.stella.tower;

import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The lost castle: a ruined keep of tower wall on a platform above the ground. A broken curtain wall with
 * an oak double gate, three ruined corner towers and a fallen fourth, a courtyard of moss and rubble, and two
 * halls behind it, each nine by nine and five high inside, under roofs with holes in them. The west hall
 * holds the blue portal, one wide and two high in a lining of tower frame, and the workstations: a crafting
 * table with a candle, a chest of tower loot, an anvil and a furnace.
 * <p>
 * Spots are in the piece's own frame: x along the front, z from the gate inward, y up from the floor. States
 * face as in a north-facing piece, where +z is north, and the piece turns them with itself. What is ruined
 * is drawn from the piece's own spot, so every chunk the castle crosses draws the same ruin.
 */
public class LostRoomPiece extends StructurePiece {
	public static final int LENGTH = 31;
	public static final int WIDTH = 27;
	public static final int HEIGHT = 11;
	/** The curtain wall's height above the floor, merlons aside. */
	public static final int WALL_HEIGHT = 4;
	public static final int TOWER = 5;
	public static final int TOWER_LOW = 6;
	public static final int TOWER_HIGH = 9;
	/** A hall's inside: nine by nine, five high, its roof on the sixth layer. */
	public static final int HALL_INNER = 9;
	public static final int HALL_HEIGHT = 5;
	public static final int HALL_Z = 11;
	/** The west corners of the two halls' outer walls. */
	public static final int PORTAL_HALL_X = 3;
	public static final int STORE_HALL_X = 15;
	public static final int GATE_X = 15;
	public static final int PORTAL_X = PORTAL_HALL_X + 1 + HALL_INNER / 2;
	public static final int PORTAL_Z = HALL_Z + HALL_INNER;
	public static final int CHEST_X = PORTAL_HALL_X + HALL_INNER;
	public static final int CHEST_Z = HALL_Z + 3;
	/** The share of a hall's roof that has fallen in, in hundredths. */
	public static final int ROOF_GAPS = 22;
	/** The courtyard the roaming mobs keep to: x and z, low and high, inclusive. */
	public static final int[] YARD = {6, 3, 24, 8};
	/** The deepest floor the chest may be stocked as. */
	public static final int LOOT_FLOORS = 100;
	private static final long SALT = 0x43415354L;

	private final long ruinSeed;

	public LostRoomPiece(final RandomSource random, final int west, final int floor, final int north) {
		this(west, floor, north, Direction.Plane.HORIZONTAL.getRandomDirection(random));
	}

	public LostRoomPiece(final int west, final int floor, final int north, final Direction facing) {
		super(LostRooms.PIECE, 0, makeBoundingBox(west, floor, north, facing, LENGTH, HEIGHT, WIDTH));
		this.setOrientation(facing);
		this.ruinSeed = SALT ^ BlockPos.asLong(west, floor, north);
	}

	public LostRoomPiece(final CompoundTag tag) {
		super(LostRooms.PIECE, tag);
		this.ruinSeed = SALT ^ BlockPos.asLong(this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ());
	}

	@Override
	protected void addAdditionalSaveData(final StructurePieceSerializationContext context, final CompoundTag tag) {
	}

	/**
	 * The floor for a castle over this ground: one block above its highest column. {@code ground} gives a
	 * column's top solid y; columns are read every {@code step} blocks, and the edges always.
	 */
	public static int floorOver(final int minX, final int minZ, final int maxX, final int maxZ, final int step, final IntBinaryOperator ground) {
		int highest = Integer.MIN_VALUE;
		for (int x = minX; ; x = Math.min(maxX, x + step)) {
			for (int z = minZ; ; z = Math.min(maxZ, z + step)) {
				highest = Math.max(highest, ground.applyAsInt(x, z));
				if (z == maxZ) {
					break;
				}
			}
			if (x == maxX) {
				break;
			}
		}
		return highest + 1;
	}

	@Override
	public void postProcess(
		final WorldGenLevel level, final StructureManager structureManager, final ChunkGenerator generator, final RandomSource random,
		final BoundingBox chunkBB, final ChunkPos chunkPos, final BlockPos referencePos
	) {
		build(level, chunkBB);
		BlockPos portal = portalPos();
		if (chunkBB.isInside(portal)) {
			LostCastles.found(level.getLevel().dimension(), this);
		}
	}

	/** The lower block of the castle's portal. */
	public BlockPos portalPos() {
		return worldPos(PORTAL_X, 1, PORTAL_Z);
	}

	public Direction facing() {
		return this.getOrientation();
	}

	/** Lays the castle where its box stands: the seam a test places it by. */
	public void build(final WorldGenLevel level, final BoundingBox chunkBB) {
		RandomSource ruin = RandomSource.create(this.ruinSeed);
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		this.generateBox(level, chunkBB, 0, 0, 0, LENGTH - 1, 0, WIDTH - 1, wall, wall, false);
		this.generateAirBox(level, chunkBB, 0, 1, 0, LENGTH - 1, HEIGHT - 1, WIDTH - 1);
		for (int x = 0; x < LENGTH; x++) {
			for (int z = 0; z < WIDTH; z++) {
				this.fillColumnDown(level, wall, x, -1, z, chunkBB);
			}
		}
		curtain(level, chunkBB, ruin);
		gate(level, chunkBB);
		tower(level, chunkBB, ruin, 0, 0, Direction.SOUTH);
		tower(level, chunkBB, ruin, LENGTH - TOWER, 0, Direction.SOUTH);
		tower(level, chunkBB, ruin, LENGTH - TOWER, WIDTH - TOWER, Direction.WEST);
		fallenTower(level, chunkBB, ruin);
		hall(level, chunkBB, ruin, PORTAL_HALL_X);
		hall(level, chunkBB, ruin, STORE_HALL_X);
		portalHall(level, chunkBB);
		storeHall(level, chunkBB, ruin);
		yard(level, chunkBB, ruin);
		vines(level, chunkBB, ruin);
	}

	private static boolean inTower(final int x, final int z) {
		boolean west = x < TOWER;
		boolean east = x >= LENGTH - TOWER;
		boolean south = z < TOWER;
		boolean north = z >= WIDTH - TOWER;
		return (west || east) && (south || north);
	}

	private static boolean inGate(final int x) {
		return x >= GATE_X - 2 && x <= GATE_X + 3;
	}

	/** The curtain wall: merlons on every other block, and three breaches that rubble spills out of. */
	private void curtain(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin) {
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		int[][] breaches = {
			{TOWER + 2 + ruin.nextInt(4), 0}, {LENGTH - 1, TOWER + 3 + ruin.nextInt(8)}, {TOWER + 4 + ruin.nextInt(12), WIDTH - 1}
		};
		for (int x = 0; x < LENGTH; x++) {
			for (int z = 0; z < WIDTH; z++) {
				boolean edge = x == 0 || z == 0 || x == LENGTH - 1 || z == WIDTH - 1;
				if (!edge || inTower(x, z) || z == 0 && inGate(x)) {
					continue;
				}
				int top = WALL_HEIGHT + ((x + z) % 2 == 0 ? 1 : 0);
				for (int[] breach : breaches) {
					int along = breach[1] == 0 || breach[1] == WIDTH - 1 ? Math.abs(x - breach[0]) : Math.abs(z - breach[1]);
					boolean onSide = breach[1] == 0 ? z == 0 : breach[1] == WIDTH - 1 && breach[0] != LENGTH - 1 ? z == WIDTH - 1 : x == breach[0];
					if (onSide && along <= 1) {
						top = along == 0 ? 0 : 1 + ruin.nextInt(2);
					}
				}
				top = Math.max(0, top - (ruin.nextInt(6) == 0 ? 1 : 0));
				for (int y = 1; y <= top; y++) {
					this.placeBlock(level, wall, x, y, z, chunkBB);
				}
			}
		}
		for (int[] breach : breaches) {
			int x = Math.min(LENGTH - 2, Math.max(1, breach[0] == LENGTH - 1 ? LENGTH - 2 : breach[0]));
			int z = Math.min(WIDTH - 2, Math.max(1, breach[1] == 0 ? 1 : breach[1] == WIDTH - 1 ? WIDTH - 2 : breach[1]));
			rubble(level, chunkBB, ruin, x, z, 4);
		}
	}

	/** The gatehouse: a higher stretch of wall, the oak double door, and a lamp over it. */
	private void gate(final WorldGenLevel level, final BoundingBox chunkBB) {
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		for (int x = GATE_X - 2; x <= GATE_X + 3; x++) {
			for (int y = 1; y <= WALL_HEIGHT + 2; y++) {
				boolean opening = (x == GATE_X || x == GATE_X + 1) && y <= 2;
				boolean merlon = y == WALL_HEIGHT + 2 && (x - GATE_X) % 2 != 0;
				if (!opening && !merlon) {
					this.placeBlock(level, wall, x, y, 0, chunkBB);
				}
			}
		}
		this.placeBlock(level, TowerBlocks.TOWER_LAMP.defaultBlockState(), GATE_X, 3, 0, chunkBB);
		this.placeBlock(level, TowerBlocks.TOWER_LAMP.defaultBlockState(), GATE_X + 1, 3, 0, chunkBB);
		door(level, chunkBB, GATE_X, 0, DoorHingeSide.LEFT);
		door(level, chunkBB, GATE_X + 1, 0, DoorHingeSide.RIGHT);
	}

	/** A hollow corner tower, its top broken to a jagged line, a doorway onto the yard. */
	private void tower(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin, final int x0, final int z0, final Direction door) {
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		int fallen = ruin.nextInt(4);
		for (int dx = 0; dx < TOWER; dx++) {
			for (int dz = 0; dz < TOWER; dz++) {
				boolean ring = dx == 0 || dz == 0 || dx == TOWER - 1 || dz == TOWER - 1;
				if (!ring) {
					continue;
				}
				int side = dx == 0 ? 0 : dz == 0 ? 1 : dx == TOWER - 1 ? 2 : 3;
				int top = side == fallen ? TOWER_LOW - 2 + ruin.nextInt(2) : TOWER_LOW + ruin.nextInt(TOWER_HIGH - TOWER_LOW + 1);
				for (int y = 1; y <= top; y++) {
					this.placeBlock(level, wall, x0 + dx, y, z0 + dz, chunkBB);
				}
			}
		}
		// The doorway faces the yard.
		int doorX = door == Direction.WEST ? x0 : x0 + TOWER / 2;
		int doorZ = door == Direction.WEST ? z0 + TOWER / 2 : z0 + TOWER - 1;
		for (int y = 1; y <= 2; y++) {
			this.placeBlock(level, Blocks.AIR.defaultBlockState(), doorX, y, doorZ, chunkBB);
		}
		this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), x0 + 1, TOWER_LOW - 1, z0 + 1, chunkBB);
		this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), x0 + TOWER - 2, 2, z0 + TOWER - 2, chunkBB);
		if (door == Direction.SOUTH) {
			chest(level, chunkBB, x0 + 1, z0 + 1, Direction.NORTH);
		}
	}

	/** The fourth corner's tower lies in a ring of stumps and rubble. */
	private void fallenTower(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin) {
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		int z0 = WIDTH - TOWER;
		for (int dx = 0; dx < TOWER; dx++) {
			for (int dz = 0; dz < TOWER; dz++) {
				boolean ring = dx == 0 || dz == 0 || dx == TOWER - 1 || dz == TOWER - 1;
				int top = ring ? ruin.nextInt(3) : 0;
				for (int y = 1; y <= top; y++) {
					this.placeBlock(level, wall, dx, y, z0 + dz, chunkBB);
				}
			}
		}
		rubble(level, chunkBB, ruin, TOWER + 1, z0 + 1, 6);
	}

	/** A hall's walls and its broken roof; a three-high arch in the middle of its yard side. */
	private void hall(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin, final int x0) {
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		int size = HALL_INNER + 2;
		int roof = HALL_HEIGHT + 1;
		this.generateAirBox(level, chunkBB, x0 + 1, 1, HALL_Z + 1, x0 + HALL_INNER, HALL_HEIGHT, HALL_Z + HALL_INNER);
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				boolean ring = dx == 0 || dz == 0 || dx == size - 1 || dz == size - 1;
				if (ring) {
					// Whole to the roof; only the crown breaks.
					int top = roof + (ruin.nextInt(3) == 0 ? 0 : 1);
					for (int y = 1; y <= top; y++) {
						this.placeBlock(level, wall, x0 + dx, y, HALL_Z + dz, chunkBB);
					}
				} else if (ruin.nextInt(100) >= ROOF_GAPS) {
					this.placeBlock(level, wall, x0 + dx, roof, HALL_Z + dz, chunkBB);
				}
			}
		}
		int arch = x0 + size / 2 - 1;
		for (int x = arch; x <= arch + 1; x++) {
			for (int y = 1; y <= 3; y++) {
				this.placeBlock(level, Blocks.AIR.defaultBlockState(), x, y, HALL_Z, chunkBB);
			}
		}
		this.placeBlock(level, TowerBlocks.TOWER_LAMP.defaultBlockState(), arch, 4, HALL_Z, chunkBB);
		this.placeBlock(level, TowerBlocks.TOWER_LAMP.defaultBlockState(), arch + 1, 4, HALL_Z, chunkBB);
		int inX = x0 + 1;
		int inZ = HALL_Z + 1;
		this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), inX, HALL_HEIGHT, inZ, chunkBB);
		this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), inX + HALL_INNER - 1, HALL_HEIGHT, inZ + HALL_INNER - 1, chunkBB);
		this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), inX + HALL_INNER - 1, HALL_HEIGHT, inZ, chunkBB);
		// What fell through the roof.
		for (int i = 0; i < 3; i++) {
			placeRubble(level, chunkBB, ruin, inX + 1 + ruin.nextInt(HALL_INNER - 2), 1, inZ + 1 + ruin.nextInt(HALL_INNER - 4));
		}
	}

	/** The west hall: the portal on its back wall, the workstations along its west wall. */
	private void portalHall(final WorldGenLevel level, final BoundingBox chunkBB) {
		BlockState frame = TowerBlocks.TOWER_FRAME.defaultBlockState();
		for (int y = 1; y <= 3; y++) {
			this.placeBlock(level, frame, PORTAL_X - 1, y, PORTAL_Z, chunkBB);
			this.placeBlock(level, frame, PORTAL_X + 1, y, PORTAL_Z, chunkBB);
		}
		this.placeBlock(level, frame, PORTAL_X, 3, PORTAL_Z, chunkBB);
		this.placeBlock(level, frame, PORTAL_X, 0, PORTAL_Z, chunkBB);
		BlockState portal = TowerBlocks.TOWER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
		this.placeBlock(level, portal, PORTAL_X, 1, PORTAL_Z, chunkBB);
		this.placeBlock(level, portal, PORTAL_X, 2, PORTAL_Z, chunkBB);
		int west = PORTAL_HALL_X + 1;
		this.placeBlock(level, Blocks.CRAFTING_TABLE.defaultBlockState(), west, 1, HALL_Z + 3, chunkBB);
		this.placeBlock(level, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), west, 2, HALL_Z + 3, chunkBB);
		this.placeBlock(level, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.EAST), west, 1, HALL_Z + 5, chunkBB);
		this.placeBlock(level, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.NORTH), west, 1, HALL_Z + 7, chunkBB);
		this.placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), CHEST_X, 1, HALL_Z + 8, chunkBB);
		chest(level, chunkBB, CHEST_X, CHEST_Z, Direction.WEST);
	}

	/** The east hall: a store gone to ruin, with shelves, barrels, and a lantern. */
	private void storeHall(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin) {
		int inX = STORE_HALL_X + 1;
		int back = HALL_Z + HALL_INNER;
		for (int x = inX; x < inX + HALL_INNER; x++) {
			int height = ruin.nextInt(4);
			for (int y = 1; y <= height; y++) {
				this.placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), x, y, back, chunkBB);
			}
		}
		this.placeBlock(level, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), inX + HALL_INNER - 1, 1, HALL_Z + 2, chunkBB);
		this.placeBlock(level, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), inX + HALL_INNER - 1, 1, HALL_Z + 3, chunkBB);
		this.placeBlock(level, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), inX + HALL_INNER - 1, 2, HALL_Z + 2, chunkBB);
		this.placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), inX, 1, HALL_Z + 2, chunkBB);
		chest(level, chunkBB, inX, HALL_Z + 5, Direction.EAST);
	}

	/** The courtyard: moss over the stones, rubble, and a broken well. */
	private void yard(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin) {
		for (int x = 1; x < LENGTH - 1; x++) {
			for (int z = 1; z < HALL_Z; z++) {
				if (!inTower(x, z) && !getBlock(level, x, 1, z, chunkBB).isSolid() && ruin.nextInt(5) == 0) {
					if (ruin.nextBoolean()) {
						this.placeBlock(level, Blocks.MOSS_BLOCK.defaultBlockState(), x, 0, z, chunkBB);
					} else {
						this.placeBlock(level, Blocks.MOSS_CARPET.defaultBlockState(), x, 1, z, chunkBB);
					}
				}
			}
		}
		int wellX = GATE_X;
		int wellZ = 6;
		BlockState stone = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 2; dz++) {
				boolean ring = dx == -1 || dz == -1 || dx == 2 || dz == 2;
				this.placeBlock(level, ring ? stone : Blocks.WATER.defaultBlockState(), wellX + dx, 0, wellZ + dz, chunkBB);
				if (ring && ruin.nextInt(3) != 0) {
					this.placeBlock(level, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), wellX + dx, 1, wellZ + dz, chunkBB);
				}
			}
		}
		for (int i = 0; i < 5; i++) {
			rubble(level, chunkBB, ruin, 2 + ruin.nextInt(LENGTH - 4), 2 + ruin.nextInt(HALL_Z - 4), 3);
		}
	}

	/** Vines down the walls' inner faces, and down the halls' fronts. */
	private void vines(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin) {
		for (int i = 0; i < 14; i++) {
			int side = ruin.nextInt(4);
			int x;
			int z;
			Direction toWall;
			switch (side) {
				case 0 -> {
					x = 1;
					z = TOWER + ruin.nextInt(WIDTH - 2 * TOWER);
					toWall = Direction.WEST;
				}
				case 1 -> {
					x = LENGTH - 2;
					z = TOWER + ruin.nextInt(WIDTH - 2 * TOWER);
					toWall = Direction.EAST;
				}
				case 2 -> {
					x = TOWER + ruin.nextInt(LENGTH - 2 * TOWER);
					z = 1;
					toWall = Direction.SOUTH;
				}
				default -> {
					x = PORTAL_HALL_X + ruin.nextInt(STORE_HALL_X + HALL_INNER + 2 - PORTAL_HALL_X);
					z = HALL_Z - 1;
					toWall = Direction.NORTH;
				}
			}
			BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.PROPERTY_BY_DIRECTION.get(toWall), true);
			int top = WALL_HEIGHT - ruin.nextInt(2);
			int length = 1 + ruin.nextInt(3);
			for (int y = top; y > top - length && y >= 1; y--) {
				if (getBlock(level, x, y, z, chunkBB).isAir() && getBlock(level, x + toWall.getStepX(), y, z - toWall.getStepZ(), chunkBB).isSolid()) {
					this.placeBlock(level, vine, x, y, z, chunkBB);
				}
			}
		}
	}

	/** A pile of broken stone, at most {@code size} blocks, on open floor. */
	private void rubble(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin, final int x, final int z, final int size) {
		for (int i = 0; i < size; i++) {
			int px = Math.max(1, Math.min(LENGTH - 2, x + ruin.nextInt(3) - 1));
			int pz = Math.max(1, Math.min(WIDTH - 2, z + ruin.nextInt(3) - 1));
			placeRubble(level, chunkBB, ruin, px, getBlock(level, px, 1, pz, chunkBB).isAir() ? 1 : 2, pz);
		}
	}

	private void placeRubble(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin, final int x, final int y, final int z) {
		if (!getBlock(level, x, y, z, chunkBB).isAir() || !getBlock(level, x, y - 1, z, chunkBB).isSolid()) {
			return;
		}
		BlockState stone = switch (ruin.nextInt(5)) {
			case 0 -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			case 1 -> Blocks.BLACKSTONE.defaultBlockState();
			case 2 -> Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			case 3 -> Blocks.COBBLESTONE.defaultBlockState();
			default -> Blocks.GILDED_BLACKSTONE.defaultBlockState();
		};
		this.placeBlock(level, stone, x, y, z, chunkBB);
	}

	private void door(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int z, final DoorHingeSide hinge) {
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, hinge);
		this.placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), x, 1, z, chunkBB);
		this.placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), x, 2, z, chunkBB);
	}

	/** The spots of the castle's loot chests: the portal hall's first. */
	public static int[][] chestSpots() {
		return new int[][] {
			{CHEST_X, CHEST_Z}, {STORE_HALL_X + 1, HALL_Z + 5}, {1, 1}, {LENGTH - TOWER + 1, 1}
		};
	}

	/** A chest, stocked as a tower chest of a random floor would be, drawn from the castle's spot and its own. */
	private void chest(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int z, final Direction facing) {
		this.placeBlock(level, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), x, 1, z, chunkBB);
		BlockPos at = worldPos(x, 1, z);
		if (!chunkBB.isInside(at) || !(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
			return;
		}
		RandomSource random = RandomSource.create(this.ruinSeed ^ 0x4C4F4F54L ^ (x * 31L + z));
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
