package com.stella.tower.colosseum;

import com.stella.tower.TowerPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Draws the colosseum in code, column by column, from the centre out. The ground is levelled first: everything over the
 * floor layer is cleared and every hollow under it filled, so any site is a fit.
 * <p>
 * From the centre: a platform four blocks high with a stair on each side and the portal on top; a sand arena with a ring
 * of broken columns; five tiers of seats; the ring wall, cracked and mossy, worn down in sectors, with four gates. All
 * wear is drawn from the world seed, so a site always looks the same.
 */
final class ColosseumBuilder {
	/** The levelled ring past the wall. */
	private static final int APRON = 4;
	private static final int CLEAR_HEIGHT = 26;
	private static final int MAX_FOUNDATION = 48;
	private static final double SEAT_FROM = 20.0;
	private static final double SEAT_STEP = 1.65;
	private static final int TIERS = 6;
	private static final double WALL_FROM = 29.0;
	private static final double WALL_TO = Colosseum.RADIUS + 0.6;
	private static final int WALL_HEIGHT = 11;
	private static final int SECTORS = 24;
	private static final int RAGGED_STEPS = 72;
	private static final int COLUMNS = 14;
	private static final double COLUMN_RING = 18.6;
	private static final int GATE_HALF_WIDTH = 2;
	private static final int GATE_HEIGHT = 6;

	private static final BlockState BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
	private static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
	private static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final ServerLevel level;
	private final int cx;
	private final int cz;
	private final int y0;
	private final long seed;
	private final Colosseum.Layout layout;

	private ColosseumBuilder(final ServerLevel level, final Colosseum.Layout layout) {
		this.level = level;
		this.layout = layout;
		this.cx = layout.floor().getX();
		this.cz = layout.floor().getZ();
		this.y0 = layout.floor().getY();
		this.seed = level.getSeed() ^ (((long) this.cx << 32) ^ (this.cz & 0xFFFFFFFFL));
	}

	/** Builds the colosseum; false, with nothing touched, if someone has built on the site. */
	static boolean build(final ServerLevel level, final Colosseum.Layout layout) {
		ColosseumBuilder builder = new ColosseumBuilder(level, layout);
		int reach = Colosseum.RADIUS + APRON;
		builder.loadChunks(reach);
		if (builder.occupied(reach)) {
			return false;
		}
		builder.run();
		return true;
	}

