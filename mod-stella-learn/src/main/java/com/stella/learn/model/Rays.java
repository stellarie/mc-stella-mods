package com.stella.learn.model;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The 32 distance rays of the observation and the sight line to the target. Each walks the voxel grid to the first block
 * with a collision shape. A chunk that is not loaded counts as open air, so a ray never loads one.
 *
 * Most of a ray runs through air, so the walk avoids block lookups. A ray that climbs or stays level jumps a whole chunk
 * when it flies above the chunk's highest block, and inside a chunk a column's own free height rules out the blocks above
 * it. Those heights are read once a tick and shared by every archer, so a block placed this tick shows next tick. The
 * class keeps scratch state and belongs to the server thread.
 */
final class Rays {
	static final int HORIZONTAL = 16;
	static final int DOWN = 8;
	static final int UP = 4;
	static final int DIAGONAL = 4;
	static final int COUNT = HORIZONTAL + DOWN + UP + DIAGONAL;
	static final double MAX = 16.0;
	/** A cached set of rays is reused while the mob stays within this many blocks, turns less than this, and is this fresh. */
	static final double REUSE_BLOCKS_SQR = 1.0;
	static final float REUSE_DEGREES = 10.0F;
	static final int REUSE_TICKS = 10;
	/** Vanilla's own limit for a mob's line of sight. */
	private static final double SIGHT_LIMIT = 128.0;
	private static final double DOWN_RING = 1.0;
	private static final double UP_RING = 0.4;
	private static final double DIAGONAL_HEIGHT = Math.sqrt(0.5);
	/** Moves a restarted walk clear of the chunk edge it stopped on, so its first block is in the next chunk. */
	private static final double EDGE_NUDGE = 1.0E-7;
	/** A walk of 16 blocks takes under 100 steps; a walk that takes more has lost its way, and counts as open. */
	private static final int MAX_STEPS = 1024;
	private static final java.util.concurrent.atomic.AtomicLong OVERRUNS = new java.util.concurrent.atomic.AtomicLong();

	/** How many walks were cut short by the step bound since the server started. */
	public static long overruns() {
		return OVERRUNS.get();
	}

	private static double overrun(final double limit, final String why) {
		long count = OVERRUNS.incrementAndGet();
		if (count == 1 || count % 10_000 == 0) {
			com.stella.learn.LearnLog.info("learnModel", "RAY_OVERRUN", "count=" + count + " reason=" + why);
		}

		return limit;
	}
	/** An 8 by 8 window of chunks, each with its own slot, so the nine around an archer never share one. */
	private static final int SLOTS = 64;
	private static final int COLUMNS = 256;
	private static final double[] HORIZONTAL_COS = new double[HORIZONTAL];
	private static final double[] HORIZONTAL_SIN = new double[HORIZONTAL];
	private static final Column[] COLUMN_CACHE = new Column[SLOTS];
	private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
	private static Level level;
	private static long now;
	private static int minY;
	private static int maxY;

	/** What one chunk tells a ray this tick: the chunk (null when unloaded), each column's first free height, and the highest. */
	private static final class Column {
		Level level;
		int chunkX;
		int chunkZ;
		long stamp = Long.MIN_VALUE;
		LevelChunk chunk;
		final int[] free = new int[COLUMNS];
		int highest;
	}

	static {
		for (int i = 0; i < HORIZONTAL; i++) {
			double angle = Math.TAU * i / HORIZONTAL;
			HORIZONTAL_COS[i] = Math.cos(angle);
			HORIZONTAL_SIN[i] = Math.sin(angle);
		}

		for (int i = 0; i < SLOTS; i++) {
			COLUMN_CACHE[i] = new Column();
		}
	}

	private Rays() {
	}

	/** Binds the level once per call, since its height limits and clock are several method calls deep. */
	private static void begin(final Level world) {
		level = world;
		minY = world.getMinY();
		maxY = world.getMaxY();
		now = world.getGameTime();
	}

	private static void end() {
		level = null;
	}

	/** Lets go of the chunks the column cache holds, so an unloaded chunk is not kept alive by it. Called every server tick. */
	static void clear() {
		for (Column column : COLUMN_CACHE) {
			column.chunk = null;
			column.level = null;
			column.stamp = Long.MIN_VALUE;
		}
	}

