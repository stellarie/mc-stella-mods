package com.stella.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The lost castle: a ring of ruins on the surface, a stair 2 wide down from the middle of the ring, and
 * {@value LostCastleGrid#MIN_FLOORS} to {@value LostCastleGrid#MAX_FLOORS} floors under the ground, each
 * {@value LostCastleGrid#FLOOR_HEIGHT} blocks from the one above. The lost room's portal stands on the
 * bottom floor. {@link LostCastleGrid} holds the shape, {@link LostCastlePlan} what each floor holds, and
 * {@link LostCastleBuilder} lays both.
 * <p>
 * The stone is the palette's ({@link LostCastlePalette}), which the biome at the ring picks.
 * <p>
 * Spots are in the piece's own frame: x along the front, z from the front inward, y up from the slab under
 * the bottom floor. States face as in a north-facing piece, where +z is north, and the piece turns them
 * with itself. What is ruined is drawn from the piece's own seed, so every chunk the castle crosses draws
 * the same ruin.
 */
public class LostRoomPiece extends StructurePiece {
	/** The deepest floor a chest may be stocked as. */
	public static final int LOOT_FLOORS = 100;
	/** A chest block is stocked with this many rolls of tower loot. */
	public static final int CHEST_ROLLS = 15;
	/** The hidden vault's chest is stocked in every slot, from the deepest floor. */
	public static final int VAULT_ROLLS = 27;
	private static final long SALT = 0x43415354L;

	private final long ruinSeed;
	private final LostCastlePalette palette;
	private final int floors;
	private final int drop;
	/** What the castle holds, drawn from the seed alone on first use. */
	private LostCastlePlan plan;

	/**
	 * A castle over ground whose lowest top block is at {@code lowestGround} and whose highest is at
	 * {@code highestGround}: the top floor's ceiling lies under the cover, and the ring's floor over the highest ground.
	 */
	public static LostRoomPiece over(final int west, final int north, final Direction facing, final LostCastlePalette palette, final int floors,
		final int lowestGround, final int highestGround) {
		return new LostRoomPiece(west, LostCastleGrid.bottomFor(floors, lowestGround), north, facing, palette, floors,
			LostCastleGrid.dropFor(lowestGround, highestGround));
	}

	public LostRoomPiece(final int west, final int bottom, final int north, final Direction facing, final LostCastlePalette palette, final int floors,
		final int drop) {
		super(LostRooms.PIECE, 0, makeBoundingBox(west, bottom, north, facing, LostCastleGrid.SIZE, LostCastleGrid.height(floors, drop), LostCastleGrid.SIZE));
		this.setOrientation(facing);
		this.ruinSeed = SALT ^ BlockPos.asLong(west, bottom, north);
		this.palette = palette;
		this.floors = floors;
		this.drop = drop;
	}

	public LostRoomPiece(final CompoundTag tag) {
		super(LostRooms.PIECE, tag);
		this.ruinSeed = SALT ^ BlockPos.asLong(this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ());
		this.palette = LostCastlePalette.values()[Math.floorMod(tag.getIntOr("Palette", 0), LostCastlePalette.values().length)];
		// A castle saved before the castles went down has no floors: it is left as it was.
		this.floors = tag.getIntOr("Floors", 0);
		this.drop = tag.getIntOr("Drop", 0);
	}

	@Override
	protected void addAdditionalSaveData(final StructurePieceSerializationContext context, final CompoundTag tag) {
		tag.putInt("Palette", this.palette.ordinal());
		tag.putInt("Floors", this.floors);
		tag.putInt("Drop", this.drop);
	}

	public LostCastlePalette palette() {
		return this.palette;
	}

	public int floors() {
		return this.floors;
	}

	public int drop() {
		return this.drop;
	}

	/** What the castle holds: the same for every chunk of one castle. */
	public LostCastlePlan plan() {
		if (this.plan == null) {
			this.plan = LostCastlePlan.of(this.ruinSeed, this.floors, this.drop);
		}
		return this.plan;
	}

	/** The height of the ring's floor, in the piece's own frame. */
	public int surfaceFeet() {
		return LostCastleGrid.surface(this.floors, this.drop);
	}

	/** Where the roaming mobs rise: x and z, low and high, inclusive, on the ring's floor. */
	public int[] yard() {
		return new int[] {LostCastleGrid.MID - 4, LostCastleGrid.MID - 4, LostCastleGrid.MID + 5, LostCastleGrid.MID + 5};
	}

	/** The spot where the entrance stair starts, at the middle of the ring: the feet of a body at the top step. */
	public BlockPos entranceTop() {
		return worldPos(LostCastleGrid.MID, surfaceFeet(), LostCastleGrid.ENTRY_Z);
	}

	/** The lower block of the castle's portal, on the bottom floor. */
	public BlockPos portalPos() {
		return worldPos(LostCastleBuilder.PORTAL_X, LostCastleGrid.feet(this.floors, this.floors - 1) + 1, LostCastleBuilder.PORTAL_Z);
	}

	/** The volume round the portal's frame, in the world. No mob spawns in it. */
	public BoundingBox portalFrame() {
		int feet = LostCastleGrid.feet(this.floors, this.floors - 1);
		BlockPos a = worldPos(LostCastleBuilder.PORTAL_X - 1, feet, LostCastleBuilder.PORTAL_Z - 1);
		BlockPos b = worldPos(LostCastleBuilder.PORTAL_X + 2, feet + 3, LostCastleBuilder.PORTAL_Z + 1);
		return BoundingBox.fromCorners(a, b);
	}

	public Direction facing() {
		return this.getOrientation();
	}

	@Override
	public void postProcess(
		final WorldGenLevel level, final StructureManager structureManager, final ChunkGenerator generator, final RandomSource random,
		final BoundingBox chunkBB, final ChunkPos chunkPos, final BlockPos referencePos
	) {
		if (this.floors == 0) {
			return;
		}
		build(level, chunkBB);
		if (chunkBB.isInside(portalPos())) {
			LostCastles.found(level.getLevel().dimension(), this);
		}
	}

	/** Lays the castle where its box stands: the seam a test places it by. */
	public void build(final WorldGenLevel level, final BoundingBox chunkBB) {
		if (this.floors == 0) {
			return;
		}
		LostCastleBuilder.build(this, level, chunkBB, this.ruinSeed, this.palette, plan());
	}

	// ---------------------------------------------------------------- blocks

	private BlockState turned(final BlockState state) {
		BlockState turned = state;
		if (getMirror() != Mirror.NONE) {
			turned = turned.mirror(getMirror());
		}
		if (getRotation() != Rotation.NONE) {
			turned = turned.rotate(getRotation());
		}
		return turned;
	}

	/**
	 * Sets a block at a spot of the piece's own frame, turned with the piece, when the spot lies in the chunk.
	 * No neighbour shape is updated, as worldgen does not, so a castle laid into a live level is the castle
	 * worldgen lays, and a portal is not broken by the stone laid after it.
	 */
	void set(final WorldGenLevel level, final BoundingBox chunkBB, final BlockState state, final int x, final int y, final int z) {
		BlockPos.MutableBlockPos at = this.getWorldPos(x, y, z);
		if (chunkBB.isInside(at)) {
			level.setBlock(at, turned(state), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
	}

	BlockState get(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z) {
		return this.getBlock(level, x, y, z, chunkBB);
	}

	/** Fills a box of the piece's own frame with one block. */
	void fill(final WorldGenLevel level, final BoundingBox chunkBB, final int x0, final int y0, final int z0, final int x1, final int y1, final int z1,
		final BlockState state) {
		BlockState turned = turned(state);
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					BlockPos.MutableBlockPos at = this.getWorldPos(x, y, z);
					if (chunkBB.isInside(at)) {
						level.setBlock(at, turned, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
			}
		}
	}

	/** Where a spot of the piece's own frame lies in the world. */
	public BlockPos worldPos(final int x, final int y, final int z) {
		return this.getWorldPos(x, y, z).immutable();
	}

	/** Stocks the chest block at a spot, when the spot lies in the chunk: {@code rolls} rolls of tower loot from floor {@code floor}. */
	private void stock(final WorldGenLevel level, final BoundingBox chunkBB, final BlockPos at, final int salt, final int rolls, final boolean deepest) {
		if (!chunkBB.isInside(at) || !(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
			return;
		}
		RandomSource random = RandomSource.create(this.ruinSeed ^ 0x4C4F4F54L ^ (salt * 0x9E3779B97F4A7C15L));
		int floor = deepest ? LOOT_FLOORS : 1 + random.nextInt(LOOT_FLOORS);
		for (int i = 0; i < rolls; i++) {
			ItemStack stack = TowerChests.roll(level.getLevel(), floor, random).stack();
			if (!stack.isEmpty()) {
				chest.setItem(i, stack);
			}
		}
	}

	/** A single chest, facing a direction of the piece's frame, stocked from the deepest floor: the vault's. */
	void vaultChest(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z, final Direction facing) {
		set(level, chunkBB, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), x, y, z);
		stock(level, chunkBB, worldPos(x, y, z), x * 31 + z + y * 961, VAULT_ROLLS, true);
	}

	/**
	 * A double chest over two columns. It is written from the world's own view of its two halves, so a turned
	 * or mirrored piece still joins them: the left half is the one whose partner lies clockwise of its facing.
	 * Each half is written only inside the chunk.
	 */
	void doubleChest(final WorldGenLevel level, final BoundingBox chunkBB, final int y, final int xa, final int za, final int xb, final int zb,
		final Direction label) {
		int fx = label.getStepX();
		int fz = -label.getStepZ();
		BlockPos a = worldPos(xa, y, za);
		BlockPos b = worldPos(xb, y, zb);
		BlockPos ahead = worldPos(xa + fx, y, za + fz);
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
			level.setBlock(a, aLeft ? left : right, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			stock(level, chunkBB, a, xa * 31 + za + y * 961, CHEST_ROLLS, false);
		}
		if (chunkBB.isInside(b)) {
			level.setBlock(b, aLeft ? right : left, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			stock(level, chunkBB, b, xb * 31 + zb + y * 961, CHEST_ROLLS, false);
		}
	}

	/** A vanilla spawner at a spot of the piece's own frame, set from a block entity tag when the spot is in the chunk. */
	void spawner(final WorldGenLevel level, final BoundingBox chunkBB, final int x, final int y, final int z, final CompoundTag config) {
		set(level, chunkBB, Blocks.SPAWNER.defaultBlockState(), x, y, z);
		BlockPos at = worldPos(x, y, z);
		if (!chunkBB.isInside(at) || !(level.getBlockEntity(at) instanceof SpawnerBlockEntity spawner)) {
			return;
		}
		spawner.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), config));
		spawner.setChanged();
	}
}
