package com.stella.learn.gym;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One seeded layout of an arena: every block from the floor's bottom to the ceiling, the two spawn cells, and the
 * doorway cells. The same seed and stage always give the same layout. Coordinates are relative to the arena origin, with
 * `dy` 0 as the floor surface.
 */
public final class Terrain {
	public static final int A = 0;
	public static final int B = 1;
	public static final int C = 2;
	public static final int RELEASE = 3;
	public static final int MIN_SPAWN_GAP = 6;
	private static final int OPEN = Arena.OPEN;
	private static final int FLOOR = Arena.FLOOR;
	private static final int LAYERS = OPEN + FLOOR;
	private static final int MARGIN = 2;
	private static final int SPAWN_TRIES = 64;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();

	/** The feature names, in the order that rotates through the release suite. */
	public static final List<String> FEATURES = List.of("flat", "pillars", "wall", "pits", "ledges", "water", "corridors");

	private final long seed;
	private final int stage;
	private final BlockState[] cells = new BlockState[LAYERS * OPEN * OPEN];
	private final List<String> features = new ArrayList<>();
	private final List<BlockPos> doorways = new ArrayList<>();
	private final List<BlockPos> ledgeTops = new ArrayList<>();
	private final List<BlockPos> spots = new ArrayList<>();
	private BlockPos mobSpawn;
	private BlockPos botSpawn;
	private boolean connected;
	private int dropped;

	private Terrain(final long seed, final int stage) {
		this.seed = seed;
		this.stage = stage;
	}

	/** A plain floor with fixed spawns, for tests that need no layout. */
	public static Terrain flat() {
		Terrain terrain = new Terrain(0L, A);
		terrain.fillBase();
		terrain.mobSpawn = new BlockPos(4, 0, 8);
		terrain.botSpawn = new BlockPos(11, 0, 8);
		terrain.connected = true;
		return terrain;
	}

	public static Terrain generate(final long seed, final int stage) {
		return generate(seed, stage, stage == A ? "flat" : null);
	}

	/** `forced` names the first feature to use, or null to choose from the seed. */
	public static Terrain generate(final long seed, final int stage, final String forced) {
		Terrain terrain = new Terrain(seed, stage);
		RandomSource random = RandomSource.create(seed * 0x9E3779B97F4A7C15L + stage);
		List<String> plan = terrain.plan(random, forced);
		List<String> drops = new ArrayList<>();
		while (true) {
			terrain.fillBase();
			terrain.features.clear();
			terrain.doorways.clear();
			terrain.ledgeTops.clear();
			RandomSource featureRandom = RandomSource.create(seed * 31L + stage);
			for (String name : plan) {
				terrain.apply(name, featureRandom);
			}

			if (terrain.chooseSpawns(RandomSource.create(seed * 17L + stage + 5L))) {
				break;
			}

			drops.add(plan.get(plan.size() - 1));
			plan = new ArrayList<>(plan.subList(0, plan.size() - 1));
			terrain.dropped++;
		}

		terrain.connected = terrain.pathBetween(terrain.mobSpawn, terrain.botSpawn) && terrain.pathBetween(terrain.botSpawn, terrain.mobSpawn);
		GymLog.info(GymLog.TERRAIN, "BUILD", "seed=" + seed + " stage=" + stage + " features=" + terrain.features
			+ " mobSpawn=" + terrain.mobSpawn.toShortString() + " botSpawn=" + terrain.botSpawn.toShortString()
			+ " doorways=" + terrain.doorways.size() + " path=" + terrain.connected + " dropped=" + drops);
		return terrain;
	}

	private List<String> plan(final RandomSource random, final String forced) {
		List<String> plan = new ArrayList<>();
		if (stage == A) {
			return plan;
		}

		if (stage == RELEASE && forced == null) {
			String main = FEATURES.get((int) Math.floorMod(seed, (long) FEATURES.size()));
			if (!"flat".equals(main)) {
				plan.add(main);
			}

			return plan;
		}

		if (forced != null) {
			if (!"flat".equals(forced)) {
				plan.add(forced);
			}

			return plan;
		}

		if (random.nextInt(5) == 0) {
			return plan;
		}

		int count = 1 + random.nextInt(3);
		List<String> pool = new ArrayList<>(FEATURES.subList(1, FEATURES.size()));
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			plan.add(pool.remove(random.nextInt(pool.size())));
		}