	/** True when no block with a collision shape lies between the eyes of `mob` and `target`. */
	static boolean sees(final Mob mob, final Entity target) {
		double dx = target.getX() - mob.getX();
		double dy = target.getEyeY() - mob.getEyeY();
		double dz = target.getZ() - mob.getZ();
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (distance > SIGHT_LIMIT) {
			return false;
		}

		if (distance < 1.0E-6) {
			return true;
		}

		begin(mob.level());
		double clear = trace(mob.getX(), mob.getEyeY(), mob.getZ(), dx / distance, dy / distance, dz / distance, distance);
		end();
		return clear >= distance;
	}

	/** Fills `out[0..COUNT)` in the contract order: horizontal, down, up, diagonal. */
	static void fill(final Mob mob, final float[] out) {
		begin(mob.level());
		double yaw = Math.toRadians(mob.getYRot());
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double rightX = -Math.cos(yaw);
		double rightZ = -Math.sin(yaw);
		Vec3 eye = mob.getEyePosition();
		double feetX = mob.getX();
		double feetY = mob.getY() + 0.01;
		double feetZ = mob.getZ();
		int at = 0;
		boolean open = openAround(eye.x, eye.y, eye.z);

		for (int i = 0; i < HORIZONTAL; i++) {
			double dx = forwardX * HORIZONTAL_COS[i] + rightX * HORIZONTAL_SIN[i];
			double dz = forwardZ * HORIZONTAL_COS[i] + rightZ * HORIZONTAL_SIN[i];
			out[at++] = open ? (float) MAX : (float) trace(eye.x, eye.y, eye.z, dx, 0.0, dz, MAX);
		}

		for (int i = 0; i < DOWN; i++) {
			double cos = HORIZONTAL_COS[i * 2];
			double sin = HORIZONTAL_SIN[i * 2];
			double ox = feetX + (forwardX * cos + rightX * sin) * DOWN_RING;
			double oz = feetZ + (forwardZ * cos + rightZ * sin) * DOWN_RING;
			out[at++] = (float) trace(ox, feetY, oz, 0.0, -1.0, 0.0, MAX);
		}

		for (int i = 0; i < UP; i++) {
			double cos = HORIZONTAL_COS[i * 4];
			double sin = HORIZONTAL_SIN[i * 4];
			double ox = eye.x + (forwardX * cos + rightX * sin) * UP_RING;
			double oz = eye.z + (forwardZ * cos + rightZ * sin) * UP_RING;
			out[at++] = open ? (float) MAX : (float) trace(ox, eye.y, oz, 0.0, 1.0, 0.0, MAX);
		}

		for (int i = 0; i < DIAGONAL; i++) {
			double cos = HORIZONTAL_COS[i * 4];
			double sin = HORIZONTAL_SIN[i * 4];
			double dx = (forwardX * cos + rightX * sin) * DIAGONAL_HEIGHT;
			double dz = (forwardZ * cos + rightZ * sin) * DIAGONAL_HEIGHT;
			out[at++] = open ? (float) MAX : (float) trace(eye.x, eye.y, eye.z, dx, DIAGONAL_HEIGHT, dz, MAX);
		}

		end();
	}

