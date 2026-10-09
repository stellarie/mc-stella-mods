package com.stella.tower;

import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The lost castle: an octagonal maze of three storeys in tower wall, on a platform above the ground, with a
 * breached south wall for its way in. Every way is six blocks wide between walls one block thick
 * ({@link TowerHex}). Each storey holds chests, candles, skulls and chains, its floor is broken with gaps
 * and cracked stone, and the top storey holds the blue portal, one wide and two high in a lining of tower
 * frame, with the workstations beside it: a crafting table with a candle, a chest of tower loot, an anvil
 * and a furnace.
 * <p>
 * The wall stone is the variant's own ({@link LostCastlePalette}), so a castle names the tower environment
 * its blocks come from. The portal, the workstations, the breach and the frame stay as they are.
 * <p>
 * Spots are in the piece's own frame: x along the front, z from the gate inward, y up from the floor. States
 * face as in a north-facing piece, where +z is north, and the piece turns them with itself. What is ruined
 * is drawn from the piece's own seed, so every chunk the castle crosses draws the same ruin.
 */
public class LostRoomPiece extends StructurePiece {
	/** The castle's frame: three storeys of the octagon fit inside it, the top one's ceiling included. */
	public static final int LENGTH = 31;
	public static final int WIDTH = 27;
	public static final int HEIGHT = 25;
	/** The deepest floor the chest may be stocked as. */
	public static final int LOOT_FLOORS = 100;
	private static final long SALT = 0x43415354L;

	private final long ruinSeed;
	private final LostCastleVariant variant;
	private final LostCastleVariant.Layout layout;
	/** The surface this castle is built from: its variant's tower environment. */
	private final LostCastlePalette palette;
	/** What the castle holds, drawn from the seed alone on first use. */
	private LostCastlePlan plan;

	public LostRoomPiece(final RandomSource random, final int west, final int floor, final int north) {
		this(west, floor, north, Direction.Plane.HORIZONTAL.getRandomDirection(random));
	}

	public LostRoomPiece(final int west, final int floor, final int north, final Direction facing) {
		this(west, floor, north, facing, LostCastleVariant.of(SALT ^ BlockPos.asLong(west, floor, north)));
	}

	/** A castle of a given variant: the seam a test places each variant by. */
	public LostRoomPiece(final int west, final int floor, final int north, final Direction facing, final LostCastleVariant variant) {
		super(LostRooms.PIECE, 0, makeBoundingBox(west, floor, north, facing, LENGTH, HEIGHT, WIDTH));
		this.setOrientation(facing);
		this.ruinSeed = SALT ^ BlockPos.asLong(west, floor, north);
		this.variant = variant;
		this.layout = variant.layout(this.ruinSeed);
		this.palette = variant.palette();
	}

	public LostRoomPiece(final CompoundTag tag) {
		super(LostRooms.PIECE, tag);
		this.ruinSeed = SALT ^ BlockPos.asLong(this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ());
		// Saved before the variants: the castle.
		this.variant = LostCastleVariant.values()[Math.floorMod(tag.getIntOr("Variant", 0), LostCastleVariant.values().length)];
		this.layout = this.variant.layout(this.ruinSeed);
		this.palette = this.variant.palette();
	}

	@Override
	protected void addAdditionalSaveData(final StructurePieceSerializationContext context, final CompoundTag tag) {
		tag.putInt("Variant", this.variant.ordinal());
	}

	public LostCastleVariant variant() {
		return this.variant;
	}

	/** What the castle holds: the same for every chunk of one castle. */
	public LostCastlePlan plan() {
		if (this.plan == null) {
			this.plan = LostCastlePlan.of(this.ruinSeed, this.variant);
		}
		return this.plan;
	}

	/** The surface this castle is built from. */
	public LostCastlePalette palette() {
		return this.palette;
	}

	/** The stone of the castle's own walls. */
	private BlockState wall() {
		return this.palette.wall().defaultBlockState();
	}

	/** Where the roaming mobs rise: x and z, low and high, inclusive, at floor level. */
	public int[] yard() {
		return this.layout.yard();
	}

	/** The lowest y, from the floor, that the castle builds at. */
	public int lowest() {
		return this.layout.lowest();
	}

	/** The spot just inside the gate, from which every part of the castle is reached: the middle of the breach. */
	public BlockPos gateInside() {
		return worldPos(LostCastleHex.breachMiddle(), 1, LostCastleHex.breachRow());
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
		int[] portal = this.layout.portal();
		return worldPos(portal[0], portal[1], portal[2]);
	}

	public Direction facing() {
		return this.getOrientation();
	}

	/** Lays the castle where its box stands: the seam a test places it by. */
	public void build(final WorldGenLevel level, final BoundingBox chunkBB) {
		BlockState wall = this.wall();
		for (int x = 0; x < LENGTH; x++) {
			for (int z = 0; z < WIDTH; z++) {
				this.fillColumnDown(level, wall, x, -1, z, chunkBB);
			}
		}
		LostCastleHex.build(this, level, chunkBB, this.ruinSeed, this.palette, plan());
	}