		return plan;
	}

	private void fillBase() {
		for (int dy = -FLOOR; dy < OPEN; dy++) {
			BlockState base = dy >= 0 ? AIR : dy >= -2 ? DIRT : STONE;
			for (int dz = 0; dz < OPEN; dz++) {
				for (int dx = 0; dx < OPEN; dx++) {
					cells[index(dx, dy, dz)] = base;
				}
			}
		}
	}

	private static int index(final int dx, final int dy, final int dz) {
		return ((dy + FLOOR) * OPEN + dz) * OPEN + dx;
	}

	private static boolean inside(final int dx, final int dy, final int dz) {
		return dx >= 0 && dx < OPEN && dz >= 0 && dz < OPEN && dy >= -FLOOR && dy < OPEN;
	}

	private void set(final int dx, final int dy, final int dz, final BlockState state) {
		if (inside(dx, dy, dz)) {
			cells[index(dx, dy, dz)] = state;
		}
	}

	public BlockState cell(final int dx, final int dy, final int dz) {
		return inside(dx, dy, dz) ? cells[index(dx, dy, dz)] : Blocks.BARRIER.defaultBlockState();
	}

	public long seed() {
		return seed;
	}

	public int stage() {
		return stage;
	}

	public List<String> features() {
		return features;
	}

	public BlockPos mobSpawn() {
		return mobSpawn;
	}

	public BlockPos botSpawn() {
		return botSpawn;
	}

	/** True when a walkable path joins the two spawn cells in both directions. */
	public boolean connected() {
		return connected;
	}

	/** Open floor cells a spawn could use: the pool of the last successful draw. */
	public List<BlockPos> spots() {
		return spots;
	}

	/** The feet cells inside a wall doorway. Empty when the layout has no wall. */
	public List<BlockPos> doorways() {
		return doorways;
	}

	/** The feet cell at the middle of each ledge top. */
	public List<BlockPos> ledgeTops() {
		return ledgeTops;
	}

	// -- features ---------------------------------------------------------------------------------------------------

	private void apply(final String name, final RandomSource random) {
		features.add(name);
		switch (name) {
			case "pillars" -> pillars(random);
			case "wall" -> wall(random);
			case "pits" -> pits(random);
			case "ledges" -> ledges(random);
			case "water" -> water(random);
			case "corridors" -> corridors(random);
			default -> features.remove(name);
		}
	}

	private void fill(final int x0, final int z0, final int width, final int depth, final int y0, final int y1, final BlockState state) {
		for (int dx = x0; dx < x0 + width; dx++) {
			for (int dz = z0; dz < z0 + depth; dz++) {
				for (int dy = y0; dy <= y1; dy++) {
					set(dx, dy, dz, state);
				}
			}
		}
	}

	private void pillars(final RandomSource random) {
		int count = 2 + random.nextInt(5);
		for (int i = 0; i < count; i++) {
			int size = 1 + random.nextInt(2);
			int height = 2 + random.nextInt(7);
			fill(1 + random.nextInt(OPEN - 2 - size), 1 + random.nextInt(OPEN - 2 - size), size, size, 0, height - 1, STONE);
		}
	}

	private void wall(final RandomSource random) {
		boolean alongX = random.nextBoolean();
		int line = 5 + random.nextInt(6);
		int doors = 1 + random.nextInt(2);
		boolean[] open = new boolean[OPEN];
		for (int d = 0; d < doors; d++) {
			int start = 1 + random.nextInt(OPEN - 4);
			open[start] = true;
			open[start + 1] = true;
			int middle = start;
			BlockPos cell = alongX ? new BlockPos(middle, 0, line) : new BlockPos(line, 0, middle);
			doorways.add(cell);
		}

		for (int along = 0; along < OPEN; along++) {
			if (open[along]) {
				continue;
			}

			for (int dy = 0; dy < 3; dy++) {
				if (alongX) {
					set(along, dy, line, STONE);
				} else {
					set(line, dy, along, STONE);
				}
			}
		}
	}

	private void pits(final RandomSource random) {
		int count = 1 + random.nextInt(3);
		for (int i = 0; i < count; i++) {
			int size = 2 + random.nextInt(2);
			int depth = 1 + random.nextInt(3);
			fill(1 + random.nextInt(OPEN - 2 - size), 1 + random.nextInt(OPEN - 2 - size), size, size, -depth, -1, AIR);
		}
	}

	private void ledges(final RandomSource random) {
		int count = 1 + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			int size = 3 + random.nextInt(2);
			int height = 3 + random.nextInt(6);
			int direction = random.nextInt(4);
			// The stair takes height - 1 cells beside the platform, so the pair must fit in the arena.
			int span = size + height - 1;
			int fitting = Math.max(1, OPEN - 2 - span);
			int px = 1 + random.nextInt(Math.max(1, OPEN - 2 - size));
			int pz = 1 + random.nextInt(Math.max(1, OPEN - 2 - size));
			switch (direction) {
				case 0 -> px = 1 + (height - 1) + random.nextInt(Math.max(1, OPEN - 2 - size - (height - 1)));
				case 1 -> px = 1 + random.nextInt(fitting);
				case 2 -> pz = 1 + (height - 1) + random.nextInt(Math.max(1, OPEN - 2 - size - (height - 1)));
				default -> pz = 1 + random.nextInt(fitting);
			}

			fill(px, pz, size, size, 0, height - 1, STONE);
			int midX = px + size / 2;
			int midZ = pz + size / 2;
			for (int step = 1; step < height; step++) {
				int offset = height - step;
				switch (direction) {
					case 0 -> fill(px - offset, midZ, 1, 1, 0, step - 1, STONE);
					case 1 -> fill(px + size - 1 + offset, midZ, 1, 1, 0, step - 1, STONE);
					case 2 -> fill(midX, pz - offset, 1, 1, 0, step - 1, STONE);
					default -> fill(midX, pz + size - 1 + offset, 1, 1, 0, step - 1, STONE);
				}
			}

			ledgeTops.add(new BlockPos(midX, height, midZ));
		}
	}

	private void water(final RandomSource random) {
		int size = 3 + random.nextInt(3);
		fill(1 + random.nextInt(OPEN - 2 - size), 1 + random.nextInt(OPEN - 2 - size), size, size, -1, -1, WATER);
	}

	private void corridors(final RandomSource random) {
		boolean alongX = random.nextBoolean();
		int length = 6 + random.nextInt(5);
		int start = 1 + random.nextInt(OPEN - 2 - length);
		int row = 2 + random.nextInt(OPEN - 6);
		for (int along = start; along < start + length; along++) {
			for (int dy = 0; dy < 3; dy++) {
				if (alongX) {
					set(along, dy, row, STONE);
					set(along, dy, row + 2, STONE);
				} else {
					set(row, dy, along, STONE);
					set(row + 2, dy, along, STONE);
				}
			}
		}
	}

	// -- spawns and paths -------------------------------------------------------------------------------------------

	private boolean chooseSpawns(final RandomSource random) {
		List<BlockPos> candidates = new ArrayList<>();
		for (int dx = MARGIN; dx < OPEN - MARGIN; dx++) {
			for (int dz = MARGIN; dz < OPEN - MARGIN; dz++) {
				if (standable(dx, 0, dz) && cell(dx, -1, dz).getBlock() != Blocks.WATER && clearAround(dx, dz)) {
					candidates.add(new BlockPos(dx, 0, dz));
				}
			}
		}

		for (int attempt = 0; attempt < SPAWN_TRIES && candidates.size() >= 2; attempt++) {
			BlockPos a = candidates.get(random.nextInt(candidates.size()));
			BlockPos b = candidates.get(random.nextInt(candidates.size()));
			spots.clear();
			spots.addAll(candidates);
			if (Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()) >= MIN_SPAWN_GAP
				&& pathBetween(a, b) && pathBetween(b, a)) {
				mobSpawn = a;
				botSpawn = b;
				return true;
			}
		}

		return false;
	}

	/** A spawn keeps its four neighbours open, so neither fighter starts wedged against a wall. */
	private boolean clearAround(final int dx, final int dz) {
		return Walker.free(this::cell, dx + 1, 0, dz) && Walker.free(this::cell, dx - 1, 0, dz)
			&& Walker.free(this::cell, dx, 0, dz + 1) && Walker.free(this::cell, dx, 0, dz - 1);
	}

	private boolean standable(final int dx, final int dy, final int dz) {
		return inside(dx, dy, dz) && Walker.standable(this::cell, dx, dy, dz);
	}

	/** Walks, one-block steps up, and drops of up to three blocks. Digging is not allowed. */
	public boolean pathBetween(final BlockPos from, final BlockPos to) {
		return Walker.path(this::cell, from, to) != null;
	}
}