	/**
	 * True when nothing in this chunk or the eight around it reaches the eye's height. A ray that stays level or climbs
	 * runs at most 16 blocks, which is one chunk, so from here every such ray is clear to its full length.
	 */
	private static boolean openAround(final double x, final double y, final double z) {
		int chunkX = Mth.floor(x) >> 4;
		int chunkZ = Mth.floor(z) >> 4;
		int eyeY = Mth.floor(y);
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				Column column = columnAt(chunkX + dx, chunkZ + dz);
				if (column.chunk != null && eyeY < column.highest) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Distance along a unit direction to the first solid voxel, up to `limit`; 0 when the start is inside one. The walk
	 * restarts whenever it enters a new chunk, and there it may jump the whole chunk.
	 */
	private static double trace(
		final double startX, final double startY, final double startZ, final double dx, final double dy, final double dz, final double limit
	) {
		int stepX = dx > 0.0 ? 1 : -1;
		int stepY = dy > 0.0 ? 1 : -1;
		int stepZ = dz > 0.0 ? 1 : -1;
		double deltaX = dx == 0.0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
		double deltaY = dy == 0.0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
		double deltaZ = dz == 0.0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
		boolean rising = dy >= 0.0;
		double travelled = 0.0;
		if (!Double.isFinite(startX + startY + startZ + dx + dy + dz)) {
			return overrun(limit, "notFinite");
		}

		int steps = 0;
		while (true) {
			if (++steps > MAX_STEPS) {
				return overrun(limit, "steps");
			}

			double px = startX + dx * travelled;
			double py = startY + dy * travelled;
			double pz = startZ + dz * travelled;
			int x = Mth.floor(px);
			int y = Mth.floor(py);
			int z = Mth.floor(pz);
			int chunkX = x >> 4;
			int chunkZ = z >> 4;
			Column column = columnAt(chunkX, chunkZ);

			if (rising && (column.chunk == null || y >= column.highest)) {
				double edgeX = dx > 0.0 ? (((chunkX + 1) << 4) - startX) / dx : dx < 0.0 ? ((chunkX << 4) - startX) / dx : Double.POSITIVE_INFINITY;
				double edgeZ = dz > 0.0 ? (((chunkZ + 1) << 4) - startZ) / dz : dz < 0.0 ? ((chunkZ << 4) - startZ) / dz : Double.POSITIVE_INFINITY;
				double edge = Math.min(edgeX, edgeZ);
				if (edge >= limit) {
					return limit;
				}

				travelled = Math.max(edge, travelled) + Math.max(EDGE_NUDGE, 8.0 * Math.ulp(Math.max(Math.abs(startX), Math.abs(startZ))) / Math.max(Math.abs(dx), Math.max(Math.abs(dz), 1.0E-9)));
				continue;
			}

			if (solid(column, x, y, z)) {
				return travelled;
			}

			double nextX = dx == 0.0 ? Double.POSITIVE_INFINITY : travelled + (dx > 0.0 ? x + 1 - px : px - x) * deltaX;
			double nextY = dy == 0.0 ? Double.POSITIVE_INFINITY : travelled + (dy > 0.0 ? y + 1 - py : py - y) * deltaY;
			double nextZ = dz == 0.0 ? Double.POSITIVE_INFINITY : travelled + (dz > 0.0 ? z + 1 - pz : pz - z) * deltaZ;
			boolean entered = false;

			while (!entered) {
				if (++steps > MAX_STEPS) {
					return overrun(limit, "steps");
				}

				double t;
				if (nextX <= nextY && nextX <= nextZ) {
					t = nextX;
					if (t > limit) {
						return limit;
					}

					x += stepX;
					nextX += deltaX;
					entered = (x >> 4) != chunkX;
				} else if (nextY <= nextZ) {
					t = nextY;
					if (t > limit) {
						return limit;
					}

					y += stepY;
					nextY += deltaY;
				} else {
					t = nextZ;
					if (t > limit) {
						return limit;
					}

					z += stepZ;
					nextZ += deltaZ;
					entered = (z >> 4) != chunkZ;
				}

				if (entered) {
					travelled = t + EDGE_NUDGE;
				} else if (solid(column, x, y, z)) {
					return t;
				}
			}
		}
	}

	private static boolean solid(final Column column, final int x, final int y, final int z) {
		if (column.chunk == null) {
			return false;
		}

		if (y < minY) {
			return true;
		}

		if (y > maxY || y >= column.free[((z & 15) << 4) | (x & 15)]) {
			return false;
		}

		LevelChunkSection section = column.chunk.getSection((y - minY) >> 4);
		if (section.hasOnlyAir()) {
			return false;
		}

		BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
		if (state.isAir()) {
			return false;
		}

		CURSOR.set(x, y, z);
		return !state.getCollisionShape(level, CURSOR).isEmpty();
	}

	/** The chunk's column data for this tick. An unloaded chunk answers with a null chunk and counts as open air. */
	private static Column columnAt(final int chunkX, final int chunkZ) {
		Column column = COLUMN_CACHE[((chunkX & 7) << 3) | (chunkZ & 7)];
		if (column.stamp == now && column.chunkX == chunkX && column.chunkZ == chunkZ && column.level == level) {
			return column;
		}

		column.level = level;
		column.chunkX = chunkX;
		column.chunkZ = chunkZ;
		column.stamp = now;
		column.chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
		column.highest = Integer.MIN_VALUE;
		if (column.chunk == null) {
			return column;
		}

		if (!column.chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE)) {
			java.util.Arrays.fill(column.free, Integer.MAX_VALUE);
			column.highest = Integer.MAX_VALUE;
			return column;
		}

		Heightmap surface = column.chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);
		int highest = Integer.MIN_VALUE;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int free = surface.getFirstAvailable(x, z);
				column.free[(z << 4) | x] = free;
				highest = Math.max(highest, free);
			}
		}

		column.highest = highest;
		return column;
	}
}
