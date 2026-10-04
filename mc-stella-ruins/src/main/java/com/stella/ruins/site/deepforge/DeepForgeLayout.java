package com.stella.ruins.site.deepforge;

import com.stella.ruins.world.LayoutContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Draws the Deep Forge. The whole plan is rolled again on every chunk, then drawn through a {@link Brush} that only
 * touches the chunk. Order matters: seal the rock, build every wall, carve every room and tunnel, then lay floors and
 * decoration. A wall pass that follows a carve pass would wall a tunnel shut.
 */
final class DeepForgeLayout {
	private static final BlockState BRICKS = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
	private static final BlockState CRACKED = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
	private static final BlockState DARK_BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
	private static final BlockState COBBLE = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
	private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();

	/** Where the dais, core, forge block and vault stand. */
	static final int CORE_X = 31;
	static final int CORE_Y = 8;
	static final int CORE_Z = 30;
	static final int FORGE_Z = 33;
	static final int VAULT_Z = 15;
	static final int GUARDIAN_Z = 38;

	private final LayoutContext ctx;
	private final Brush b;
	private final Plan plan;
	private final long seed;
	private final Brush.Bars bars = new Brush.Bars();
	private final List<int[]> lava = new ArrayList<>();
	private final Set<Long> deck = new HashSet<>();

	private DeepForgeLayout(final LayoutContext ctx) {
		this.ctx = ctx;
		this.plan = Plan.roll(ctx.random());
		this.seed = ctx.frame().seed();
		this.b = Brush.probe(ctx);
		lavaRects();
	}

	/** The layout the site registers. */
	static void draw(final LayoutContext ctx) {
		new DeepForgeLayout(ctx).run();
	}

	/** The plan a layout context would draw, for tests. */
	static Plan planOf(final LayoutContext ctx) {
		return Plan.roll(ctx.random());
	}

	private void run() {
		markers();
		if (!this.b.empty()) {
			walls();
			carve();
			this.b.seal(Blocks.DEEPSLATE.defaultBlockState());
			furnish();
			this.bars.flush(this.b);
		}
		this.ctx.placeCore(CORE_X, CORE_Y, CORE_Z);
		this.ctx.placeVault(CORE_X, 7, VAULT_Z);
		for (Plan.SpurPlan spur : this.plan.spurs()) {
			if (!spur.closed()) {
				int[] at = spurChest(spur);
				this.ctx.placeChest(at[0], spur.level().floor + 1, at[1], spur.out().getOpposite(), DeepForgeSite.CACHE_TABLE);
			}
		}
	}

	// ------------------------------------------------------------------ blocks