	/**
	 * Whether the ground the colosseum would cover holds anything a person or a village made: planks, beds, wool, chests,
	 * torches, cut stone and the like. Natural stone, dirt, sand and trees do not count.
	 */
	boolean occupied(final int reach) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx * dx + dz * dz > reach * reach) {
					continue;
				}
				for (int y = this.y0 - 1; y <= this.y0 + 18; y++) {
					if (madeByHand(this.level.getBlockState(at.set(this.cx + dx, y, this.cz + dz)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Whether a block is one that only a hand puts in the world. */
	static boolean madeByHand(final BlockState state) {
		return state.is(BlockTags.PLANKS) || state.is(BlockTags.BEDS) || state.is(BlockTags.WOOL) || state.is(BlockTags.DOORS)
			|| state.is(BlockTags.STAIRS) || state.is(BlockTags.FENCES) || state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)
			|| state.is(Blocks.BARREL) || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE) || state.is(Blocks.TORCH)
			|| state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN) || state.is(Blocks.BOOKSHELF) || state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.STONE_BRICKS) || state.is(Blocks.CUT_SANDSTONE) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
			|| state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.BRICKS) || state.is(Blocks.NETHER_PORTAL)
			|| state.is(Blocks.SPAWNER) || state.is(Blocks.RAIL);
	}

	private void run() {
		int reach = Colosseum.RADIUS + APRON;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r <= reach) {
					prepare(dx, dz);
				}
			}
		}
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r <= reach) {
					shape(dx, dz, r);
				}
			}
		}
		stairs();
		columns();
		rubble();
		portal();
		chest();
		lanterns();
	}

	private void loadChunks(final int reach) {
		for (int x = (this.cx - reach) >> 4; x <= (this.cx + reach) >> 4; x++) {
			for (int z = (this.cz - reach) >> 4; z <= (this.cz + reach) >> 4; z++) {
				this.level.getChunk(x, z);
			}
		}
	}

	// ---------------------------------------------------------------- ground

	/** Clears everything over the floor layer and fills every hollow under it. */
	private void prepare(final int dx, final int dz) {
		int x = this.cx + dx;
		int z = this.cz + dz;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int y = this.y0 + 1; y <= this.y0 + CLEAR_HEIGHT; y++) {
			at.set(x, y, z);
			if (!this.level.getBlockState(at).isAir()) {
				this.level.setBlock(at, AIR, Block.UPDATE_CLIENTS);
			}
		}
		for (int y = this.y0 - 1; y >= this.y0 - MAX_FOUNDATION && y > this.level.getMinY(); y--) {
			at.set(x, y, z);
			if (solid(this.level.getBlockState(at))) {
				break;
			}
			this.level.setBlock(at, noise(x, y, z, 1) < 0.5 ? BRICKS : Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private static boolean solid(final BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()
			&& !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
	}

	// ---------------------------------------------------------------- the ring

	private void shape(final int dx, final int dz, final double r) {
		int x = this.cx + dx;
		int z = this.cz + dz;
		if (r > WALL_TO - ragged(Math.atan2(dz, dx))) {
			double n = noise(x, 0, z, 2);
			set(x, this.y0, z, n < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.8 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
			// Blocks fallen from the wall lie on the ground outside it.
			if (r < WALL_TO + 3.0 && noise(x, 1, z, 29) > 0.9) {
				set(x, this.y0 + 1, z, noise(x, 2, z, 30) < 0.5 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : CRACKED);
			}
			return;
		}
		if (r <= Colosseum.PLATFORM_RADIUS + 0.2) {
			platformColumn(x, z, r);
			return;
		}
		if (r < SEAT_FROM) {
			arenaFloor(x, z, r);
			return;
		}
		double theta = Math.atan2(dz, dx);
		if (inGate(dx, dz)) {
			gateColumn(x, z, r);
			return;
		}
		if (r < WALL_FROM) {
			seatColumn(x, z, dx, dz, r, theta);
		} else {
			wallColumn(x, z, r, theta);
		}
	}

	private void platformColumn(final int x, final int z, final double r) {
		for (int k = 0; k < Colosseum.PLATFORM_HEIGHT; k++) {
			set(x, this.y0 + k, z, brick(x, this.y0 + k, z));
		}
		int top = this.y0 + Colosseum.PLATFORM_HEIGHT;
		set(x, top, z, r > Colosseum.PLATFORM_RADIUS - 1.0 ? CHISELED : noise(x, top, z, 3) < 0.25 ? CRACKED : BRICKS);
	}

	private void arenaFloor(final int x, final int z, final double r) {
		double n = noise(x, 0, z, 4);
		BlockState floor;
		if (r >= 9.0 && r <= 13.0) {
			floor = brick(x, this.y0, z);
		} else if (n < 0.64) {
			floor = Blocks.SAND.defaultBlockState();
		} else if (n < 0.78) {
			floor = Blocks.COARSE_DIRT.defaultBlockState();
		} else if (n < 0.9) {
			floor = Blocks.GRAVEL.defaultBlockState();
		} else {
			floor = n < 0.95 ? CRACKED : MOSSY;
		}
		set(x, this.y0, z, floor);
	}

	/** The seats: each tier a block higher than the one inside it, topped by a stair with its back to the wall. */
	private void seatColumn(final int x, final int z, final int dx, final int dz, final double r, final double theta) {
		int tier = Math.min(TIERS - 1, (int) Math.floor((r - SEAT_FROM) / SEAT_STEP));
		int height = tier + 1;
		double wear = wear(theta);
		int top = height;
		if (wear > 0.35 && noise(x, 0, z, 5) < (wear - 0.35) * 1.1) {
			top = Math.max(0, height - 1 - (int) (noise(x, 1, z, 6) * height));
		}
		set(x, this.y0, z, brick(x, this.y0, z));
		for (int k = 1; k <= top; k++) {
			if (k == height) {
				Direction outward = Direction.getApproximateNearest(dx, 0.0, dz);
				BlockState stair = (noise(x, k, z, 7) < 0.28 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS)
					.defaultBlockState().setValue(StairBlock.FACING, outward);
				set(x, this.y0 + k, z, stair);
			} else {
				set(x, this.y0 + k, z, brick(x, this.y0 + k, z));
			}
		}
	}

	private void wallColumn(final int x, final int z, final double r, final double theta) {
		double wear = wear(theta);
		int loss = (int) Math.floor(wear * 9.0 + noise(x, 0, z, 8) * 2.0);
		int top = wear > 0.9 ? 2 + (int) (noise(x, 1, z, 9) * 2.0) : WALL_HEIGHT - loss;
		top = Math.max(top, 2);
		if (loss == 0 && ((x + z) & 1) == 0) {
			top++;
		}
		int sector = sectorOf(theta);
		double across = r * Math.sin(theta - sectorCentre(sector));
		boolean window = sector % 2 == 0 && Math.abs(across) <= 0.9 && top >= 10;
		set(x, this.y0, z, brick(x, this.y0, z));
		for (int k = 1; k <= top; k++) {
			if (window && k >= 7 && k <= 9) {
				continue;
			}
			BlockState state = brick(x, this.y0 + k, z);
			if (k <= 2 && noise(x, k, z, 10) < 0.35) {
				state = MOSSY;
			}
			set(x, this.y0 + k, z, state);
		}
	}

	/** A gate through seats and wall: a flat way at the floor layer with a lintel over the wall. */
	private void gateColumn(final int x, final int z, final double r) {
		set(x, this.y0, z, brick(x, this.y0, z));
		if (r >= WALL_FROM - 0.5) {
			for (int k = GATE_HEIGHT + 1; k <= WALL_HEIGHT - 2; k++) {
				set(x, this.y0 + k, z, brick(x, this.y0 + k, z));
			}
		}
	}

	private static boolean inGate(final int dx, final int dz) {
		return (Math.abs(dz) <= GATE_HALF_WIDTH && Math.abs(dx) > Math.abs(dz))
			|| (Math.abs(dx) <= GATE_HALF_WIDTH && Math.abs(dz) > Math.abs(dx));
	}

	// ---------------------------------------------------------------- the middle

	/** A flight of four stairs up each side of the platform. */
	private void stairs() {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			Direction across = side.getClockWise();
			for (int k = 1; k <= Colosseum.PLATFORM_HEIGHT; k++) {
				for (int lateral = -1; lateral <= 1; lateral++) {
					BlockPos at = new BlockPos(this.cx, this.y0, this.cz)
						.relative(side, Colosseum.PLATFORM_RADIUS + k).relative(across, lateral);
					int layer = this.y0 + Colosseum.PLATFORM_HEIGHT + 1 - k;
					for (int y = this.y0; y < layer; y++) {
						set(at.getX(), y, at.getZ(), brick(at.getX(), y, at.getZ()));
					}
					BlockState stair = (noise(at.getX(), layer, at.getZ(), 11) < 0.2 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS)
						.defaultBlockState().setValue(StairBlock.FACING, side.getOpposite());
					set(at.getX(), layer, at.getZ(), stair);
				}
			}
		}
	}

	private void portal() {
		int top = this.y0 + Colosseum.PLATFORM_HEIGHT;
		BlockState frame = Blocks.CRYING_OBSIDIAN.defaultBlockState();
		for (int dx = -1; dx <= 2; dx++) {
			set(this.cx + dx, top, this.cz, frame);
			set(this.cx + dx, top + 4, this.cz, frame);
		}
		for (int dy = 1; dy <= 3; dy++) {
			set(this.cx - 1, top + dy, this.cz, frame);
			set(this.cx + 2, top + dy, this.cz, frame);
			set(this.cx, top + dy, this.cz, AIR);
			set(this.cx + 1, top + dy, this.cz, AIR);
		}
		TowerPortalShape.find(this.level, this.layout.portalInside()).ifPresent(shape -> shape.createPortal(this.level));
	}

	private void chest() {
		BlockPos at = this.layout.chest();
		this.level.setBlock(at, com.stella.tower.TowerBlocks.TOWER_CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
		BlockEntity entity = this.level.getBlockEntity(at);
		if (entity instanceof ChestBlockEntity chest) {
			chest.setItem(0, OfferingChest.book());
			chest.setChanged();
		}
	}

	private void lanterns() {
		int top = this.y0 + Colosseum.PLATFORM_HEIGHT + 1;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				set(this.cx + 4 * sx, top, this.cz + 4 * sz, Blocks.LANTERN.defaultBlockState());
			}
		}
	}

	// ---------------------------------------------------------------- the ruin

	/** Broken columns in a ring inside the seats, two blocks square, each a different height. */
	private void columns() {
		for (int i = 0; i < COLUMNS; i++) {
			double angle = Math.PI * 2.0 * i / COLUMNS + (noise(i, 0, 0, 12) - 0.5) * 0.12;
			int x = this.cx + (int) Math.round(Math.cos(angle) * COLUMN_RING);
			int z = this.cz + (int) Math.round(Math.sin(angle) * COLUMN_RING);
			if (inGate(x - this.cx, z - this.cz)) {
				continue;
			}
			double n = noise(i, 1, 0, 13);
			int height = n < 0.25 ? 1 + (int) (noise(i, 2, 0, 14) * 2.0) : 3 + (int) (noise(i, 3, 0, 15) * 6.0);
			for (int ox = 0; ox <= 1; ox++) {
				for (int oz = 0; oz <= 1; oz++) {
					for (int k = 1; k <= height; k++) {
						BlockState state = k == 1 ? CHISELED : brick(x + ox, this.y0 + k, z + oz);
						set(x + ox, this.y0 + k, z + oz, state);
					}
					if (height >= 4 && noise(x + ox, 4, z + oz, 16) < 0.5) {
						set(x + ox, this.y0 + height + 1, z + oz, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
					}
				}
			}
			if (height >= 5 && n > 0.6) {
				set(x, this.y0 + height + 1, z, Blocks.LANTERN.defaultBlockState());
			}
		}
	}

	/** Fallen shafts and heaps of rubble on the sand. */
	private void rubble() {
		for (int i = 0; i < 7; i++) {
			double angle = noise(i, 0, 1, 17) * Math.PI * 2.0;
			double radius = 13.5 + noise(i, 1, 1, 18) * 3.5;
			int x = this.cx + (int) Math.round(Math.cos(angle) * radius);
			int z = this.cz + (int) Math.round(Math.sin(angle) * radius);
			Direction along = noise(i, 2, 1, 19) < 0.5 ? Direction.EAST : Direction.SOUTH;
			int length = 3 + (int) (noise(i, 3, 1, 20) * 3.0);
			for (int k = 0; k < length; k++) {
				BlockPos at = new BlockPos(x, this.y0 + 1, z).relative(along, k);
				BlockState slab = (noise(at.getX(), 0, at.getZ(), 21) < 0.4 ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB)
					.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
				set(at.getX(), at.getY(), at.getZ(), k == 0 ? CHISELED : slab);
			}
		}
		for (int i = 0; i < 12; i++) {
			double angle = noise(i, 0, 2, 22) * Math.PI * 2.0;
			double radius = 10.0 + noise(i, 1, 2, 23) * 9.5;
			int x = this.cx + (int) Math.round(Math.cos(angle) * radius);
			int z = this.cz + (int) Math.round(Math.sin(angle) * radius);
			int heap = 1 + (int) (noise(i, 2, 2, 24) * 2.0);
			for (int k = 1; k <= heap; k++) {
				double n = noise(x, k, z, 25);
				set(x, this.y0 + k, z, n < 0.4 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.7 ? Blocks.COBBLESTONE.defaultBlockState() : CRACKED);
			}
		}
	}

	// ---------------------------------------------------------------- wear and noise

	private BlockState brick(final int x, final int y, final int z) {
		double n = noise(x, y, z, 26);
		return n < 0.18 ? CRACKED : n < 0.36 ? MOSSY : BRICKS;
	}

	/** How far the wall's outer face is eaten back at an angle, 0 to 1.3 blocks, smooth from one angle to the next. */
	private double ragged(final double theta) {
		double scaled = (theta + Math.PI) / (Math.PI * 2.0) * RAGGED_STEPS;
		int low = (int) Math.floor(scaled);
		double blend = scaled - low;
		double a = noise(Math.floorMod(low, RAGGED_STEPS), 0, 0, 31);
		double b = noise(Math.floorMod(low + 1, RAGGED_STEPS), 0, 0, 31);
		return 1.3 * (a + (b - a) * blend);
	}

	private static int sectorOf(final double theta) {
		return Math.floorMod((int) Math.floor((theta + Math.PI) / (Math.PI * 2.0) * SECTORS), SECTORS);
	}

	private static double sectorCentre(final int sector) {
		return -Math.PI + (sector + 0.5) * (Math.PI * 2.0 / SECTORS);
	}

	/** How worn a sector of the ring is, 0 to 1; most are whole, a few are nearly gone. */
	private double wear(final double theta) {
		int sector = sectorOf(theta);
		// Always two worn-through stretches side by side, and one whole stretch across the ring from them.
		int gone = (int) (noise(0, 0, 0, 28) * SECTORS);
		if (sector == gone || sector == (gone + 1) % SECTORS) {
			return 0.95;
		}
		if (sector == (gone + SECTORS / 2) % SECTORS) {
			return 0.0;
		}
		double n = noise(sector, 0, 0, 27);
		return n * n;
	}

	private void set(final int x, final int y, final int z, final BlockState state) {
		this.level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
	}

	/** A repeatable value from 0 to 1 for a spot: the same site always draws the same ruin. */
	private double noise(final int x, final int y, final int z, final int salt) {
		long h = this.seed + salt * 0x9E3779B97F4A7C15L;
		h ^= x * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 30)) * 0x94D049BB133111EBL;
		h ^= y * 0xD6E8FEB86659FD93L;
		h = (h ^ (h >>> 27)) * 0xBF58476D1CE4E5B9L;
		h ^= z * 0x9E3779B97F4A7C15L;
		h = (h ^ (h >>> 31)) * 0x94D049BB133111EBL;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}
}
