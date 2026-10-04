package com.stella.tower;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Builds a floor the first time anyone reaches it, and never again: a floor's corner wall block is its own
 * record that it exists, so a floor keeps every change made to it for good. Its maze is drawn from the
 * world seed and the floor number, so each floor is its own and the same every time it is drawn. A floor
 * built before the mazes came keeps the open room it was built with.
 * <p>
 * Building a floor at once takes long enough to stall the server, so floors are built ahead of the
 * climb, a column at a time within a small budget each tick: a player's arrival on a floor starts the one
 * above. The floor's chunks are asked for first and generated off the server thread; building starts once
 * they are all loaded. A floor someone needs before it is done is finished on the spot.
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
	private static long longestStepNanos;

	private TowerFloors() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerFloors::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			synchronized (QUEUE) {
				QUEUE.clear();
			}
		});
		// The caches are keyed by seed and floor, not by world: one JVM can host another world after this one stops
		// (a single player leaving one save for another), so nothing decided here may outlive the server.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearCaches());
	}

	/** Forgets every plan and every built floor's decision, as a stopped server does. */
	static void clearCaches() {
		synchronized (BUILT_VERSIONS) {
			BUILT_VERSIONS.clear();
		}
		TowerPlan.clearCache();
	}

	/** Queues a floor to be built in the background, unless it stands or is queued already. */
	public static void prepare(final ServerLevel tower, final int floor) {
		if (floor < 1 || isBuilt(tower, floor)) {
			return;
		}
		synchronized (QUEUE) {
			QUEUE.computeIfAbsent(floor, key -> new Build(tower, floor, TowerPlan.of(tower.getSeed(), floor)));
		}
	}

	public static boolean isQueued(final int floor) {
		synchronized (QUEUE) {
			return QUEUE.containsKey(floor);
		}
	}

	/** The longest single background step so far, in milliseconds. */
	public static double longestStepMillis() {
		return longestStepNanos / 1_000_000.0;
	}

	private static void tick(final MinecraftServer server) {
		long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
		synchronized (QUEUE) {
			Iterator<Build> jobs = QUEUE.values().iterator();
			while (jobs.hasNext() && System.nanoTime() < deadline) {
				Build job = jobs.next();
				while (System.nanoTime() < deadline) {
					long started = System.nanoTime();
					Step step = job.step(true);
					longestStepNanos = Math.max(longestStepNanos, System.nanoTime() - started);
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

	/**
	 * The block that proves a floor was built to a plan, laid just under the settle marker. A built floor
	 * without it was built before the plans and has the legacy plan.
	 */
	public static BlockPos planMarker(final int floor) {
		return TowerLayout.origin(floor).offset(-1, -3, -1);
	}

	/** The most version markers a floor can carry. */
	public static final int MAX_VERSIONS = 32;
	private static final int VERSION_CACHE = 4096;
	private record Built(long seed, int floor) {
	}

	private static final Map<Built, Integer> BUILT_VERSIONS = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(final Map.Entry<Built, Integer> eldest) {
			return this.size() > VERSION_CACHE;
		}
	};

	/**
	 * The version of the plan a built floor was built to: the count of tower wall blocks stacked downward from
	 * {@link #planMarker(int)}, so 0 for a floor built before the plans, which carries none.
	 */
	public static int planVersion(final ServerLevel tower, final int floor) {
		int version = 0;
		while (version < MAX_VERSIONS && tower.getBlockState(planMarker(floor).below(version)).is(TowerBlocks.TOWER_WALL)) {
			version++;
		}
		return version;
	}

	/**
	 * The plan version of a floor that stands, or -1 for one that does not yet. A standing floor's version is
	 * read from its blocks once and kept, so a sweep that asks for the plan of every mob's floor reads none of its
	 * markers (one block, the floor's own, tells that it stands).
	 * A floor not yet standing is never remembered, so one going up is never taken for an old one.
	 */
	static int builtVersion(final ServerLevel tower, final int floor) {
		// Whether the floor stands is asked first, of this world's blocks: a remembered decision is only ever served
		// for a floor that stands, so a cache built in another world, or before a floor was cleared, is never believed.
		if (!isBuilt(tower, floor)) {
			return -1;
		}
		Built key = new Built(tower.getSeed(), floor);
		synchronized (BUILT_VERSIONS) {
			Integer known = BUILT_VERSIONS.get(key);
			if (known != null) {
				return known;
			}
		}
		int version = planVersion(tower, floor);
		synchronized (BUILT_VERSIONS) {
			BUILT_VERSIONS.put(key, version);
		}
		return version;
	}

	/**
	 * {@link #builtVersion} that never loads a chunk: with the markers' chunk not loaded it answers with the
	 * remembered version, or -1 (the current plan) when none is remembered.
	 */
	static int builtVersionQuiet(final ServerLevel tower, final int floor) {
		if (!tower.hasChunkAt(marker(floor))) {
			synchronized (BUILT_VERSIONS) {
				Integer known = BUILT_VERSIONS.get(new Built(tower.getSeed(), floor));
				return known == null ? -1 : known;
			}
		}
		return builtVersion(tower, floor);
	}

	/** Test seam: forgets what is known of a floor's built version, so its blocks are read again. */
	static void forget(final int floor) {
		synchronized (BUILT_VERSIONS) {
			BUILT_VERSIONS.keySet().removeIf(key -> key.floor() == floor);
		}
	}

	public static boolean isPlanned(final ServerLevel tower, final int floor) {
		return planVersion(tower, floor) > 0;
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

	/**
	 * Builds the floor to a given plan unless it stands: the test seam that builds a floor as it was built
	 * before the plans, from {@link TowerPlan#legacy(long, int)}, which carries no plan marker.
	 */
	static boolean ensure(final ServerLevel tower, final int floor, final @Nullable TowerPlan plan) {
		if (floor < 1) {
			return false;
		}
		if (isBuilt(tower, floor)) {
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
	}

	private enum Step { PROGRESS, WAITING, DONE }

	/**
	 * How far round a floor's middle chunks are asked for. The room and walls reach three chunks out, and a
	 * ticket loads its outermost ring only partly, so it reaches one further.
	 */
	public static final int CHUNK_RADIUS = 4;
	/** A portal ticket lapses after 300 ticks; a floor still waiting asks again well before. */
	public static final int REASK_TICKS = 200;

	/** One floor's building, a column of the room at a time, then the pieces, the frame, and the marker. */
	private static final class Build {
		private final ServerLevel tower;
		private final int floor;
		private final Random random;
		private final ChunkPos middle;
		private final TowerPlan plan;
		private final boolean[][] lamps;
		private int askedAt = -1;
		private int column;

		private Build(final ServerLevel tower, final int floor, final TowerPlan plan) {
			this.tower = tower;
			this.floor = floor;
			this.random = new Random(tower.getSeed() ^ FLOOR_SALT ^ (floor * 0x9E3779B97F4A7C15L));
			this.plan = plan;
			this.lamps = lampSpots(plan);
			BlockPos origin = TowerLayout.origin(floor);
			this.middle = ChunkPos.containing(origin.offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2));
		}

		private Step step(final boolean mayWait) {
			if (mayWait) {
				int now = this.tower.getServer().getTickCount();
				if (this.askedAt < 0 || now - this.askedAt >= REASK_TICKS) {
					this.tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, this.middle, CHUNK_RADIUS);
					this.askedAt = now;
				}
				if (!loaded()) {
					return Step.WAITING;
				}
			}
			if (this.column <= TowerLayout.SIZE + 1) {
				buildColumn(this.tower, this.floor, TowerLayout.origin(this.floor).getX() - 1 + this.column, this.random, this.plan, this.lamps);
				this.column++;
				return Step.PROGRESS;
			}
			finish(this.tower, this.floor, this.plan);
			StellaTower.LOGGER.debug("[TOWER] floor={} BUILT origin={}", this.floor, TowerLayout.origin(this.floor));
			return Step.DONE;
		}

		private boolean loaded() {
			BlockPos origin = TowerLayout.origin(this.floor);
			int low = (origin.getX() - 1) >> 4;
			int high = (origin.getX() + TowerLayout.SIZE) >> 4;
			int lowZ = (origin.getZ() - 1) >> 4;
			int highZ = (origin.getZ() + TowerLayout.SIZE) >> 4;
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

	/**
	 * Where the ceiling lamps hang, by offset from the room's corner: on the grid of the maze's cells, the spot of a cell where
	 * its light falls on the path. Where that spot lies under a wall that reaches the ceiling (a variation whose walls do not
	 * follow the maze's cells), the lamp hangs over the nearest open spot within three blocks instead, or over none.
	 */
	static boolean[][] lampSpots(final TowerPlan plan) {
		boolean[][] lit = new boolean[TowerLayout.SIZE][TowerLayout.SIZE];
		for (int gx = 0; gx < TowerMaze.CELLS; gx++) {
			for (int gz = 0; gz < TowerMaze.CELLS; gz++) {
				int x = 1 + gx * LAMP_SPACING + LAMP_AT;
				int z = 1 + gz * LAMP_SPACING + LAMP_AT;
				int best = Integer.MAX_VALUE;
				int bx = -1;
				int bz = -1;
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						int ax = x + dx;
						int az = z + dz;
						if (ax < 1 || az < 1 || ax > TowerLayout.SIZE - 2 || az > TowerLayout.SIZE - 2) {
							continue;
						}
						boolean under = plan.walls()[ax][az] && plan.wallHeightAt(ax, az) >= TowerLayout.HEIGHT;
						int away = Math.max(Math.abs(dx), Math.abs(dz)) * 8 + Math.abs(dx) + Math.abs(dz);
						if (!under && away < best) {
							best = away;
							bx = ax;
							bz = az;
						}
					}
				}
				if (bx >= 0) {
					lit[bx][bz] = true;
				}
			}
		}
		return lit;
	}

	private static void buildColumn(final ServerLevel tower, final int floor, final int x, final Random random, final TowerPlan plan, final boolean[][] lamps) {
		BlockPos origin = TowerLayout.origin(floor);
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int base = TowerLayout.BASE_Y;
		int size = TowerLayout.SIZE;
		int top = base + TowerLayout.HEIGHT;
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		BlockState lamp = TowerBlocks.TOWER_LAMP.defaultBlockState();
		BlockState mazeWall = TowerBlocks.TOWER_MAZE_WALL.defaultBlockState();
		BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
		BlockState[] palette = palette(floor);
		{
			for (int z = z0 - 1; z <= z0 + size; z++) {
				boolean edge = x == x0 - 1 || x == x0 + size || z == z0 - 1 || z == z0 + size;
				set(tower, x, base - 2, z, bedrock);
				if (edge) {
					for (int y = base - 1; y <= top; y++) {
						// The corner that marks the floor as built goes down last, below.
						if (!(x == x0 - 1 && z == z0 - 1 && y == base - 1)) {
							set(tower, x, y, z, wall);
						}
					}
				} else {
					BlockState ground = palette[random.nextInt(palette.length)];
					set(tower, x, base - 1, z, plan.inSafeZone(x - x0, z - z0) ? SAFE_FLOOR.defaultBlockState() : ground);
					// Over a maze cell, never over a wall line, so the light falls on the paths.
					boolean lit = lamps[x - x0][z - z0];
					set(tower, x, top, z, lit ? lamp : wall);
					if (plan.walls()[x - x0][z - z0]) {
						// The safe room's ring is tower wall, which nothing breaks; the maze's own is maze wall.
						BlockState solid = plan.isRing(x - x0, z - z0) ? wall : mazeWall;
						int height = Math.min(top, base + plan.wallHeightAt(x - x0, z - z0));
						for (int y = base; y < height; y++) {
							set(tower, x, y, z, solid);
						}
					}
				}
			}
		}
	}

	private static void finish(final ServerLevel tower, final int floor, final TowerPlan plan) {
		finish(tower, floor, plan, floor, floor);
	}

	/**
	 * Builds a lost room's instance at once, to a plan moved to its cell: the traps of {@code trapFloor} and
	 * chests stocked as on {@code lootFloor}. True when it was built now.
	 */
	static boolean buildRoom(final ServerLevel tower, final int room, final TowerPlan plan, final int trapFloor, final int lootFloor) {
		if (isBuilt(tower, room)) {
			return false;
		}
		Random random = new Random(tower.getSeed() ^ FLOOR_SALT ^ (room * 0x9E3779B97F4A7C15L));
		boolean[][] lamps = lampSpots(plan);
		int x0 = TowerLayout.origin(room).getX();
		for (int column = 0; column <= TowerLayout.SIZE + 1; column++) {
			buildColumn(tower, room, x0 - 1 + column, random, plan, lamps);
		}
		finish(tower, room, plan, trapFloor, lootFloor);
		return true;
	}

	private static void finish(final ServerLevel tower, final int floor, final TowerPlan plan, final int trapFloor, final int lootFloor) {
		BlockPos origin = TowerLayout.origin(floor);
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		for (TowerTraps.Trap trap : TowerTraps.layout(tower.getSeed(), trapFloor, plan)) {
			tower.setBlock(origin.offset(trap.x(), -1, trap.z()), TowerBlocks.TRAPS.get(trap.theme()).defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		// A planned floor's portal stands on wall: a block under its frame, or a boss floor's pyramid.
		for (BlockPos pos : plan.plinth()) {
			tower.setBlock(pos, wall, Block.UPDATE_CLIENTS);
		}
		gates(tower, plan);
		BlockState frame = TowerBlocks.TOWER_FRAME.defaultBlockState();
		for (BlockPos pos : plan.portalFrame()) {
			tower.setBlock(pos, frame, Block.UPDATE_CLIENTS);
		}
		if (TowerBuffs.isBossFloor(trapFloor)) {
			TowerBuffs.mist(tower, floor);
		}
		stations(tower, plan);
		chests(tower, floor, plan, lootFloor);
		// One marker block a version, stacked downward, so the floor records the plan it was built to.
		for (int i = 0; i < plan.version(); i++) {
			tower.setBlock(planMarker(floor).below(i), wall, Block.UPDATE_CLIENTS);
		}
		tower.setBlock(settledMarker(floor), wall, Block.UPDATE_CLIENTS);
		tower.setBlock(marker(floor), wall, Block.UPDATE_CLIENTS);
		synchronized (BUILT_VERSIONS) {
			BUILT_VERSIONS.put(new Built(tower.getSeed(), floor), plan.version());
		}
	}

	/**
	 * Places the plan's double chests, once, as the floor goes up, and stocks each from a random of the seed, the floor
	 * and the spot, so the same floor draws the same loot. A legacy plan has no spots. The blocks are cleared first:
	 * a spot is open ground by the plan, so nothing is lost.
	 */
	private static void chests(final ServerLevel tower, final int floor, final TowerPlan plan, final int lootFloor) {
		BlockPos origin = plan.origin();
		int index = 0;
		for (TowerPlan.ChestSpot spot : plan.chestSpots()) {
			TowerPlan.Spot other = spot.second();
			BlockPos first = origin.offset(spot.x(), 0, spot.z());
			BlockPos second = origin.offset(other.x(), 0, other.z());
			tower.setBlock(first, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			tower.setBlock(second, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			// The left half is the one the facing's clockwise side lies beyond: the first block for north and east, the second for south and west.
			boolean firstIsLeft = spot.facing() == Direction.NORTH || spot.facing() == Direction.EAST;
			BlockPos left = firstIsLeft ? first : second;
			if (TowerChests.placeDouble(tower, left, spot.facing()) == null) {
				StellaTower.LOGGER.warn("Tower floor {}: no chest at {}", floor, first);
				continue;
			}
			RandomSource random = RandomSource.create(tower.getSeed() ^ (floor * 0x9E3779B97F4A7C15L) ^ (index * 0xC2B2AE3D27D4EB4FL) ^ 0x43485354L);
			TowerChests.fill(tower, first, lootFloor, spot.guaranteed(), random);
			index++;
		}
	}

	/**
	 * Finishes the safe room's openings, which the columns left as full-height gaps: two high, the wall closed
	 * over them, and the tower lamp on the lintel and on the jambs, so they are plain to see from across the hall.
	 */
	private static void gates(final ServerLevel tower, final TowerPlan plan) {
		BlockPos origin = plan.origin();
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		BlockState lamp = TowerBlocks.TOWER_LAMP.defaultBlockState();
		for (TowerPlan.Gate gate : plan.gates()) {
			for (TowerPlan.Spot cell : gate.cells()) {
				for (int y = GATE_HEIGHT; y < TowerLayout.HEIGHT; y++) {
					tower.setBlock(origin.offset(cell.x(), y, cell.z()), y == GATE_HEIGHT ? lamp : wall, Block.UPDATE_CLIENTS);
				}
			}
			for (TowerPlan.Spot jamb : gate.jambs()) {
				for (int y = 0; y <= GATE_HEIGHT; y++) {
					tower.setBlock(origin.offset(jamb.x(), y, jamb.z()), lamp, Block.UPDATE_CLIENTS);
				}
			}
		}
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
					for (int y = 0; y < TowerLayout.HEIGHT; y++) {
						BlockPos at = origin.offset(x, y, z);
						if (tower.getBlockState(at).is(TowerBlocks.TOWER_MAZE_WALL)) {
							tower.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						}
					}
					// Only the floor's own stone and traps: a block a player set into the floor stays.
					BlockPos ground = origin.offset(x, -1, z);
					BlockState was = tower.getBlockState(ground);
					if (was.getBlock() instanceof TowerTrapBlock || java.util.Arrays.stream(palette(floor)).anyMatch(stone -> stone.is(was.getBlock()))) {
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

	private static void set(final ServerLevel tower, final int x, final int y, final int z, final BlockState state) {
		tower.setBlock(new BlockPos(x, y, z), state, BUILD_FLAGS);
	}

	/**
	 * The stone a floor is built from. The first twenty floors are overworld stone; above them the tower
	 * turns from overworld to nether to end stone every ten floors.
	 */
	public static BlockState[] palette(final int floor) {
		int band = floor <= 20 ? 0 : ((floor - 21) / 10) % 3;
		return switch (band) {
			case 1 -> new BlockState[] {
				Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
				Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()
			};
			case 2 -> new BlockState[] {
				Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
				Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.END_STONE.defaultBlockState()
			};
			default -> new BlockState[] {
				Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
				Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState()
			};
		};
	}
}
