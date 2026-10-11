package com.stella.tower;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Builds a floor the first time anyone reaches it, and never again: a floor's corner wall block is its own
 * record that it exists, so a floor keeps every change made to it for good. Its plan is drawn from the
 * world seed and the floor number, so each floor is its own and the same every time it is drawn; the
 * builder sets the plan's {@link TowerVolume} block for block.
 * <p>
 * Building a floor at once takes long enough to stall the server, so floors are built ahead of the
 * climb, a short stretch of blocks at a time within a small budget each tick: a player's arrival on a floor starts
 * the one above. The floor's chunks are asked for first and generated off the server thread; building starts
 * once they are all loaded. A floor someone needs before it is done is finished on the spot.
 */
public final class TowerFloors {
	/** One lamp to a maze cell, over its middle. */
	public static final int LAMP_SPACING = TowerMaze.PITCH;
	public static final int LAMP_AT = TowerMaze.PATH / 2 - 1;
	/** A safe zone's floor, so it is plain to see where the mobs are not raised. */
	public static final Block SAFE_FLOOR = Blocks.SMOOTH_QUARTZ;
	private static final long FLOOR_SALT = 0x5DEECE66DL;
	/** Background building's share of each server tick. */
	public static final long TICK_BUDGET_NANOS = 3_000_000L;
	private static final Map<Integer, Build> QUEUE = new LinkedHashMap<>();
	private static final int MAX_QUEUED_FLOORS = 64;
	private static final int MAX_PENDING_PLANS = 64;
	private static @Nullable ThreadPoolExecutor planner;
	private static long longestStepNanos;
	private static long longestWaitNanos;
	private static long longestFinishNanos;
	private static long longestImmediateBuildNanos;
	private static long longestSynchronousPlanNanos;
	/** Step times for tests. */
	private static final List<Long> STEP_TIMES = new ArrayList<>();
	private static final int STEP_TIMES_KEPT = 4096;

