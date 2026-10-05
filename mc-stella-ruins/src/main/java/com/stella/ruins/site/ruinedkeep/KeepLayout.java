package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Breach;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Cell;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Buttress;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Decor;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Floor;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Hole;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Kind;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Marker;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Rect;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Stair;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Tower;
import com.stella.ruins.world.LayoutContext;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Draws the Ruined Keep from its {@link KeepPlan}. The order is fixed: the solid mass first, then each carve, then
 * decay. A write never depends on a block read outside the layout's own writes, so every chunk draws the same keep.
 * <p>
 * Layers: podium 0-1, floor slabs 2, 10, 18 and 26, roof 40, crown 41. A floor's walking level is the layer above
 * its slab. The gate faces local south.
 */
public final class KeepLayout {
	public static final ResourceKey<LootTable> ROOM_LOOT = ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("chests/ruined_keep_room"));
	public static final String M_SENTINEL = "spawn_sentinel_";
	public static final String M_KNIGHT = "spawn_knight_";
	public static final String M_WARDEN = "spawn_warden_";
	public static final String M_CASTELLAN = "spawn_castellan";
	public static final String M_THRONE_DOOR = "throne_door";
	/** The local spot of the core, the vault and the Castellan. */
	public static final int[] CORE = {24, 27, 32};
	public static final int[] VAULT = {24, 27, 5};
	public static final int[] CASTELLAN = {24, 27, 12};
	/** The throne door: x, y and z of its lowest west block, its width and its height. */
	public static final int DOOR_X = 22;
	public static final int DOOR_Z = 27;
	public static final int DOOR_W = 4;
	public static final int DOOR_H = 5;

	private final LayoutContext ctx;
	private final KeepPlan plan;
	private final long seed;
	private final BlockState wall;
	private final BlockState mossy;
	private final BlockState cracked;
	private final BlockState floor;
	private final BlockState stairs;
	private final BlockState air = Blocks.AIR.defaultBlockState();

	private KeepLayout(final LayoutContext ctx, final KeepPlan plan) {
		this.ctx = ctx;
		this.plan = plan;
		this.seed = ctx.frame().seed();
		if (plan.stone) {
			this.wall = Blocks.STONE_BRICKS.defaultBlockState();
			this.mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			this.cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
			this.floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
			this.stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
		} else {
			this.wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
			this.mossy = Blocks.MOSS_BLOCK.defaultBlockState();
			this.cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
			this.floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
			this.stairs = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState();
		}
	}

	/** The site layout. */
	public static void draw(final LayoutContext ctx) {
		new KeepLayout(ctx, KeepPlan.of(ctx.random())).build();
	}

	private void build() {
		skirtAndShell();
		carveFloors();
		throneShell();
		towers();
		doors();
		stairs();
		holes();
		breaches();
		gate();
		throneContent();
		furniture();
		crown();
		buttresses();
		decay();
		lanterns();
		chestsAndMarkers();
	}

	// ---- helpers ----

	private void box(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final BlockState state) {
		this.ctx.fill(x1, y1, z1, x2, y2, z2, state);
	}

	private void carve(final Rect r, final int y1, final int y2) {
		box(r.x(), y1, r.z(), r.maxX(), y2, r.maxZ(), this.air);
	}

	private void fill(final Rect r, final int y1, final int y2, final BlockState state) {
		box(r.x(), y1, r.z(), r.maxX(), y2, r.maxZ(), state);
	}

	/** A number from 0 to 99, fixed by the seed and the spot. */
	private int roll(final int x, final int y, final int z, final int salt) {
		long h = this.seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L) ^ (salt * 0xD6E8FEB86659FD93L);
		h ^= h >>> 32;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 29;
		return (int) Math.floorMod(h >>> 8, 100L);
	}

	private BlockState rubble(final int x, final int y, final int z) {
		int r = roll(x, y, z, 7);
		if (this.plan.stone) {
			return r < 45 ? Blocks.COBBLESTONE.defaultBlockState() : r < 80 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
		}
		return r < 70 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
	}

	private static BlockState stairAt(final BlockState base, final Direction up) {
		return base.setValue(StairBlock.FACING, up);
	}

	private static int stand(final int floor) {
		return KeepPlan.STAND[floor];
	}

	// ---- the mass ----

	private void skirtAndShell() {
		int sink = -this.ctx.foundation();
		box(KeepPlan.BODY_MIN_X, sink, KeepPlan.BODY_MIN_Z, KeepPlan.BODY_MAX_X, 1, KeepPlan.BODY_MAX_Z, this.wall);
		for (int layer : new int[] {2, 10, 18, 26, KeepPlan.ROOF}) {
			box(KeepPlan.BODY_MIN_X, layer, KeepPlan.BODY_MIN_Z, KeepPlan.BODY_MAX_X, layer, KeepPlan.BODY_MAX_Z, this.wall);
		}
		// The outer wall, two blocks thick, from the podium to the roof.
		int top = KeepPlan.ROOF - 1;
		box(KeepPlan.BODY_MIN_X, 3, KeepPlan.BODY_MIN_Z, KeepPlan.BODY_MAX_X, top, KeepPlan.BODY_MIN_Z + 1, this.wall);
		box(KeepPlan.BODY_MIN_X, 3, KeepPlan.BODY_MAX_Z - 1, KeepPlan.BODY_MAX_X, top, KeepPlan.BODY_MAX_Z, this.wall);
		box(KeepPlan.BODY_MIN_X, 3, KeepPlan.BODY_MIN_Z + 2, KeepPlan.BODY_MIN_X + 1, top, KeepPlan.BODY_MAX_Z - 2, this.wall);
		box(KeepPlan.BODY_MAX_X - 1, 3, KeepPlan.BODY_MIN_Z + 2, KeepPlan.BODY_MAX_X, top, KeepPlan.BODY_MAX_Z - 2, this.wall);
		// The forecourt: a landing and three stair blocks, from the ground to the gate sill.
		box(20, sink, 40, 27, 2, 43, this.wall);
		box(20, sink, 44, 27, 1, 44, this.wall);
		box(20, sink, 45, 27, 0, 45, this.wall);
		box(20, 2, 44, 27, 2, 44, stairAt(this.stairs, Direction.NORTH));
		box(20, 1, 45, 27, 1, 45, stairAt(this.stairs, Direction.NORTH));
		box(20, 0, 46, 27, 0, 46, stairAt(this.stairs, Direction.NORTH));
		box(20, sink, 46, 27, -1, 46, this.wall);
	}

	/** Partitions between rooms, rubble cells, and each room's floor. A wall stands only where two rooms meet. */
	private void carveFloors() {
		for (int f = 0; f < 3; f++) {
			int s = stand(f);
			int top = s + KeepPlan.ROOM_HEIGHT - 1;
			Floor fl = this.plan.floors[f];
			for (KeepPlan.Room room : fl.rooms()) {
				if (f == 0) {
					carve(room.rect(), s, top);
				}
				fill(room.rect(), s - 1, s - 1, this.floor);
			}
			for (int c = 0; c < 3; c++) {
				for (int r = 0; r < 3; r++) {
					Cell cell = new Cell(c, r);
					if (fl.roomAt(cell) == null) {
						fill(cell.rect(), s, top, this.wall);
					}
					if (c < 2 && fl.roomAt(cell) != fl.roomAt(new Cell(c + 1, r))) {
						box(cell.x0() + 10, s, cell.z0(), cell.x0() + 10, top, cell.z0() + 9, this.wall);
					}
					if (r < 2 && fl.roomAt(cell) != fl.roomAt(new Cell(c, r + 1))) {
						box(cell.x0(), s, cell.z0() + 10, cell.x0() + 9, top, cell.z0() + 10, this.wall);
					}
				}
			}
			for (int px : new int[] {18, 29}) {
				for (int pz : new int[] {16, 27}) {
					box(px, s, pz, px, top, pz, this.wall);
				}
			}
		}
	}

	private void throneShell() {
		int s = stand(3);
		Rect throne = new Rect(8, 6, 32, 21);
		Rect ante = new Rect(8, 28, 32, 10);
		fill(throne, s - 1, s - 1, this.floor);
		fill(ante, s - 1, s - 1, this.floor);
		box(8, s, DOOR_Z, 39, KeepPlan.ROOF - 1, DOOR_Z, this.wall);
		for (int zz : this.plan.pillarRows) {
			for (int xx : new int[] {13, 33}) {
				box(xx, s, zz, xx + 1, KeepPlan.ROOF - 1, zz + 1, this.wall);
			}
		}
	}

	private void towers() {
		for (Tower tower : this.plan.towers) {
			int fx = tower.corner().footX;
			int fz = tower.corner().footZ;
			int top = tower.top();
			int sink = -this.ctx.foundation();
			box(fx, sink, fz, fx + 8, top - 2, fz + 8, this.wall);
			// A jagged crown: each edge block of the ring stands to the top or one below, by hash.
			for (int dx = 0; dx < 9; dx++) {
				for (int dz = 0; dz < 9; dz++) {
					boolean ring = dx < 2 || dz < 2 || dx > 6 || dz > 6;
					if (ring) {
						int h = roll(fx + dx, 0, fz + dz, 11);
						int up = h < 35 ? 2 : h < 70 ? 1 : 0;
						for (int y = top - 1; y <= top - 2 + up; y++) {
							this.ctx.set(fx + dx, y, fz + dz, this.wall);
						}
					}
				}
			}
			box(fx + 2, 3, fz + 2, fx + 6, top - 1, fz + 6, this.air);
			// The newel is a ladder on the north wall, up to a loft with the tower chest.
			int ladderX = fx + 4;
			int ladderZ = fz + 2;
			int loft = top - 5;
			for (int y = 3; y <= loft + 1; y++) {
				this.ctx.set(ladderX, y, ladderZ, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
			}
			for (int x = fx + 2; x <= fx + 6; x++) {
				for (int z = fz + 2; z <= fz + 6; z++) {
					if (x != ladderX || z != ladderZ) {
						this.ctx.set(x, loft, z, this.floor);
					}
				}
			}
			// Posterns: 1 wide, 2 high, 2 deep, one on each floor. On the throne floor they are shut with bars.
			int pz = tower.corner().north() ? fz + 6 : fz + 2;
			int px = tower.corner().west() ? fx + 7 : fx;
			for (int f = 0; f < 4; f++) {
				int s = stand(f);
				if (s + 1 >= top - 5) {
					continue;
				}
				for (int dx = 0; dx < 2; dx++) {
					for (int y = s; y <= s + 1; y++) {
						this.ctx.set(px + dx, y, pz, f < 3 ? this.air : Blocks.IRON_BARS.defaultBlockState());
					}
				}
			}
		}
	}

	private void doors() {
		for (int f = 0; f < 3; f++) {
			int s = stand(f);
			for (KeepPlan.Door door : this.plan.floors[f].doors()) {
				carve(door.rect(), s, s + 2);
			}
		}
	}

	/** The steps of a flight, one stair block each, on a solid column. The top step stands in the slab above. */
	private void stairs() {
		for (int f = 0; f < 3; f++) {
			Stair st = this.plan.floors[f].up();
			int s = stand(f);
			Direction up = st.north() ? Direction.NORTH : Direction.SOUTH;
			Rect r = st.rect();
			for (int i = 0; i < 8; i++) {
				int z = st.stepZ(i);
				if (i > 0) {
					box(r.x(), s, z, r.maxX(), s + i - 1, z, this.wall);
				}
				box(r.x(), s + i, z, r.maxX(), s + i, z, stairAt(this.stairs, up));
			}
			// The slab over the flight opens above steps 3 to 6, so the climber has head room.
			for (int i = 3; i <= 6; i++) {
				int z = st.stepZ(i);
				box(r.x(), s + 7, z, r.maxX(), s + 7, z, this.air);
			}
		}
	}

	private void holes() {
		for (Hole hole : this.plan.holes) {
			int slab = stand(hole.floor()) - 1;
			carve(hole.rect(), slab, slab);
			Rect r = hole.rect();
			int below = stand(hole.floor() - 1);
			for (int x = r.x(); x <= r.maxX(); x++) {
				for (int z = r.z(); z <= r.maxZ(); z++) {
					int h = roll(x, 0, z, 13) / 34;
					for (int y = below; y < below + h; y++) {
						this.ctx.set(x, y, z, rubble(x, y, z));
					}
				}
			}
		}
	}

	private void breaches() {
		for (Breach b : this.plan.breaches) {
			Rect r = b.rect();
			box(r.x(), b.sill(), r.z(), r.maxX(), b.sill() + 3, r.maxZ(), this.air);
			// Rubble spilled outside, at the foot of the wall.
			int ox = b.face() == KeepPlan.Face.W ? 5 : b.face() == KeepPlan.Face.E ? 42 : r.x();
			int oz = b.face() == KeepPlan.Face.N ? 3 : b.face() == KeepPlan.Face.S ? 40 : r.z();
			int w = b.face() == KeepPlan.Face.N || b.face() == KeepPlan.Face.S ? 3 : 1;
			int d = w == 3 ? 1 : 3;
			for (int x = ox; x < ox + w; x++) {
				for (int z = oz; z < oz + d; z++) {
					this.ctx.set(x, 0, z, rubble(x, 0, z));
				}
			}
		}
	}

	private void gate() {
		box(22, 3, 38, 25, 7, 39, this.air);
		box(22, 8, 38, 25, 8, 39, this.wall);
		BlockState hung = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		this.ctx.set(22, 7, 38, hung);
		this.ctx.set(25, 7, 38, hung);
	}

	private void throneContent() {
		int s = stand(3);
		box(22, s, 11, 25, s, 26, Blocks.CARPET.red().defaultBlockState());
		box(22, s, 28, 25, s, 30, Blocks.CARPET.red().defaultBlockState());
		// The dais, the throne, and the vault niche behind it.
		box(18, s, 6, 29, s, 10, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		this.ctx.set(23, s, 7, stairAt(Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH));
		this.ctx.set(24, s, 7, stairAt(Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH));
		box(23, s, VAULT[2], 25, s + 2, VAULT[2], this.air);
		this.ctx.placeVault(VAULT[0], VAULT[1], VAULT[2]);
		// The core, with three sockets round it.
		this.ctx.placeCore(CORE[0], CORE[1], CORE[2]);
		for (int[] socket : new int[][] {{20, 32}, {28, 32}, {24, 29}}) {
			this.ctx.set(socket[0], s, socket[1], Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		}
		// The throne door.
		BlockState door = KeepBlocks.THRONE_DOOR.defaultBlockState();
		for (int x = DOOR_X; x < DOOR_X + DOOR_W; x++) {
			for (int y = s; y < s + DOOR_H; y++) {
				this.ctx.set(x, y, DOOR_Z, door);
				this.ctx.marker(M_THRONE_DOOR, x, y, DOOR_Z);
			}
		}
	}

	private void furniture() {
		for (int f = 0; f < 3; f++) {
			int s = stand(f);
			for (Decor d : this.plan.floors[f].decor()) {
				Rect r = d.rect();
				switch (d.kind()) {
					case "pillar" -> fill(r, s, s + KeepPlan.ROOM_HEIGHT - 1, this.wall);
					case "carpet" -> fill(r, s, s, Blocks.CARPET.red().defaultBlockState());
					case "bunk" -> fill(r, s, s, Blocks.OAK_PLANKS.defaultBlockState());
					case "shelf" -> fill(r, s, s + 1, Blocks.BOOKSHELF.defaultBlockState());
					case "table" -> fill(r, s, s, Blocks.SPRUCE_SLAB.defaultBlockState());
					case "altar" -> fill(r, s, s, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
					case "pew" -> fill(r, s, s, Blocks.OAK_SLAB.defaultBlockState());
					case "rack" -> fill(r, s, s + 1, Blocks.DARK_OAK_FENCE.defaultBlockState());
					case "stand" -> fill(r, s, s, Blocks.SMITHING_TABLE.defaultBlockState());
					case "hearth" -> fill(r, s, s, Blocks.BLAST_FURNACE.defaultBlockState());
					case "barrel" -> fill(r, s, s, Blocks.BARREL.defaultBlockState());
					default -> {
					}
				}
			}
		}
	}

	/** The broken parapet, the fallen corner, and the gaps in the roof. */
	private void crown() {
		for (Rect gap : this.plan.roofGaps) {
			carve(gap, KeepPlan.ROOF, KeepPlan.ROOF);
		}
		for (int x = KeepPlan.BODY_MIN_X; x <= KeepPlan.BODY_MAX_X; x++) {
			for (int z = KeepPlan.BODY_MIN_Z; z <= KeepPlan.BODY_MAX_Z; z++) {
				boolean edge = x == KeepPlan.BODY_MIN_X || x == KeepPlan.BODY_MAX_X || z == KeepPlan.BODY_MIN_Z || z == KeepPlan.BODY_MAX_Z;
				if (edge && (x + z) % 2 == 0 && roll(x, KeepPlan.CROWN, z, 17) >= 35) {
					this.ctx.set(x, KeepPlan.CROWN, z, this.wall);
				}
			}
		}
		KeepPlan.Corner c = this.plan.fallenCorner;
		int x1 = c.west() ? KeepPlan.BODY_MIN_X : KeepPlan.BODY_MAX_X - 11;
		int x2 = c.west() ? KeepPlan.BODY_MIN_X + 11 : KeepPlan.BODY_MAX_X;
		int z1 = c.north() ? KeepPlan.BODY_MIN_Z : KeepPlan.BODY_MAX_Z - 11;
		int z2 = c.north() ? KeepPlan.BODY_MIN_Z + 11 : KeepPlan.BODY_MAX_Z;
		int from = KeepPlan.CROWN + 1 - this.plan.fallenDepth;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				if (this.plan.tower(c) && inFoot(c, x, z)) {
					continue;
				}
				int lowered = from - roll(x, 0, z, 19) / 34;
				box(x, lowered, z, x, KeepPlan.CROWN, z, this.air);
			}
		}
		// What fell: rubble on the throne floor under the gap, clear of the stairwell and the core.
		int s = stand(3);
		Rect keep = this.plan.floors[2].up().rect().grow(2);
		for (int x = Math.max(x1, 8); x <= Math.min(x2, 39); x++) {
			for (int z = Math.max(z1, 6); z <= Math.min(z2, 37); z++) {
				boolean clear = keep.contains(x, z) || (x >= 18 && x <= 29 && z >= 26 && z <= 34) || z == DOOR_Z || (x >= 18 && x <= 29 && z <= 12) || nearTower(x, z);
				if (!clear && roll(x, s, z, 23) < 22) {
					this.ctx.set(x, s, z, rubble(x, s, z));
				}
			}
		}
	}

	private boolean inFallen(final int x, final int z) {
		KeepPlan.Corner c = this.plan.fallenCorner;
		int x1 = c.west() ? KeepPlan.BODY_MIN_X : KeepPlan.BODY_MAX_X - 11;
		int z1 = c.north() ? KeepPlan.BODY_MIN_Z : KeepPlan.BODY_MAX_Z - 11;
		return x >= x1 && x <= x1 + 11 && z >= z1 && z <= z1 + 11;
	}

	/** True within one block of any tower's footprint: the posterns must stay as drawn. */
	private boolean nearTower(final int x, final int z) {
		for (Tower t : this.plan.towers) {
			KeepPlan.Corner c = t.corner();
			if (x >= c.footX - 1 && x <= c.footX + 9 && z >= c.footZ - 1 && z <= c.footZ + 9) {
				return true;
			}
		}
		return false;
	}

	private boolean inFoot(final KeepPlan.Corner c, final int x, final int z) {
		return x >= c.footX && x <= c.footX + 8 && z >= c.footZ && z <= c.footZ + 8;
	}

	private void buttresses() {
		int sink = -this.ctx.foundation();
		for (Buttress b : this.plan.buttresses) {
			Rect r = b.rect();
			fill(r, sink, b.top() - 2, this.wall);
			for (int x = r.x(); x <= r.maxX(); x++) {
				for (int z = r.z(); z <= r.maxZ(); z++) {
					int h = roll(x, 0, z, 29);
					int up = h < 50 ? 2 : 1;
					for (int y = b.top() - 1; y <= b.top() - 2 + up; y++) {
						this.ctx.set(x, y, z, this.wall);
					}
				}
			}
		}
	}

	/** True for a column that holds wall: the outer ring, the partitions, a tower or a buttress. */
	private boolean wallColumn(final int x, final int z) {
		if (x >= KeepPlan.BODY_MIN_X && x <= KeepPlan.BODY_MAX_X && z >= KeepPlan.BODY_MIN_Z && z <= KeepPlan.BODY_MAX_Z) {
			boolean ring = x < 8 || x > 39 || z < 6 || z > 37;
			return ring || x == 18 || x == 29 || z == 16 || z == 27;
		}
		for (Tower t : this.plan.towers) {
			if (x >= t.corner().footX && x <= t.corner().footX + 8 && z >= t.corner().footZ && z <= t.corner().footZ + 8) {
				return true;
			}
		}
		for (Buttress b : this.plan.buttresses) {
			if (b.rect().contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** Moss and cracks on the wall blocks, by position. Only a plain wall block changes. */
	private void decay() {
		int highest = this.plan.height();
		for (int x = 0; x < KeepPlan.SIZE; x++) {
			for (int z = 0; z < KeepPlan.SIZE; z++) {
				if (!wallColumn(x, z)) {
					continue;
				}
				for (int y = 0; y <= highest; y++) {
					if (this.ctx.get(x, y, z) != this.wall) {
						continue;
					}
					int r = roll(x, y, z, 31);
					if (r < this.plan.moss) {
						this.ctx.set(x, y, z, this.mossy);
					} else if (r < this.plan.moss + this.plan.crack) {
						this.ctx.set(x, y, z, this.cracked);
					}
				}
			}
		}
	}

	private void lanterns() {
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		for (int f = 0; f < 3; f++) {
			int s = stand(f);
			Floor fl = this.plan.floors[f];
			for (KeepPlan.Room room : fl.rooms()) {
				for (KeepPlan.Cell cell : room.cells()) {
					int x = cell.x0() + 4;
					int z = cell.z0() + 4;
					if (free(f, x, z)) {
						this.ctx.set(x, s + KeepPlan.ROOM_HEIGHT - 1, z, lantern);
					}
				}
			}
		}
		for (int[] spot : new int[][] {{18, 14}, {30, 14}, {18, 22}, {30, 22}, {24, 33}, {14, 31}, {33, 31}}) {
			boolean gap = false;
			for (Rect g : this.plan.roofGaps) {
				gap |= g.contains(spot[0], spot[1]);
			}
			if (!gap && !inFallen(spot[0], spot[1])) {
				this.ctx.set(spot[0], KeepPlan.ROOF - 1, spot[1], lantern);
			}
		}
	}

	/** True when nothing of floor f or the floor above covers the spot. */
	private boolean free(final int f, final int x, final int z) {
		Floor fl = this.plan.floors[f];
		if (fl.up().rect().contains(x, z) || fl.arriveRect().contains(x, z)) {
			return false;
		}
		for (Hole h : this.plan.holes) {
			if ((h.floor() == f + 1 || h.floor() == f) && h.rect().contains(x, z)) {
				return false;
			}
		}
		for (Decor d : fl.decor()) {
			if (d.rect().contains(x, z) && d.solid()) {
				return false;
			}
		}
		return true;
	}

	private void chestsAndMarkers() {
		for (int f = 0; f < 3; f++) {
			int s = stand(f);
			for (Marker m : this.plan.floors[f].markers()) {
				switch (m.kind()) {
					case CHEST -> this.ctx.placeChest(m.x(), s, m.z(), Direction.NORTH, ROOM_LOOT);
					case SENTINEL -> this.ctx.marker(M_SENTINEL + (f + 1), m.x(), s, m.z());
					case KNIGHT -> this.ctx.marker(M_KNIGHT + (f + 1), m.x(), s, m.z());
					case WARDEN -> this.ctx.marker(M_WARDEN + (f + 1), m.x(), s, m.z());
					default -> {
					}
				}
			}
		}
		int s = stand(3);
		for (Marker m : this.plan.throneMarkers) {
			this.ctx.marker((m.kind() == Kind.SENTINEL ? M_SENTINEL : M_KNIGHT) + "4", m.x(), s, m.z());
		}
		this.ctx.marker(M_CASTELLAN, CASTELLAN[0], CASTELLAN[1], CASTELLAN[2]);
		for (Tower tower : this.plan.towers) {
			int fx = tower.corner().footX;
			int fz = tower.corner().footZ;
			this.ctx.placeChest(fx + 6, tower.top() - 4, fz + 6, Direction.NORTH, ROOM_LOOT);
		}
	}
}