	void placeRubble(final WorldGenLevel level, final BoundingBox chunkBB, final RandomSource ruin, final int x, final int y, final int z) {
		// The fallen stone keeps to the breach's sides: its middle stays open and only its floor row takes stone.
		if (LostCastleHex.inBreach(x, y, z) && !LostCastleHex.inBreachSide(x, y, z)) {
			return;
		}
		if (!getBlock(level, x, y, z, chunkBB).isAir() || !getBlock(level, x, y - 1, z, chunkBB).isSolid()) {
			return;
		}
		// The castle's own rubble stone leads, so a pile names its environment, and the ruin keeps its mix.
		BlockState stone = switch (ruin.nextInt(5)) {
			case 0, 1 -> this.palette.rubble().defaultBlockState();
			case 2 -> Blocks.BLACKSTONE.defaultBlockState();
			case 3 -> Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			default -> Blocks.COBBLESTONE.defaultBlockState();
		};
		this.placeBlock(level, stone, x, y, z, chunkBB);
	}

	/** A chest, stocked as a tower chest of a random floor would be, drawn from the castle's spot and its own. */
	void chest(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int z, final Direction facing) {
		chest(level, chunkBB, x, 1, z, facing);
	}

	void chest(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z, final Direction facing) {
		this.placeBlock(level, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), x, y, z, chunkBB);
		stock(level, chunkBB, worldPos(x, y, z), x, y, z);
	}

	/** Stocks the chest block at a spot, when the spot lies in the chunk: {@link LostCastleHex#CHEST_ROLLS} rolls of tower loot. */
	private void stock(final WorldGenLevel level, final BoundingBox chunkBB, final BlockPos at, final int x, final int y, final int z) {
		if (!chunkBB.isInside(at) || !(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
			return;
		}
		RandomSource random = RandomSource.create(this.ruinSeed ^ 0x4C4F4F54L ^ (x * 31L + z) ^ (y == 1 ? 0L : y * 961L));
		int floor = 1 + random.nextInt(LOOT_FLOORS);
		for (int i = 0; i < LostCastleHex.CHEST_ROLLS; i++) {
			ItemStack stack = TowerChests.roll(level.getLevel(), floor, random).stack();
			if (!stack.isEmpty()) {
				chest.setItem(i, stack);
			}
		}
	}

	/**
	 * A chest site of the plan. A double is written from the world's own view of its two halves, so a turned
	 * or mirrored piece still joins them: the left half is the one whose partner lies clockwise of its facing.
	 * Each half is written only inside the chunk, and both stay inside the piece.
	 */
	void chestSite(final WorldGenLevel level, final BoundingBox chunkBB, final int y, final LostCastlePlan.Chest site) {
		if (!site.isDouble()) {
			chest(level, chunkBB, site.x(), y, site.z(), site.facing());
			return;
		}
		int fx = site.facing().getStepX();
		int fz = -site.facing().getStepZ();
		BlockPos a = worldPos(site.x(), y, site.z());
		BlockPos b = worldPos(site.x2(), y, site.z2());
		BlockPos ahead = worldPos(site.x() + fx, y, site.z() + fz);
		Direction facing = Direction.NORTH;
		for (Direction candidate : Direction.Plane.HORIZONTAL) {
			if (a.relative(candidate).equals(ahead)) {
				facing = candidate;
			}
		}
		boolean aLeft = a.relative(facing.getClockWise()).equals(b);
		BlockState base = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
		BlockState left = base.setValue(ChestBlock.TYPE, ChestType.LEFT);
		BlockState right = base.setValue(ChestBlock.TYPE, ChestType.RIGHT);
		if (chunkBB.isInside(a)) {
			level.setBlock(a, aLeft ? left : right, Block.UPDATE_CLIENTS);
			stock(level, chunkBB, a, site.x(), y, site.z());
		}
		if (chunkBB.isInside(b)) {
			level.setBlock(b, aLeft ? right : left, Block.UPDATE_CLIENTS);
			stock(level, chunkBB, b, site.x2(), y, site.z2());
		}
	}

	/**
	 * A bed of the plan, written from the world's own view of its two halves, so a turned or mirrored piece
	 * still joins them. Each half is written only inside the chunk.
	 */
	void bed(final WorldGenLevel level, final BoundingBox chunkBB, final int y, final LostCastlePlan.Furnishing bed) {
		BlockPos foot = worldPos(bed.x(), y, bed.z());
		BlockPos head = worldPos(bed.x2(), y, bed.z2());
		Direction facing = Direction.NORTH;
		for (Direction candidate : Direction.Plane.HORIZONTAL) {
			if (foot.relative(candidate).equals(head)) {
				facing = candidate;
			}
		}
		BlockState base = Blocks.STRAW_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
		if (chunkBB.isInside(foot)) {
			level.setBlock(foot, base.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
		}
		if (chunkBB.isInside(head)) {
			level.setBlock(head, base.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
		}
	}

	/** A vanilla spawner at a spot of the piece's own frame, set from a block entity tag when the spot is in the chunk. */
	void spawner(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z, final CompoundTag config) {
		this.placeBlock(level, Blocks.SPAWNER.defaultBlockState(), x, y, z, chunkBB);
		BlockPos at = worldPos(x, y, z);
		if (!chunkBB.isInside(at) || !(level.getBlockEntity(at) instanceof SpawnerBlockEntity spawner)) {
			return;
		}
		spawner.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), config));
		spawner.setChanged();
	}

	/**
	 * Iron bars at a spot of the piece's own frame, joined as they would end up in the finished castle. The
	 * spawner stands one step away in {@code (spawnerDx, spawnerDz)}, wall in each of {@code walls}, and air
	 * in the rest. Bars set bare change when a neighbour is set after them, which would make a castle built
	 * in chunks differ from one built whole by the order of the chunks.
	 */
	void bars(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z, final int spawnerDx, final int spawnerDz, final int[][] walls) {
		BlockPos at = worldPos(x, y, z);
		if (!chunkBB.isInside(at)) {
			return;
		}
		IronBarsBlock block = (IronBarsBlock) Blocks.IRON_BARS;
		BlockState state = block.defaultBlockState();
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] step : steps) {
			Direction side = null;
			BlockPos next = worldPos(x + step[0], y, z + step[1]);
			for (Direction candidate : Direction.Plane.HORIZONTAL) {
				if (at.relative(candidate).equals(next)) {
					side = candidate;
				}
			}
			BlockState neighbour = Blocks.AIR.defaultBlockState();
			if (step[0] == spawnerDx && step[1] == spawnerDz) {
				neighbour = Blocks.SPAWNER.defaultBlockState();
			}
			for (int[] wall : walls) {
				if (step[0] == wall[0] && step[1] == wall[1]) {
					neighbour = this.wall();
				}
			}
			boolean joined = block.attachsTo(neighbour, neighbour.isFaceSturdy(level, next, side.getOpposite()));
			state = state.setValue(switch (side) {
				case NORTH -> IronBarsBlock.NORTH;
				case EAST -> IronBarsBlock.EAST;
				case SOUTH -> IronBarsBlock.SOUTH;
				default -> IronBarsBlock.WEST;
			}, joined);
		}
		level.setBlock(at, state, Block.UPDATE_CLIENTS);
	}

	/** Sets a block at a spot of the piece's own frame, turned with the piece. */
	void set(final WorldGenLevel level, final BoundingBox chunkBB, final BlockState state, final int x, final int y, final int z) {
		this.placeBlock(level, state, x, y, z, chunkBB);
	}

	BlockState get(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z) {
		return this.getBlock(level, x, y, z, chunkBB);
	}

	/** Fills a box of the piece's own frame with one block. */
	void fill(final WorldGenLevel level, final BoundingBox chunkBB, final int x0, final int y0, final int z0, final int x1, final int y1, final int z1, final BlockState state) {
		this.generateBox(level, chunkBB, x0, y0, z0, x1, y1, z1, state, state, false);
	}

	/** Lines the blue portal, one wide and two high, axis x, entered from lower z; {@code y} is its lower block. */
	void portal(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z) {
		BlockState frame = TowerBlocks.TOWER_FRAME.defaultBlockState();
		for (int dy = 0; dy <= 2; dy++) {
			this.placeBlock(level, frame, x - 1, y + dy, z, chunkBB);
			this.placeBlock(level, frame, x + 1, y + dy, z, chunkBB);
		}
		this.placeBlock(level, frame, x, y + 2, z, chunkBB);
		this.placeBlock(level, frame, x, y - 1, z, chunkBB);
		BlockState portal = TowerBlocks.TOWER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
		this.placeBlock(level, portal, x, y, z, chunkBB);
		this.placeBlock(level, portal, x, y + 1, z, chunkBB);
	}

	/**
	 * The workstations in a row from a spot, a step of (dx, dz) apart: a crafting table with a lit candle,
	 * a furnace, an anvil, and a chest, each facing {@code facing}.
	 */
	void workstations(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z, final int dx, final int dz, final Direction facing) {
		this.placeBlock(level, Blocks.CRAFTING_TABLE.defaultBlockState(), x, y, z, chunkBB);
		this.placeBlock(level, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), x, y + 1, z, chunkBB);
		this.placeBlock(level, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, facing), x + dx, y, z + dz, chunkBB);
		Direction along = facing.getClockWise();
		this.placeBlock(level, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, along), x + 2 * dx, y, z + 2 * dz, chunkBB);
		chest(level, chunkBB, x + 3 * dx, y, z + 3 * dz, facing);
	}

	/** Where a spot of the piece's own frame lies in the world. */
	public BlockPos worldPos(final int x, final int y, final int z) {
		return this.getWorldPos(x, y, z).immutable();
	}
}