	private static int hash(final long seed, final int x, final int y, final int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) (h & 0x7FFFFFFF);
	}

	private BlockState wall(final int x, final int y, final int z) {
		int roll = hash(this.seed, x, y, z) % 100;
		if (roll < this.plan.crackPercent()) {
			return CRACKED;
		}
		return roll < this.plan.crackPercent() + 12 ? DARK_BRICKS : BRICKS;
	}

	private static BlockState floorOf(final Geo.Level level) {
		return switch (level) {
			case A -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
			case B -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
			case C -> Blocks.DEEPSLATE_TILES.defaultBlockState();
		};
	}

	private static BlockState stair(final Direction facing) {
		return Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
	}

	private static BlockState lantern() {
		return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
	}

	private BlockState rubble(final int x, final int y, final int z) {
		return hash(this.seed, x, y, z) % 3 == 0 ? TUFF : COBBLE;
	}

	private void wallBox(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1) {
		this.b.box(x0, y0, z0, x1, y1, z1, this::wall);
	}

	private void air(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1) {
		this.b.carve(x0, y0, z0, x1, y1, z1);
	}

	// ------------------------------------------------------------------ markers

	private void markers() {
		for (Plan.ChamberPlan c : this.plan.chambers()) {
			Template t = c.template();
			int f = c.floorY();
			int[] impAt = c.slot().cell(t.impA, t.impB);
			this.ctx.marker("imp", impAt[0], f + 1, impAt[1]);
			if (c.holder()) {
				int[] at = c.slot().cell(t.holderA, t.holderB);
				this.ctx.marker("holder", at[0], f + 1, at[1]);
			}
			if (c.lever()) {
				int[] at = c.slot().cell(t.leverA, t.leverB);
				this.ctx.marker("lever", at[0], f + 1, at[1]);
			}
			if (t == Template.LAVA) {
				int[] at = c.slot().cell(5, 3);
				this.ctx.marker("imp_vent", at[0], f + 3, at[1]);
			}
		}
		int[] head = ventHead();
		this.ctx.marker("imp_vent", head[0], 9, head[1]);
		this.ctx.marker("golem", 25, 7, 36);
		this.ctx.marker("golem", 37, 7, 36);
		this.ctx.marker("guardian", CORE_X, 7, GUARDIAN_Z);
		this.ctx.marker("forge", CORE_X, CORE_Y, FORGE_Z);
		for (int x = 30; x <= 32; x++) {
			for (int y = 7; y <= 9; y++) {
				this.ctx.marker("vault_gate", x, y, 18);
			}
		}
		int[] entry = this.plan.gate().cell(4, 0);
		this.ctx.marker("entry", entry[0], Geo.Level.A.floor + 1, entry[1]);
	}

	private int[] ventHead() {
		int w = this.plan.lavaWidth();
		return switch (this.plan.route()) {
			case 0, 3 -> new int[] {20, 23};
			case 1 -> new int[] {23, 43 - w + 1};
			default -> new int[] {43 - w + 1, 28};
		};
	}

	// ------------------------------------------------------------------ lava

	private void lavaRects() {
		int w = this.plan.lavaWidth();
		switch (this.plan.route()) {
			case 0 -> this.lava.add(new int[] {19, 23, 19 + w - 1, 41});
			case 1 -> this.lava.add(new int[] {22, 43 - w + 1, 41, 43});
			case 2 -> this.lava.add(new int[] {43 - w + 1, 27, 43, 41});
			default -> {
				this.lava.add(new int[] {19, 23, 19 + w - 1, 41});
				this.lava.add(new int[] {19, 43 - w + 1, 43, 43});
				this.lava.add(new int[] {43 - w + 1, 27, 43, 43});
			}
		}
		for (Plan.ChamberPlan c : this.plan.chambers()) {
			if (c.level() == Geo.Level.C) {
				int[] ext = c.slot().ext;
				for (int x = ext[0]; x <= ext[2]; x++) {
					for (int z = ext[1]; z <= ext[3]; z++) {
						if (rawLava(x, z)) {
							this.deck.add(cellKey(x, z));
						}
					}
				}
			}
		}
	}

	private static long cellKey(final int x, final int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private boolean rawLava(final int x, final int z) {
		for (int[] r : this.lava) {
			if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) {
				return true;
			}
		}
		return false;
	}

	private boolean isLava(final int x, final int z) {
		return rawLava(x, z) && !this.deck.contains(cellKey(x, z));
	}

	// ------------------------------------------------------------------ pass 1: walls

	private void walls() {
		int lo = Geo.HALL_MIN;
		int hi = Geo.HALL_MAX;
		int base = Geo.Level.C.floor;
		wallBox(lo, base, lo, lo, Geo.ROOF, hi);
		wallBox(hi, base, lo, hi, Geo.ROOF, hi);
		wallBox(lo, base, lo, hi, Geo.ROOF, lo);
		wallBox(lo, base, hi, hi, Geo.ROOF, hi);
		wallBox(lo, Geo.ROOF, lo, hi, Geo.ROOF, hi);
		wallBox(27, 6, 14, 35, 11, 18);
		for (Plan.ChamberPlan c : this.plan.chambers()) {
			int f = c.floorY();
			int[] box = c.slot().rect(0, -Geo.CHAMBER_HALF_WIDTH, Geo.CHAMBER_DEPTH, Geo.CHAMBER_HALF_WIDTH);
			wallBox(box[0], f, box[1], box[2], f + Geo.CHAMBER_HEIGHT + 1, box[3]);
			tunnelWalls(c.slot(), c.level().floor, c.offset());
		}
		int gf = Geo.Level.A.floor;
		int[] gate = this.plan.gate().rect(0, -5, 9, 5);
		wallBox(gate[0], gf, gate[1], gate[2], gf + Geo.CHAMBER_HEIGHT + 1, gate[3]);
		int[] facade = this.plan.gate().rect(10, -2, 12, 2);
		wallBox(facade[0], gf, facade[1], facade[2], gf + 5, facade[3]);
		tunnelWalls(this.plan.gate(), gf, 0);
		for (Plan.SpurPlan spur : this.plan.spurs()) {
			int l = spur.level().floor;
			wallBox(spur.x0() - 1, l - 1, spur.z0() - 1, spur.x1() + 1, l + Geo.TUNNEL_HEIGHT + 1, spur.z1() + 1);
		}
	}

	/** The block y of the floor of tunnel cell {@code i}, counted from the chamber. Stairs lie next to the chamber. */
	private static int tunnelFloor(final int i, final int level, final int offset) {
		int steps = Math.abs(offset);
		if (offset == 0 || i >= steps) {
			return level;
		}
		return offset > 0 ? level + offset - i : level + offset + 1 + i;
	}

	private static boolean tunnelStair(final int i, final int offset) {
		return offset != 0 && i < Math.abs(offset);
	}

	/** The cells of tunnel slice {@code i}: the three across a straight run, or the nine of a corner. */
	private static int[] sliceBox(final Geo.Slot slot, final List<int[]> cells, final int i, final int reach) {
		int[] c = cells.get(i);
		int[] before = cells.get(Math.max(0, i - 1));
		int[] after = cells.get(Math.min(cells.size() - 1, i + 1));
		boolean alongX = i == 0 ? slot.tunnelStart().getAxis() == Direction.Axis.X : before[1] == c[1] && (after[1] == c[1] || after == c);
		boolean corner = i > 0 && i < cells.size() - 1 && (before[0] != after[0] && before[1] != after[1]);
		if (i == cells.size() - 1) {
			alongX = slot.tunnelEnd().getAxis() == Direction.Axis.X;
		}
		if (corner) {
			return new int[] {c[0] - reach, c[1] - reach, c[0] + reach, c[1] + reach};
		}
		return alongX ? new int[] {c[0], c[1] - reach, c[0], c[1] + reach} : new int[] {c[0] - reach, c[1], c[0] + reach, c[1]};
	}

	private void tunnelWalls(final Geo.Slot slot, final int level, final int offset) {
		List<int[]> cells = slot.tunnelCells();
		for (int i = 0; i < cells.size(); i++) {
			int[] box = sliceBox(slot, cells, i, 2);
			int fy = tunnelFloor(i, level, offset);
			wallBox(box[0], fy - 1, box[1], box[2], fy + Geo.TUNNEL_HEIGHT + 1, box[3]);
		}
	}

	// ------------------------------------------------------------------ pass 2: carve

	private void carve() {
		air(Geo.IN_MIN, 7, Geo.IN_MIN, Geo.IN_MAX, Geo.ROOF - 1, Geo.IN_MAX);
		air(28, 7, 15, 34, 10, 17);
		air(28, 7, 18, 34, 9, 18);
		for (Plan.ChamberPlan c : this.plan.chambers()) {
			int f = c.floorY();
			int[] inner = c.slot().rect(1, -5, 9, 5);
			air(inner[0], f + 1, inner[1], inner[2], f + Geo.CHAMBER_HEIGHT, inner[3]);
			int[] door = c.slot().rect(0, -1, 0, 1);
			air(door[0], f + 1, door[1], door[2], f + 3, door[3]);
			tunnelCarve(c.slot(), c.level().floor, c.offset());
		}
		int gf = Geo.Level.A.floor;
		int[] inner = this.plan.gate().rect(1, -4, 8, 4);
		air(inner[0], gf + 1, inner[1], inner[2], gf + Geo.CHAMBER_HEIGHT, inner[3]);
		int[] door = this.plan.gate().rect(0, -1, 0, 1);
		air(door[0], gf + 1, door[1], door[2], gf + 3, door[3]);
		tunnelCarve(this.plan.gate(), gf, 0);
		for (Plan.SpurPlan spur : this.plan.spurs()) {
			int l = spur.level().floor;
			air(spur.x0(), l + 1, spur.z0(), spur.x1(), l + Geo.TUNNEL_HEIGHT, spur.z1());
		}
	}

	private void tunnelCarve(final Geo.Slot slot, final int level, final int offset) {
		List<int[]> cells = slot.tunnelCells();
		Direction down = offset > 0 ? slot.tunnelStart() : slot.tunnelStart().getOpposite();
		for (int i = 0; i < cells.size(); i++) {
			int[] box = sliceBox(slot, cells, i, 1);
			int fy = tunnelFloor(i, level, offset);
			air(box[0], fy + 1, box[1], box[2], fy + Geo.TUNNEL_HEIGHT, box[3]);
			if (tunnelStair(i, offset)) {
				this.b.fill(box[0], fy, box[1], box[2], fy, box[3], stair(down));
			} else {
				this.b.fill(box[0], fy, box[1], box[2], fy, box[3], floorOf(levelOf(level)));
			}
			if (i % 4 == 2 && !tunnelStair(i, offset)) {
				int[] c = cells.get(i);
				this.b.set(c[0], fy + Geo.TUNNEL_HEIGHT, c[1], lantern());
			}
		}
	}

	private static Geo.Level levelOf(final int floorY) {
		for (Geo.Level level : Geo.Level.values()) {
			if (level.floor == floorY) {
				return level;
			}
		}
		return Geo.Level.A;
	}

	// ------------------------------------------------------------------ floors and decoration

	private void furnish() {
		hallFloor();
		galleries();
		tower();
		railings();
		vaultAlcove();
		for (Plan.ChamberPlan c : this.plan.chambers()) {
			chamber(c);
		}
		gateHall();
		for (Plan.SpurPlan spur : this.plan.spurs()) {
			spur(spur);
		}
		lights();
	}

	private void hallFloor() {
		int c = Geo.Level.C.floor;
		this.b.fill(Geo.IN_MIN, c, Geo.IN_MIN, Geo.IN_MAX, c, Geo.IN_MAX, floorOf(Geo.Level.C));
		BlockState disc = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState ring = Blocks.RED_NETHER_BRICKS.defaultBlockState();
		for (int x = 22; x <= 40; x++) {
			for (int z = 22; z <= 40; z++) {
				int d = (x - CORE_X) * (x - CORE_X) + (z - CORE_X) * (z - CORE_X);
				if (d <= 64) {
					this.b.set(x, c, z, d > 56 ? ring : disc);
				}
			}
		}
		this.b.fill(28, 7, 28, 34, 7, 34, DARK_BRICKS);
		this.b.fill(29, 8, 28, 33, 22, 29, BRICKS);
		this.b.fill(30, 23, 28, 32, 35, 29, BRICKS);
		this.b.fill(30, 10, 29, 32, 12, 29, Blocks.MAGMA_BLOCK.defaultBlockState());
		this.b.set(CORE_X, 15, 28, LAVA);
		for (int[] pillar : new int[][] {{24, 24}, {24, 38}, {38, 38}}) {
			this.b.fill(pillar[0], 7, pillar[1], pillar[0], Geo.ROOF - 1, pillar[1], BRICKS);
		}
		for (int[] pile : this.plan.rubble()) {
			this.b.set(pile[0], 7, pile[1], rubble(pile[0], 7, pile[1]));
		}
		for (int[] r : this.lava) {
			this.b.fill(r[0], c - 1, r[1], r[2], c - 1, r[3], Blocks.BLACKSTONE.defaultBlockState());
		}
		for (int x = Geo.IN_MIN; x <= Geo.IN_MAX; x++) {
			for (int z = Geo.IN_MIN; z <= Geo.IN_MAX; z++) {
				if (isLava(x, z)) {
					this.b.set(x, c, z, LAVA);
				} else if (rawLava(x, z)) {
					this.b.set(x, c, z, DARK_BRICKS);
				}
			}
		}
		for (int x = Geo.IN_MIN; x <= Geo.IN_MAX; x++) {
			for (int z = Geo.IN_MIN; z <= Geo.IN_MAX; z++) {
				if (isLava(x, z) && guardedEdge(x, z)) {
					this.bars.add(x, 7, z);
					this.bars.add(x, 8, z);
				}
			}
		}
		this.b.set(CORE_X, CORE_Y, FORGE_Z, DeepForgeBlocks.FORGE.defaultBlockState().setValue(DeepForgeBlock.FACING, Direction.SOUTH));
	}

	/** A lava cell that borders floor, or a bridge: it needs a rail. */
	private boolean guardedEdge(final int x, final int z) {
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : around) {
			int nx = x + d[0];
			int nz = z + d[1];
			if (Geo.inHall(nx, nz) && !isLava(nx, nz)) {
				return true;
			}
		}
		return false;
	}

	private void galleries() {
		for (Geo.Level level : new Geo.Level[] {Geo.Level.A, Geo.Level.B}) {
			BlockState floor = floorOf(level);
			for (int x = Geo.IN_MIN; x <= Geo.IN_MAX; x++) {
				for (int z = Geo.IN_MIN; z <= Geo.IN_MAX; z++) {
					if (Geo.isGallery(x, z)) {
						this.b.set(x, level.floor, z, floor);
					}
				}
			}
		}
	}

	private void tower() {
		int[][] ring = Geo.ringCells();
		BlockState landing = floorOf(Geo.Level.B);
		for (int i = 0; i < ring.length; i++) {
			int x = ring[i][0];
			int z = ring[i][1];
			int y = Geo.ringY(i);
			if (y - 1 >= 7) {
				this.b.fill(x, 7, z, x, y - 1, z, BRICKS);
			}
			if (Geo.ringIsLanding(i)) {
				this.b.set(x, y, z, landing);
			} else {
				int[] next = ring[(i + 1) % ring.length];
				Direction facing = i == ring.length - 1 ? Direction.WEST : Geo.dirOf(next[0] - x, next[1] - z);
				this.b.set(x, y, z, stair(facing));
			}
		}
		for (int x = Geo.TOWER_X0 + 1; x <= Geo.TOWER_X1 - 1; x++) {
			for (int z = Geo.TOWER_Z0 + 1; z <= Geo.TOWER_Z1 - 1; z++) {
				if (x == Geo.TOWER_X0 + 1 || x == Geo.TOWER_X1 - 1 || z == Geo.TOWER_Z0 + 1 || z == Geo.TOWER_Z1 - 1) {
					this.bars.addColumn(x, 7, 30, z);
				}
			}
		}
		// The ring's outer side is open hall on its south and west edges.
		for (int i = 15; i <= 22; i++) {
			int x = ring[i][0];
			int z = ring[i][1];
			int ox = i <= 18 ? x : x - 1;
			int oz = i <= 18 ? z + 1 : z;
			this.bars.addColumn(ox, Geo.ringY(i) + 1, Geo.ringY(i) + 2, oz);
		}
	}

	private static int ringStand(final int i) {
		return Geo.ringY(i) + 1;
	}

	private static int ringIndexAt(final int x, final int z) {
		int[][] ring = Geo.ringCells();
		for (int i = 0; i < ring.length; i++) {
			if (ring[i][0] == x && ring[i][1] == z) {
				return i;
			}
		}
		return -1;
	}

	private void railings() {
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (Geo.Level level : new Geo.Level[] {Geo.Level.A, Geo.Level.B}) {
			for (int x = Geo.IN_MIN; x <= Geo.IN_MAX; x++) {
				for (int z = Geo.IN_MIN; z <= Geo.IN_MAX; z++) {
					if (!Geo.isGallery(x, z)) {
						continue;
					}
					for (int[] d : around) {
						int nx = x + d[0];
						int nz = z + d[1];
						if (!Geo.inHall(nx, nz) || Geo.isGallery(nx, nz)) {
							continue;
						}
						if (Geo.inTower(nx, nz)) {
							int i = ringIndexAt(nx, nz);
							if (i >= 0 && (Math.abs(ringStand(i) - (level.floor + 1)) <= 1 || Geo.ringY(i) >= level.floor + 1)) {
								continue;
							}
						}
						this.bars.addColumn(nx, level.floor + 1, level.floor + 2, nz);
					}
				}
			}
		}
	}

	private void vaultAlcove() {
		this.b.fill(28, 6, 15, 34, 6, 17, Blocks.GILDED_BLACKSTONE.defaultBlockState());
		for (int x = 28; x <= 34; x++) {
			for (int y = 7; y <= 9; y++) {
				this.bars.add(x, y, 18);
			}
		}
		this.b.set(CORE_X, 10, 16, lantern());
	}

	// ------------------------------------------------------------------ chambers

	private void chamber(final Plan.ChamberPlan c) {
		Geo.Slot slot = c.slot();
		int f = c.floorY();
		BlockState floor = floorOf(c.level());
		int[] inner = slot.rect(1, -5, 9, 5);
		this.b.fill(inner[0], f, inner[1], inner[2], f, inner[3], floor);
		int[] door = slot.rect(0, -1, 0, 1);
		this.b.fill(door[0], f, door[1], door[2], f, door[3], floor);
		int[] mid = slot.cell(5, 0);
		this.b.set(mid[0], f + Geo.CHAMBER_HEIGHT, mid[1], lantern());
		Template t = c.template();
		switch (t) {
			case BARRACKS -> barracks(slot, f);
			case ORE_STORE -> oreStore(slot, f);
			case BELLOWS -> bellows(slot, f);
			case MINE -> mine(slot, f);
			case LAVA -> lavaRoom(slot, f);
			case ARMORY -> armory(slot, f);
		}
		if (c.holder()) {
			int[] at = slot.cell(t.holderA, t.holderB);
			this.b.set(at[0], f, at[1], Blocks.GILDED_BLACKSTONE.defaultBlockState());
		}
		if (c.lever()) {
			lever(slot, f, t);
		}
	}

	private void lever(final Geo.Slot slot, final int f, final Template t) {
		int[] at = slot.cell(t.leverA, t.leverB);
		Direction facing = switch (t.leverWall) {
			case SIDE_POSITIVE -> slot.lateral().getOpposite();
			case SIDE_NEGATIVE -> slot.lateral();
			case BACK -> slot.door;
		};
		BlockState lever = DeepForgeBlocks.FORGE_LEVER.defaultBlockState()
			.setValue(ForgeLeverBlock.FACE, AttachFace.WALL)
			.setValue(ForgeLeverBlock.FACING, facing)
			.setValue(ForgeLeverBlock.POWERED, false)
			.setValue(ForgeLeverBlock.UNLOCKED, false);
		this.b.set(at[0], f + 1, at[1], lever);
	}

	/** Fills a chamber-frame rectangle between two heights above the floor. */
	private void put(final Geo.Slot slot, final int f, final int a0, final int b0, final int a1, final int b1, final int dy0, final int dy1, final BlockState state) {
		int[] r = slot.rect(a0, b0, a1, b1);
		this.b.fill(r[0], f + dy0, r[1], r[2], f + dy1, r[3], state);
	}

	private void putOne(final Geo.Slot slot, final int f, final int a, final int bb, final int dy, final BlockState state) {
		int[] p = slot.cell(a, bb);
		this.b.set(p[0], f + dy, p[1], state);
	}

	private BlockState railAlong(final Geo.Slot slot) {
		RailShape shape = slot.back().getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
		return Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape);
	}

	private void barracks(final Geo.Slot slot, final int f) {
		int[][] beds = {{2, -5}, {2, -3}, {2, 2}, {2, 4}, {6, -5}, {6, -3}, {6, 2}, {6, 4}};
		for (int[] bed : beds) {
			putOne(slot, f, bed[0], bed[1], 1, Blocks.WOOL.red().defaultBlockState());
			putOne(slot, f, bed[0] + 1, bed[1], 1, Blocks.WOOL.white().defaultBlockState());
		}
		put(slot, f, 4, -1, 5, 1, 1, 1, Blocks.SPRUCE_SLAB.defaultBlockState());
	}

	private void oreStore(final Geo.Slot slot, final int f) {
		int[][] stacks = {{2, -5, 3, -2}, {2, 2, 3, 5}, {6, -5, 7, -2}, {6, 3, 7, 5}};
		Block[] ores = {Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.RAW_IRON_BLOCK, Blocks.DEEPSLATE_COPPER_ORE};
		for (int[] s : stacks) {
			for (int a = s[0]; a <= s[2]; a++) {
				for (int bb = s[1]; bb <= s[3]; bb++) {
					int[] p = slot.cell(a, bb);
					int pick = hash(this.seed, p[0], f, p[1]) % ores.length;
					putOne(slot, f, a, bb, 1, ores[pick].defaultBlockState());
					putOne(slot, f, a, bb, 2, ores[(pick + 2) % ores.length].defaultBlockState());
				}
			}
		}
		for (int a = 1; a <= 8; a++) {
			if (a != 5) {
				putOne(slot, f, a, 0, 1, railAlong(slot));
			}
		}
		putOne(slot, f, 5, 0, 1, Blocks.CAULDRON.defaultBlockState());
		putOne(slot, f, 1, -5, 1, Blocks.BARREL.defaultBlockState());
		putOne(slot, f, 1, 5, 1, Blocks.BARREL.defaultBlockState());
	}

	private void bellows(final Geo.Slot slot, final int f) {
		put(slot, f, 3, -5, 6, -3, 1, 2, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
		put(slot, f, 3, 3, 6, 5, 1, 2, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
		put(slot, f, 6, -1, 8, 1, 1, 3, Blocks.BLACKSTONE.defaultBlockState());
		putOne(slot, f, 7, 0, 1, Blocks.MAGMA_BLOCK.defaultBlockState());
		BlockState glow = Blocks.SHROOMLIGHT.defaultBlockState();
		putOne(slot, f, 2, 0, 0, glow);
		putOne(slot, f, 4, -1, 0, glow);
		putOne(slot, f, 4, 1, 0, glow);
	}

	private void mine(final Geo.Slot slot, final int f) {
		int[][] heaps = {{3, 2, 8, 5}, {5, 1, 7, 1}, {4, 5, 5, 5}, {8, -1, 9, -1}};
		for (int[] h : heaps) {
			for (int a = h[0]; a <= h[2]; a++) {
				for (int bb = h[1]; bb <= h[3]; bb++) {
					int[] p = slot.cell(a, bb);
					int height = hash(this.seed, p[0], f + 9, p[1]) % 3 == 0 ? 2 : 1;
					for (int dy = 1; dy <= height; dy++) {
						putOne(slot, f, a, bb, dy, rubble(p[0], f + dy, p[1]));
					}
				}
			}
		}
		for (int a = 1; a <= 5; a++) {
			putOne(slot, f, a, 0, 1, railAlong(slot));
		}
		for (int[] beam : new int[][] {{3, -4}, {3, -1}, {6, -4}}) {
			put(slot, f, beam[0], beam[1], beam[0], beam[1], 1, Geo.CHAMBER_HEIGHT, Blocks.SPRUCE_LOG.defaultBlockState());
		}
		putOne(slot, f, 7, -3, 1, Blocks.CAULDRON.defaultBlockState());
		BlockState[] veins = {Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), Blocks.DEEPSLATE_COAL_ORE.defaultBlockState()};
		for (int a = 1; a <= 4; a++) {
			putOne(slot, f, a, -6, 1 + a % 2, veins[a % veins.length]);
		}
		for (int a = 6; a <= 9; a++) {
			putOne(slot, f, a, 6, 1 + a % 2, veins[(a + 1) % veins.length]);
		}
	}

	private void lavaRoom(final Geo.Slot slot, final int f) {
		int[][] bands = {{5, -5, 6, -2}, {5, 2, 6, 5}};
		for (int[] band : bands) {
			put(slot, f, band[0], band[1], band[2], band[3], 0, 0, LAVA);
			put(slot, f, band[0], band[1], band[2], band[3], -1, -1, Blocks.BLACKSTONE.defaultBlockState());
			int[] r = slot.rect(band[0], band[1], band[2], band[3]);
			for (int x = r[0]; x <= r[2]; x++) {
				for (int z = r[1]; z <= r[3]; z++) {
					this.bars.add(x, f + 1, z);
					this.bars.add(x, f + 2, z);
				}
			}
		}
		put(slot, f, 5, -1, 6, 1, 0, 0, DARK_BRICKS);
		put(slot, f, 2, -5, 3, -4, 1, 1, Blocks.CAULDRON.defaultBlockState());
		put(slot, f, 2, 4, 3, 5, 1, 1, Blocks.CAULDRON.defaultBlockState());
		putOne(slot, f, 2, 5, 1, Blocks.LAVA_CAULDRON.defaultBlockState());
	}

	private void armory(final Geo.Slot slot, final int f) {
		for (int side : new int[] {-5, 5}) {
			put(slot, f, 2, side, 7, side, 1, 1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
			int[] r = slot.rect(2, side, 7, side);
			for (int x = r[0]; x <= r[2]; x++) {
				for (int z = r[1]; z <= r[3]; z++) {
					this.bars.add(x, f + 2, z);
				}
			}
		}
		putOne(slot, f, 6, -3, 1, Blocks.ANVIL.defaultBlockState());
		putOne(slot, f, 6, 3, 1, Blocks.ANVIL.defaultBlockState());
		put(slot, f, 3, -1, 4, 1, 1, 1, DARK_BRICKS);
		putOne(slot, f, 3, 0, 2, Blocks.GRINDSTONE.defaultBlockState());
	}

	// ------------------------------------------------------------------ gate, spurs, lights

	private void gateHall() {
		Geo.Slot gate = this.plan.gate();
		int f = Geo.Level.A.floor;
		BlockState floor = floorOf(Geo.Level.A);
		int[] inner = gate.rect(1, -4, 8, 4);
		this.b.fill(inner[0], f, inner[1], inner[2], f, inner[3], floor);
		int[] door = gate.rect(0, -1, 0, 1);
		this.b.fill(door[0], f, door[1], door[2], f, door[3], floor);
		BlockState gold = Blocks.GILDED_BLACKSTONE.defaultBlockState();
		for (int y = 1; y <= 3; y++) {
			for (int side : new int[] {-2, 2}) {
				putOne(gate, f, 9, side, y, gold);
			}
		}
		for (int side = -2; side <= 2; side++) {
			putOne(gate, f, 9, side, 4, gold);
		}
		for (int side = -1; side <= 1; side++) {
			for (int y = 1; y <= 3; y++) {
				int[] p = gate.cell(9, side);
				this.bars.add(p[0], f + y, p[1]);
			}
		}
		for (int[] brazier : new int[][] {{3, -3}, {3, 3}, {7, -3}, {7, 3}}) {
			putOne(gate, f, brazier[0], brazier[1], 1, Blocks.CAMPFIRE.defaultBlockState());
		}
		int[] mid = gate.cell(4, 0);
		this.b.set(mid[0], f + Geo.CHAMBER_HEIGHT, mid[1], lantern());
		int[] plug = gate.cell(this.plan.plugA(), this.plan.plugB());
		for (int x = plug[0] - 1; x <= plug[0] + 1; x++) {
			for (int z = plug[1] - 1; z <= plug[1] + 1; z++) {
				for (int y = f + Geo.CHAMBER_HEIGHT + 1; y <= Geo.TOP; y++) {
					this.b.set(x, y, z, rubble(x, y, z));
				}
			}
		}
	}

	private int[] spurChest(final Plan.SpurPlan spur) {
		return switch (spur.out()) {
			case EAST -> new int[] {spur.x1(), spur.z0()};
			case WEST -> new int[] {spur.x0(), spur.z0()};
			case SOUTH -> new int[] {spur.x0(), spur.z1()};
			default -> new int[] {spur.x0(), spur.z0()};
		};
	}

	private void spur(final Plan.SpurPlan spur) {
		int l = spur.level().floor;
		this.b.fill(spur.x0(), l, spur.z0(), spur.x1(), l, spur.z1(), floorOf(spur.level()));
		if (spur.closed()) {
			int x0 = spur.x0();
			int x1 = spur.x1();
			int z0 = spur.z0();
			int z1 = spur.z1();
			switch (spur.out()) {
				case EAST -> x0 = x1 - 1;
				case WEST -> x1 = x0 + 1;
				case SOUTH -> z0 = z1 - 1;
				default -> z1 = z0 + 1;
			}
			this.b.box(x0, l + 1, z0, x1, l + Geo.TUNNEL_HEIGHT, z1, this::rubble);
		}
	}

	private void lights() {
		int[][] spots = {{25, 25}, {25, 37}, {37, 37}, {31, 24}, {40, 31}, {22, 31}};
		for (int[] s : spots) {
			this.b.set(s[0], Geo.ROOF - 1, s[1], Blocks.IRON_CHAIN.defaultBlockState());
			this.b.set(s[0], Geo.ROOF - 2, s[1], Blocks.IRON_CHAIN.defaultBlockState());
			this.b.set(s[0], Geo.ROOF - 3, s[1], lantern());
		}
	}
}