	private TowerFloors() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerFloors::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			synchronized (QUEUE) {
				QUEUE.values().forEach(Build::cancel);
				QUEUE.clear();
				if (planner != null) {
					planner.shutdownNow();
					planner = null;
				}
			}
		});
		// The caches are keyed by seed and floor, not by world: one JVM can host another world after this one stops
		// (a single player leaving one save for another), so nothing decided here may outlive the server.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearCaches());
	}

	/** Forgets every plan, as a stopped server does. */
	static void clearCaches() {
		TowerPlan.clearCache();
		longestImmediateBuildNanos = 0L;
		longestSynchronousPlanNanos = 0L;
	}

	/** Queues a floor, then checks its built marker after its chunks load. */
	public static void prepare(final ServerLevel tower, final int floor) {
		if (floor < 1) {
			return;
		}
		synchronized (QUEUE) {
			if (!QUEUE.containsKey(floor) && QUEUE.size() < MAX_QUEUED_FLOORS) {
				long seed = tower.getSeed();
				QUEUE.put(floor, new Build(tower, floor, seed));
			}
		}
	}

	static ThreadPoolExecutor planner() {
		if (planner == null) {
			planner = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(MAX_PENDING_PLANS), work -> {
					Thread thread = new Thread(work, "stella-tower-plan");
					thread.setDaemon(true);
					return thread;
				});
		}
		return planner;
	}

	public static boolean isQueued(final int floor) {
		synchronized (QUEUE) {
			return QUEUE.containsKey(floor);
		}
	}

	static @Nullable Future<?> queuedPlan(final int floor) {
		synchronized (QUEUE) {
			Build job = QUEUE.get(floor);
			return job == null ? null : job.prepared;
		}
	}

	/** Longest background step in milliseconds. */
	public static double longestStepMillis() {
		return longestStepNanos / 1_000_000.0;
	}

	static double longestWaitMillis() {
		return longestWaitNanos / 1_000_000.0;
	}

	static double longestImmediateBuildMillis() {
		return longestImmediateBuildNanos / 1_000_000.0;
	}

	static double longestSynchronousPlanMillis() {
		return longestSynchronousPlanNanos / 1_000_000.0;
	}

	/** How many collections the garbage collectors have run, all told. */
	private static long collections() {
		long total = 0;
		for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
			total += Math.max(0, bean.getCollectionCount());
		}
		return total;
	}

	/** A copy of the step times recorded: negative for a step a garbage collection ran during. */
	static List<Long> stepTimes() {
		synchronized (QUEUE) {
			return List.copyOf(STEP_TIMES);
		}
	}

	static void clearStepTimes() {
		synchronized (QUEUE) {
			STEP_TIMES.clear();
			longestStepNanos = 0L;
			longestWaitNanos = 0L;
			longestFinishNanos = 0L;
		}
	}

	/** Longest marker step in milliseconds. */
	public static double longestFinishMillis() {
		return longestFinishNanos / 1_000_000.0;
	}

	private static void tick(final MinecraftServer server) {
		long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
		synchronized (QUEUE) {
			Iterator<Build> jobs = QUEUE.values().iterator();
			while (jobs.hasNext() && System.nanoTime() < deadline) {
				Build job = jobs.next();
				while (System.nanoTime() < deadline) {
					long collected = collections();
					long started = System.nanoTime();
					Step step = job.step(true);
					long took = System.nanoTime() - started;
					if (step == Step.DONE) {
						longestFinishNanos = Math.max(longestFinishNanos, took);
					} else {
						longestStepNanos = Math.max(longestStepNanos, took);
						if (step == Step.WAITING) {
							longestWaitNanos = Math.max(longestWaitNanos, took);
						}
						if (STEP_TIMES.size() < STEP_TIMES_KEPT) {
							// A step the collector paused is kept apart, marked by its sign: its time is the collector's.
							STEP_TIMES.add(collections() == collected ? took : -took);
						}
					}
					if (step == Step.DONE) {
						jobs.remove();
						break;
					}
					if (step == Step.WAITING) {
						break;
					}
				}
			}
		}
	}

	/**
	 * A boss floor's safe room holds a crafting table, a furnace and an anvil along its far wall, clear of
	 * its doors. Empty on any other floor.
	 */
	public static Map<BlockPos, BlockState> workstations(final TowerPlan plan) {
		BlockPos origin = plan.origin();
		Map<BlockPos, BlockState> stations = new LinkedHashMap<>();
		for (TowerPlan.Station station : plan.stations()) {
			BlockPos at = origin.offset(station.x(), 0, station.z());
			// Facing into the room, away from the wall behind them.
			stations.put(at, switch (station.kind()) {
				case 0 -> Blocks.CRAFTING_TABLE.defaultBlockState();
				case 1 -> Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, station.facing());
				default -> Blocks.ANVIL.defaultBlockState().setValue(net.minecraft.world.level.block.AnvilBlock.FACING, station.facing());
			});
		}
		return stations;
	}

	/** The block that proves a floor was built. */
	public static BlockPos marker(final int floor) {
		return TowerLayout.origin(floor).offset(-1, -1, -1);
	}

	public static boolean isBuilt(final ServerLevel tower, final int floor) {
		return tower.getBlockState(marker(floor)).is(TowerBlocks.TOWER_WALL);
	}

	/** Builds the floor unless it already stands; true when it was built now. */
	public static boolean ensure(final ServerLevel tower, final int floor) {
		return ensure(tower, floor, null);
	}

	/** Builds the floor to a given plan unless it stands: the test seam that builds a floor to a plan of a test's choosing. */
	static boolean ensure(final ServerLevel tower, final int floor, final @Nullable TowerPlan plan) {
		long started = System.nanoTime();
		try {
			if (floor < 1) {
				return false;
			}
			if (isBuilt(tower, floor)) {
				synchronized (QUEUE) {
					Build skipped = QUEUE.remove(floor);
					if (skipped != null) {
						skipped.cancel();
					}
				}
				settle(tower, floor);
				return false;
			}
			Build job;
			synchronized (QUEUE) {
				job = QUEUE.remove(floor);
			}
			if (job == null) {
				job = new Build(tower, floor, plan == null ? TowerPlan.of(tower.getSeed(), floor) : plan);
			}
			while (job.step(false) != Step.DONE) {
				// Finishes whatever the background left undone, loading chunks as it goes.
			}
			return true;
		} finally {
			longestImmediateBuildNanos = Math.max(longestImmediateBuildNanos, System.nanoTime() - started);
		}
	}

	private enum Step { PROGRESS, WAITING, DONE }

	private record Planned(TowerPlan plan, TowerVolume volume) {
	}

	/**
	 * How far round a base floor's middle chunks are asked for. The room and walls reach three chunks out, and a
	 * ticket loads its outermost ring only partly, so it reaches one further.
	 */
	public static final int CHUNK_RADIUS = 4;

	/** How far round the middle chunks a room of this width and depth is asked for: two chunks more than half its longer side. */
	public static int chunkRadius(final int width, final int depth) {
		return Math.max(width, depth) / 32 + 2;
	}

	/** {@link #chunkRadius(int, int)} for a plan's room. */
	public static int chunkRadius(final TowerPlan plan) {
		return chunkRadius(plan.width(), plan.depth());
	}
	/** A portal ticket lapses after 300 ticks; a floor still waiting asks again well before. */
	public static final int REASK_TICKS = 200;

	/** How long one step sets blocks before it stops and keeps its place: a third of the tick's budget. */
	public static final long STEP_WORK_NANOS = TICK_BUDGET_NANOS / 3;
	/** How many blocks a step sets between looks at the clock. */
	private static final int CLOCK_EVERY = 1;
	/** Every block the building of the tallest base-size floor sets before its finish: the room and its walls, bedrock to roof. */
	public static final int CELLS = (TowerLayout.SIZE + 2) * (TowerLayout.ROOM_HEIGHT - TowerLayout.ROOM_LOW + 1) * (TowerLayout.SIZE + 2);

	/** Every block a plan's building sets before its finish: the room and its walls, bedrock to roof. */
	static int cellsOf(final TowerPlan plan) {
		return (plan.width() + 2) * (plan.roofY() - plan.low() + 1) * (plan.depth() + 2);
	}

	/** One floor's building, block by block in a fixed order, a short stretch a step, then the traps, the chests, and the marker. */
	private static final class Build {
		private final ServerLevel tower;
		private final int floor;
		private final long seed;
		private final ChunkPos middle;
		/** The room's size, known from the seed before the plan is drawn. */
		private final int width;
		private final int depth;
		private @Nullable TowerPlan plan;
		private @Nullable TowerVolume volume;
		private @Nullable Future<Planned> prepared;
		private int askedAt = -1;
		private boolean checkedBuilt;
		private int cell;
		private @Nullable List<TowerTraps.Trap> traps;
		private @Nullable List<Map.Entry<BlockPos, BlockState>> stations;
		private int trapIndex;
		private int mistIndex;
		private int paintIndex;
		private int stationIndex;
		private int chestIndex;
		private int lootIndex;
		private boolean settled;

		private Build(final ServerLevel tower, final int floor, final TowerPlan plan) {
			this.tower = tower;
			this.floor = floor;
			this.seed = plan.seed();
			this.plan = plan;
			this.volume = TowerVolume.of(plan);
			BlockPos origin = TowerLayout.origin(floor);
			this.width = plan.width();
			this.depth = plan.depth();
			this.middle = ChunkPos.containing(origin.offset(this.width / 2, 0, this.depth / 2));
		}

		private Build(final ServerLevel tower, final int floor, final long seed) {
			this.tower = tower;
			this.floor = floor;
			this.seed = seed;
			BlockPos origin = TowerLayout.origin(floor);
			TowerSizes.Size size = TowerSizes.of(seed, floor);
			this.width = size.width();
			this.depth = size.depth();
			this.middle = ChunkPos.containing(origin.offset(this.width / 2, 0, this.depth / 2));
		}

		private void cancel() {
			if (this.prepared != null) {
				this.prepared.cancel(true);
				if (planner != null) {
					planner.purge();
				}
			}
		}

		private boolean ready(final boolean mayWait) {
			if (this.plan != null) {
				return true;
			}
			if (!mayWait && this.prepared != null && !this.prepared.isDone()) {
				cancel();
				this.prepared = null;
			}
			if (this.prepared == null) {
				if (!mayWait) {
					long started = System.nanoTime();
					TowerPlan drawn = TowerPlan.of(this.seed, this.floor);
					this.plan = drawn;
					this.volume = TowerVolume.of(drawn);
					longestSynchronousPlanNanos = Math.max(longestSynchronousPlanNanos, System.nanoTime() - started);
					return true;
				}
				ThreadPoolExecutor worker = planner();
				if (worker.getQueue().remainingCapacity() == 0) {
					return false;
				}
				long planSeed = this.seed;
				int planFloor = this.floor;
				try {
					this.prepared = worker.submit(() -> {
						TowerPlan drawn = TowerPlan.of(planSeed, planFloor);
						return new Planned(drawn, TowerVolume.of(drawn));
					});
				} catch (RejectedExecutionException e) {
					return false;
				}
			}
			if (mayWait && !this.prepared.isDone()) {
				return false;
			}
			try {
				Planned result = this.prepared.get();
				this.plan = result.plan();
				this.volume = result.volume();
				return true;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("tower plan interrupted", e);
			} catch (ExecutionException e) {
				throw new IllegalStateException("tower plan failed", e.getCause());
			}
		}

		private Step step(final boolean mayWait) {
			if (mayWait) {
				int now = this.tower.getServer().getTickCount();
				if (this.askedAt < 0 || now - this.askedAt >= REASK_TICKS) {
					this.tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, this.middle, chunkRadius(this.width, this.depth));
					this.askedAt = now;
				}
				if (!loaded()) {
					return Step.WAITING;
				}
				if (!this.checkedBuilt) {
					this.checkedBuilt = true;
					if (isBuilt(this.tower, this.floor)) {
						cancel();
						return Step.DONE;
					}
				}
			}
			if (!ready(mayWait)) {
				return Step.WAITING;
			}
			TowerPlan plan = this.plan;
			TowerVolume volume = this.volume;
			int cells = cellsOf(plan);
			if (this.cell < cells) {
				long until = System.nanoTime() + STEP_WORK_NANOS;
				TowerTheme theme = plan.theme();
				do {
					buildCell(this.tower, this.floor, this.cell, plan, theme, volume);
					this.cell++;
				} while (this.cell < cells && (this.cell % CLOCK_EVERY != 0 || System.nanoTime() < until));
				return Step.PROGRESS;
			}
			return finishStep(plan);
		}

		private Step finishStep(final TowerPlan plan) {
			BlockPos origin = TowerLayout.origin(this.floor);
			if (this.traps == null) {
				this.traps = TowerTraps.layout(this.tower.getSeed(), this.floor, plan);
				this.stations = new ArrayList<>(workstations(plan).entrySet());
			}
			if (this.trapIndex < this.traps.size()) {
				TowerTraps.Trap trap = this.traps.get(this.trapIndex++);
				this.tower.setBlock(origin.offset(trap.x(), trap.y() - 1, trap.z()), TowerBlocks.TRAPS.get(trap.theme()).defaultBlockState(), Block.UPDATE_CLIENTS);
				return Step.PROGRESS;
			}
			int lowX = (origin.getX() - 1) >> 4;
			int lowZ = (origin.getZ() - 1) >> 4;
			int chunksX = ((origin.getX() + plan.width()) >> 4) - lowX + 1;
			int chunksZ = ((origin.getZ() + plan.depth()) >> 4) - lowZ + 1;
			if (plan.isBiome() && !TowerBuffs.isBossFloor(this.floor) && this.paintIndex < chunksX * chunksZ) {
				int cx = lowX + this.paintIndex / chunksZ;
				int cz = lowZ + this.paintIndex % chunksZ;
				TowerBiomes.paintChunk(this.tower, cx, cz, plan.biome().biome());
				this.paintIndex++;
				return Step.PROGRESS;
			}
			if (TowerBuffs.isBossFloor(this.floor) && this.mistIndex < chunksX * chunksZ) {
				int cx = lowX + this.mistIndex / chunksZ;
				int cz = lowZ + this.mistIndex % chunksZ;
				TowerBuffs.mistChunk(this.tower, cx, cz);
				this.mistIndex++;
				return Step.PROGRESS;
			}
			if (this.stationIndex < this.stations.size()) {
				Map.Entry<BlockPos, BlockState> station = this.stations.get(this.stationIndex++);
				if (this.tower.getBlockState(station.getKey()).isAir()) {
					this.tower.setBlock(station.getKey(), station.getValue(), Block.UPDATE_CLIENTS);
				}
				return Step.PROGRESS;
			}
			if (this.chestIndex < plan.chestSpots().size()) {
				if (chest(this.tower, this.floor, plan, this.floor, plan.chestSpots().get(this.chestIndex), this.lootIndex)) {
					this.lootIndex++;
				}
				this.chestIndex++;
				return Step.PROGRESS;
			}
			if (!this.settled) {
				this.tower.setBlock(settledMarker(this.floor), TowerBlocks.TOWER_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
				this.settled = true;
				return Step.PROGRESS;
			}
			this.tower.setBlock(marker(this.floor), TowerBlocks.TOWER_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
			StellaTower.LOGGER.debug("[TOWER] floor={} BUILT origin={}", this.floor, origin);
			return Step.DONE;
		}

		private boolean loaded() {
			BlockPos origin = TowerLayout.origin(this.floor);
			int low = (origin.getX() - 1) >> 4;
			int high = (origin.getX() + this.width) >> 4;
			int lowZ = (origin.getZ() - 1) >> 4;
			int highZ = (origin.getZ() + this.depth) >> 4;
			for (int cx = low; cx <= high; cx++) {
				for (int cz = lowZ; cz <= highZ; cz++) {
					if (this.tower.getChunkSource().getChunkNow(cx, cz) == null) {
						return false;
					}
				}
			}
			return true;
		}
	}

	/** A spread of the seed, the floor and a spot, so the same block of the same floor always draws the same stone. */
	static int hash(final long seed, final int floor, final int x, final int y, final int z) {
		long mixed = TowerPlan.mix(seed ^ FLOOR_SALT ^ (floor * 0x9E3779B97F4A7C15L) ^ (x * 0x632BE59BD9B4E019L) ^ (y * 0x85157AF5L) ^ (z * 0xC2B2AE3D27D4EB4FL));
		return (int) (mixed ^ (mixed >>> 32)) & Integer.MAX_VALUE;
	}

	/**
	 * Sets one block of the room, by its place in the build's order (x, then y, then z, from the outer wall's corner):
	 * the room's outer wall at its edge, and inside, the plan's volume block for block. Air is set only where something
	 * else stands, so a fresh floor's empty air costs a read, and a floor left half built by a stop is cleared of
	 * anything the plan does not hold.
	 */
	private static void buildCell(final ServerLevel tower, final int floor, final int index, final TowerPlan plan, final TowerTheme theme,
		final TowerVolume volume) {
		int spanY = plan.roofY() - plan.low() + 1;
		int width = plan.width();
		int depth = plan.depth();
		int spanZ = depth + 2;
		int lx = index / (spanY * spanZ) - 1;
		int y = (index / spanZ) % spanY + plan.low();
		int lz = index % spanZ - 1;
		BlockPos origin = TowerLayout.origin(floor);
		BlockPos pos = origin.offset(lx, y, lz);
		boolean edge = lx == -1 || lx == width || lz == -1 || lz == depth;
		if (edge) {
			// The corner that marks the floor as built goes down last, in the finish.
			if (!(lx == -1 && lz == -1 && y == -1)) {
				tower.setBlock(pos, y == plan.low() ? Blocks.BEDROCK.defaultBlockState() : TowerBlocks.TOWER_WALL.defaultBlockState(), BUILD_FLAGS);
			}
			return;
		}
		BlockState state = stateOf(volume, theme, plan, lx, y, lz);
		if (!state.isAir() || !tower.getBlockState(pos).isAir()) {
			tower.setBlock(pos, state, BUILD_FLAGS);
		}
	}

	/** The block a part of the volume is built from, in the floor's theme. A chest and a station wait for the finish. */
	static BlockState stateOf(final TowerVolume volume, final TowerTheme theme, final TowerPlan plan, final int x, final int y, final int z) {
		byte kind = volume.kind(x, y, z);
		int hash = hash(plan.seed(), plan.look(), x, y, z);
		return switch (kind) {
			case TowerVolume.BEDROCK -> Blocks.BEDROCK.defaultBlockState();
			case TowerVolume.GROUND -> theme.ground()[hash % theme.ground().length];
			case TowerVolume.SAFE -> SAFE_FLOOR.defaultBlockState();
			case TowerVolume.RUNNER -> theme.runner();
			case TowerVolume.WALL -> TowerBlocks.TOWER_MAZE_WALL.skinned(theme.wall()[hash % theme.wall().length]);
			case TowerVolume.PILLAR -> TowerBlocks.TOWER_MAZE_WALL.skinned(theme.pillar());
			case TowerVolume.SLAB -> TowerBlocks.TOWER_MAZE_WALL.skinned(theme.floor()[hash % theme.floor().length]);
			case TowerVolume.LID, TowerVolume.ROOF, TowerVolume.PLINTH -> TowerBlocks.TOWER_WALL.defaultBlockState();
			case TowerVolume.LAMP -> TowerBlocks.TOWER_LAMP.skinned(theme.lamp());
			case TowerVolume.STAIR_NORTH -> stair(theme, Direction.NORTH);
			case TowerVolume.STAIR_EAST -> stair(theme, Direction.EAST);
			case TowerVolume.STAIR_SOUTH -> stair(theme, Direction.SOUTH);
			case TowerVolume.STAIR_WEST -> stair(theme, Direction.WEST);
			case TowerVolume.STEP, TowerVolume.SUPPORT, TowerVolume.COVER -> theme.step();
			case TowerVolume.RAIL -> rail(volume, theme, x, y, z);
			case TowerVolume.LANTERN -> theme.lantern().setValue(LanternBlock.HANGING, true);
			case TowerVolume.CHAIN -> Blocks.IRON_CHAIN.defaultBlockState();
			case TowerVolume.CLUSTER -> Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN);
			case TowerVolume.FRAME -> TowerBlocks.TOWER_FRAME.defaultBlockState();
			case TowerVolume.DAIS_STAIR_EAST -> TowerBlocks.TOWER_STAIR.skinned(plan.biome().biome().daisSkin()).setValue(StairBlock.FACING, Direction.EAST);
			case TowerVolume.DAIS_STAIR_WEST -> TowerBlocks.TOWER_STAIR.skinned(plan.biome().biome().daisSkin()).setValue(StairBlock.FACING, Direction.WEST);
			case TowerVolume.BIOME_BLOCK -> BiomeBlocks.state(volume.code(x, y, z));
			case TowerVolume.PORTAL_FILL -> TowerBlocks.TOWER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.Z);
			default -> Blocks.AIR.defaultBlockState();
		};
	}

	private static BlockState stair(final TowerTheme theme, final Direction climbs) {
		return theme.stair().setValue(StairBlock.FACING, climbs);
	}

	/** A rail joined to every rail and every wall beside it, as one placed by hand would be. */
	private static BlockState rail(final TowerVolume volume, final TowerTheme theme, final int x, final int y, final int z) {
		BlockState rail = theme.rail().setValue(WallBlock.UP, true);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			byte beside = volume.kind(x + side.getStepX(), y, z + side.getStepZ());
			boolean joins = beside == TowerVolume.RAIL || TowerVolume.walkClass(beside) == TowerWalk.SOLID && beside != TowerVolume.LANTERN
				&& beside != TowerVolume.CHAIN && beside != TowerVolume.CLUSTER && beside != TowerVolume.CHEST && !TowerVolume.isStair(beside);
			rail = rail.setValue(WallBlock.PROPERTY_BY_DIRECTION.get(side), joins ? WallSide.LOW : WallSide.NONE);
		}
		return rail;
	}

	/**
	 * Builds a lost room's instance at once, to a plan moved to its cell: the traps of {@code trapFloor} and
	 * chests stocked as on {@code lootFloor}. True when it was built now.
	 */
	static boolean buildRoom(final ServerLevel tower, final int room, final TowerPlan plan, final int trapFloor, final int lootFloor) {
		if (isBuilt(tower, room)) {
			return false;
		}
		TowerVolume volume = TowerVolume.of(plan);
		TowerTheme theme = plan.theme();
		for (int cell = 0; cell < cellsOf(plan); cell++) {
			buildCell(tower, room, cell, plan, theme, volume);
		}
		finishRoom(tower, room, plan, trapFloor, lootFloor);
		return true;
	}

	/** Finishes a lost room built on demand. */
	private static void finishRoom(final ServerLevel tower, final int floor, final TowerPlan plan, final int trapFloor, final int lootFloor) {
		BlockPos origin = TowerLayout.origin(floor);
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		List<TowerTraps.Trap> traps = TowerTraps.layout(tower.getSeed(), trapFloor, plan);
		for (TowerTraps.Trap trap : traps) {
			tower.setBlock(origin.offset(trap.x(), trap.y() - 1, trap.z()), TowerBlocks.TRAPS.get(trap.theme()).defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		if (TowerBuffs.isBossFloor(trapFloor)) {
			TowerBuffs.mist(tower, floor);
		}
		stations(tower, plan);
		chests(tower, floor, plan, lootFloor);
		// Last, so the room's design, traps and chests are all drawn before the things the dead left in it.
		TowerProps.scatterRoom(tower, floor, plan, traps);
		tower.setBlock(settledMarker(floor), wall, Block.UPDATE_CLIENTS);
		tower.setBlock(marker(floor), wall, Block.UPDATE_CLIENTS);
	}

	/**
	 * Places the plan's double chests, once, as the floor goes up, and stocks each from a random of the seed, the floor
	 * and the spot, so the same floor draws the same loot. A legacy plan has no spots. The blocks are cleared first:
	 * a spot is open ground by the plan, so nothing is lost.
	 */
	private static void chests(final ServerLevel tower, final int floor, final TowerPlan plan, final int lootFloor) {
		int index = 0;
		for (TowerPlan.ChestSpot spot : plan.chestSpots()) {
			if (chest(tower, floor, plan, lootFloor, spot, index)) {
				index++;
			}
		}
	}

	private static boolean chest(final ServerLevel tower, final int floor, final TowerPlan plan, final int lootFloor,
		final TowerPlan.ChestSpot spot, final int index) {
		BlockPos origin = plan.origin();
		TowerPlan.Spot other = spot.second();
		BlockPos first = origin.offset(spot.x(), spot.y(), spot.z());
		BlockPos second = origin.offset(other.x(), spot.y(), other.z());
		tower.setBlock(first, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		tower.setBlock(second, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		boolean firstIsLeft = spot.facing() == Direction.NORTH || spot.facing() == Direction.EAST;
		BlockPos left = firstIsLeft ? first : second;
		if (TowerChests.placeDouble(tower, left, spot.facing()) == null) {
			StellaTower.LOGGER.warn("Tower floor {}: no chest at {}", floor, first);
			return false;
		}
		RandomSource random = RandomSource.create(tower.getSeed() ^ (floor * 0x9E3779B97F4A7C15L) ^ (index * 0xC2B2AE3D27D4EB4FL) ^ 0x43485354L);
		TowerChests.fill(tower, first, lootFloor, spot.guaranteed(), random);
		return true;
	}

	/** How high the safe room's openings are: two blocks, and the lintel's lamp above them. */
	public static final int GATE_HEIGHT = 2;

	/** The block that proves a floor has its safe zones. */
	public static BlockPos settledMarker(final int floor) {
		return TowerLayout.origin(floor).offset(-1, -2, -1);
	}

	/**
	 * Lays the safe zones into a floor built before them, the first time anyone arrives: the maze walls in
	 * them are cleared, their floor laid, and a boss floor's workstations set. Only the maze's own blocks
	 * are cleared, only the floor's own stone and traps relaid, and stations set only onto open floor, so
	 * whatever a player built there stays.
	 */
	public static void settle(final ServerLevel tower, final int floor) {
		if (tower.getBlockState(settledMarker(floor)).is(TowerBlocks.TOWER_WALL)) {
			return;
		}
		BlockPos origin = TowerLayout.origin(floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		for (TowerMaze.Zone zone : plan.safeZones()) {
			for (int x = zone.x(); x < zone.x() + TowerMaze.SAFE; x++) {
				for (int z = zone.z(); z < zone.z() + TowerMaze.SAFE; z++) {
					for (int y = zone.y(); y < zone.top(); y++) {
						BlockPos at = origin.offset(x, y, z);
						if (tower.getBlockState(at).is(TowerBlocks.TOWER_MAZE_WALL)) {
							tower.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						}
					}
					// Only the floor's own stone and traps: a block a player set into the floor stays.
					BlockPos ground = origin.offset(x, zone.y() - 1, z);
					BlockState was = tower.getBlockState(ground);
					if (was.getBlock() instanceof TowerTrapBlock || java.util.Arrays.stream(palette(plan)).anyMatch(stone -> stone.is(was.getBlock()))) {
						tower.setBlock(ground, SAFE_FLOOR.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
		stations(tower, plan);
		tower.setBlock(settledMarker(floor), TowerBlocks.TOWER_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
		StellaTower.LOGGER.debug("[TOWER] floor={} SETTLED safe zones into an older floor", floor);
	}

	private static void stations(final ServerLevel tower, final TowerPlan plan) {
		// Only onto open floor, so nothing a player set there is lost.
		workstations(plan).forEach((pos, state) -> {
			if (tower.getBlockState(pos).isAir()) {
				tower.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}
		});
	}

	/** A fresh floor has no neighbours to tell and no shapes to fit, so only the clients hear of each block. */
	private static final int BUILD_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;


	/** The stone a floor's ground is laid from: its theme's. */
	public static BlockState[] palette(final TowerPlan plan) {
		return plan.theme().ground();
	}
}
