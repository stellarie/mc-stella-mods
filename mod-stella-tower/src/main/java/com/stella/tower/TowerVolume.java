package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Every block of a floor's room as its plan lays it, by offsets from the room's inner corner, from the bedrock under
 * the ground to the roof: what the builder sets, and the room a plan's walk is proved over. Drawn from the plan, the
 * same plan always gives the same volume. The room's outer wall, a ring of tower wall round it, is the builder's own.
 * <p>
 * The parts are laid in order, each over the last: the ground and the roof; each story's slab, walls and lid; the
 * stairs and ramps, which cut their headroom out of whatever is above them; the boss room's gates; the portal's plinth
 * and frame; the rails where an upper story's edge drops two or more; the stations; the lamps; the decor; the chests.
 */
public final class TowerVolume implements TowerWalk.Space {
	/** The lowest and the highest block of a plan drawn before the biome floors; a biome plan holds its own ({@link TowerPlan#low}). */
	public static final int LOW = -2;
	public static final int HIGH = TowerLayout.HEIGHT;

	public static final byte AIR = 0;
	public static final byte BEDROCK = 1;
	public static final byte GROUND = 2;
	public static final byte SAFE = 3;
	public static final byte RUNNER = 4;
	/** A story's wall, in the theme's wall skin. */
	public static final byte WALL = 5;
	public static final byte PILLAR = 6;
	/** An upper story's floor, in the theme's floor skin. */
	public static final byte SLAB = 7;
	/** Tower wall that nothing breaks: a lid, the boss room's ring, a gate's lintel wall. */
	public static final byte LID = 8;
	public static final byte ROOF = 9;
	public static final byte LAMP = 10;
	public static final byte STAIR_NORTH = 11;
	public static final byte STAIR_EAST = 12;
	public static final byte STAIR_SOUTH = 13;
	public static final byte STAIR_WEST = 14;
	/** A ramp's level step, in the theme's stair stone. */
	public static final byte STEP = 15;
	/** What fills under a stair or a ramp, and the low wall along its open side. */
	public static final byte SUPPORT = 16;
	public static final byte RAIL = 17;
	public static final byte LANTERN = 18;
	public static final byte CHAIN = 19;
	public static final byte CLUSTER = 20;
	public static final byte PLINTH = 21;
	public static final byte FRAME = 22;
	public static final byte STATION = 23;
	public static final byte CHEST = 24;
	/** A pew or a low wall, one block high on a story's floor, in the theme's stair stone. */
	public static final byte COVER = 25;
	/** The lit portal in a floor's frame; a body walks through it as through air. */
	public static final byte PORTAL_FILL = 26;
	/** A biome floor's block: the palette entry is in {@link #code}. */
	public static final byte BIOME_BLOCK = 27;
	/** A step of the portal's stair, in the biome's stone, climbing east or west. */
	public static final byte DAIS_STAIR_EAST = 28;
	public static final byte DAIS_STAIR_WEST = 29;

	/** How high above a story a ceiling may be and still take a lamp; a higher one has a lantern hung from it. */
	public static final int CEILING_REACH = 9;
	/** The lamp grid's pitch, and the farthest a walked spot of a story may be from the nearest of its lamps. */
	public static final int LAMP_PITCH = 7;
	public static final int LAMP_REACH = 6;
	/** In a thousand: how many of a crystal cavern's high rock ceilings grow a cluster. */
	private static final int CLUSTER_SHARE = 70;

	private final int low;
	private final int high;
	private final int span;
	/** The room's width along x and depth along z. */
	private final int width;
	private final int depth;
	private final byte[] kinds;
	private final boolean[] reserved;
	/** The palette entry of each {@link #BIOME_BLOCK}; null on a plan with none. */
	private final byte[] codes;
	/** The lamps and lanterns, by story: offsets (x, y, z). */
	private final List<List<int[]>> lamps = new ArrayList<>();

	private TowerVolume(final int low, final int high, final boolean palette, final int width, final int depth) {
		this.low = low;
		this.high = high;
		this.span = high - low + 1;
		this.width = width;
		this.depth = depth;
		this.kinds = new byte[width * this.span * depth];
		this.reserved = new boolean[width * this.span * depth];
		this.codes = palette ? new byte[width * this.span * depth] : null;
	}

	/** The room's width along x, in blocks. */
	public int width() {
		return this.width;
	}

	/** The room's depth along z, in blocks. */
	public int depth() {
		return this.depth;
	}


	private int index(final int x, final int y, final int z) {
		return (x * this.span + (y - this.low)) * this.depth + z;
	}

	private boolean inside(final int x, final int y, final int z) {
		return x >= 0 && z >= 0 && x < this.width && z < this.depth && y >= this.low && y <= this.high;
	}

	/** The lowest block of the room: its bedrock. */
	public int low() {
		return this.low;
	}

	/** The highest block of the room: its roof. */
	public int high() {
		return this.high;
	}

	/** The palette entry of a {@link #BIOME_BLOCK} cell; 0 for any other. */
	public byte code(final int x, final int y, final int z) {
		return this.codes != null && inside(x, y, z) ? this.codes[index(x, y, z)] : 0;
	}

	private void block(final int x, final int y, final int z, final byte code) {
		if (inside(x, y, z)) {
			this.kinds[index(x, y, z)] = BIOME_BLOCK;
			this.codes[index(x, y, z)] = code;
		}
	}

	/** The part laid at a cell; outside the room, tower wall. */
	public byte kind(final int x, final int y, final int z) {
		return inside(x, y, z) ? this.kinds[index(x, y, z)] : LID;
	}

	private void set(final int x, final int y, final int z, final byte kind) {
		if (inside(x, y, z)) {
			this.kinds[index(x, y, z)] = kind;
		}
	}

	private boolean reservedAt(final int x, final int y, final int z) {
		return inside(x, y, z) && this.reserved[index(x, y, z)];
	}

	private void reserve(final int x, final int y, final int z) {
		if (inside(x, y, z)) {
			this.reserved[index(x, y, z)] = true;
		}
	}

	@Override
	public byte at(final int x, final int y, final int z) {
		byte kind = kind(x, y, z);
		return kind == BIOME_BLOCK ? BiomeBlocks.walk(code(x, y, z)) : walkClass(kind);
	}

	/** How a part counts to a walking body, as its block's collision shape counts in the world. */
	public static byte walkClass(final byte kind) {
		return switch (kind) {
			case AIR, PORTAL_FILL -> TowerWalk.OPEN;
			case RAIL -> TowerWalk.TALL;
			default -> TowerWalk.SOLID;
		};
	}

	/** The stair kind that climbs toward a direction. */
	public static byte stair(final Direction direction) {
		return switch (direction) {
			case NORTH -> STAIR_NORTH;
			case EAST -> STAIR_EAST;
			case SOUTH -> STAIR_SOUTH;
			default -> STAIR_WEST;
		};
	}

	public static boolean isStair(final byte kind) {
		return kind >= STAIR_NORTH && kind <= STAIR_WEST;
	}

	/** The lamps and lanterns lighting a story, by offsets. */
	public List<int[]> lampsOf(final int story) {
		return story < this.lamps.size() ? this.lamps.get(story) : List.of();
	}

	/** Whether a body stands at the spot. */
	public boolean stands(final int x, final int y, final int z) {
		return TowerWalk.inRoom(x, y, z) && TowerWalk.stands(this, x, y, z);
	}

	/** Whether a body stands at the spot on a floor of its own: ground, a safe zone's floor, a runner or a slab. */
	public boolean standsFlat(final int x, final int y, final int z) {
		if (!stands(x, y, z)) {
			return false;
		}
		byte below = kind(x, y - 1, z);
		return below == GROUND || below == SAFE || below == RUNNER || below == SLAB || below == BIOME_BLOCK && BiomeBlocks.ground(code(x, y - 1, z));
	}

	/** The volume of a plan: every part, its chests included. */
	public static TowerVolume of(final TowerPlan plan) {
		TowerVolume volume = new TowerVolume(plan.low(), plan.roofY(), plan.isBiome(), plan.width(), plan.depth());
		if (plan.isBiome()) {
			volume.biome(plan);
			volume.portal(plan);
			volume.chests(plan);
			return volume;
		}
		volume.base(plan);
		volume.stories(plan);
		for (TowerFlight flight : plan.flights()) {
			volume.flight(flight);
		}
		volume.gates(plan);
		volume.portal(plan);
		volume.terrain(plan);
		volume.rails(plan);
		volume.stations(plan);
		volume.lamps(plan);
		volume.decor(plan);
		volume.chests(plan);
		return volume;
	}

	/** Whether the plan's arrival reaches its portal over this volume, on foot. */
	public boolean reaches(final TowerPlan plan) {
		return TowerWalk.reaches(this, new int[] {plan.arrivalX(), 0, plan.arrivalZ()}, portalGoals(plan));
	}

	/** Where a walk to the portal ends: standing in the bottom of the portal's inside, either block of it. */
	public static List<int[]> portalGoals(final TowerPlan plan) {
		BlockPos origin = plan.origin();
		List<int[]> goals = new ArrayList<>();
		for (BlockPos inside : plan.portalInside()) {
			if (inside.getY() == TowerLayout.BASE_Y + plan.portalY() + plan.lift()) {
				goals.add(new int[] {inside.getX() - origin.getX(), inside.getY() - TowerLayout.BASE_Y, inside.getZ() - origin.getZ()});
			}
		}
		return goals;
	}

	private void base(final TowerPlan plan) {
		TowerPlan.Room runner = plan.runner();
		for (int x = 0; x < this.width; x++) {
			for (int z = 0; z < this.depth; z++) {
				set(x, this.low, z, BEDROCK);
				boolean onRunner = runner != null && x >= runner.x() && x < runner.x() + runner.width() && z >= runner.z() && z < runner.z() + runner.length();
				set(x, -1, z, plan.inSafeZone(x, 0, z) ? SAFE : onRunner ? RUNNER : GROUND);
				set(x, this.high, z, ROOF);
			}
		}
	}

	private void stories(final TowerPlan plan) {
		List<TowerStory> stories = plan.stories();
		for (int s = 0; s < stories.size(); s++) {
			TowerStory story = stories.get(s);
			TowerStory below = s > 0 ? stories.get(s - 1) : null;
			for (int x = 0; x < this.width; x++) {
				for (int z = 0; z < this.depth; z++) {
					boolean wall = story.wall()[x][z];
					boolean pillar = story.isPillar(x, z);
					if (below != null && (story.floor()[x][z] || wall)) {
						byte slab = pillar ? PILLAR : wall && below.wall()[x][z] ? WALL : SLAB;
						set(x, story.y() - 1, z, slab);
					}
					byte cover = story.coverAt(x, z);
					if (!wall && cover != 0) {
						set(x, story.y(), z, cover == TowerStory.COVER_LAMP ? LAMP : COVER);
					}
					if (wall) {
						int height = pillar ? story.top() - story.y() : Math.min(story.top() - story.y(), story.wallTop());
						if (s == 0) {
							height = Math.min(height, plan.wallHeightAt(x, z));
						}
						byte kind = pillar ? PILLAR : plan.isRing(x, z) ? LID : WALL;
						for (int y = story.y(); y < story.y() + height; y++) {
							set(x, y, z, kind);
						}
					}
				}
			}
			if (s == stories.size() - 1 && story.top() < this.high) {
				for (int x = 0; x < this.width; x++) {
					for (int z = 0; z < this.depth; z++) {
						if (!inHall(plan, x, z, 0)) {
							set(x, story.top(), z, LID);
						}
					}
				}
			}
		}
		if (plan.hall() > 0) {
			hallShaft(plan);
		}
	}

	/** Whether a spot lies in the floor's hall, grown by {@code grow} blocks on every side. */
	private static boolean inHall(final TowerPlan plan, final int x, final int z, final int grow) {
		int low = plan.hallLow() - grow;
		int high = plan.hallLow() + plan.hall() - 1 + grow;
		return plan.hall() > 0 && x >= low && x <= high && z >= low && z <= high;
	}

	/** The ledge round a hall's shaft, and how high it hangs: well above the boss's reach, under the lamps. */
	public static final int LEDGE_Y = 12;
	public static final int LEDGE_WIDTH = 2;

	/**
	 * An elite's or a boss's hall rises to the roof: the lid stops at its edge, and a wall of tower wall closes the
	 * shaft from the lid up. A spectators' ledge two wide runs round the shaft, railed, with lamps along its wall.
	 */
	private void hallShaft(final TowerPlan plan) {
		int lid = plan.stories().get(0).top();
		for (int x = 0; x < this.width; x++) {
			for (int z = 0; z < this.depth; z++) {
				if (inHall(plan, x, z, 1) && !inHall(plan, x, z, 0)) {
					for (int y = lid; y < this.high; y++) {
						set(x, y, z, (y - LEDGE_Y - 2) % 4 == 0 && y > LEDGE_Y && (x + z) % 4 == 0 ? LAMP : LID);
					}
				}
				if (inHall(plan, x, z, 0) && !inHall(plan, x, z, -LEDGE_WIDTH)) {
					set(x, LEDGE_Y, z, SLAB);
					if (inHall(plan, x, z, -1)) {
						set(x, LEDGE_Y + 1, z, RAIL);
					}
				}
			}
		}
	}

	private void flight(final TowerFlight flight) {
		int top = flight.top();
		for (int i = 0; i < flight.length(); i++) {
			int h = flight.surface(i);
			for (int w = 0; w < flight.width(); w++) {
				int x = flight.cellX(i, w);
				int z = flight.cellZ(i, w);
				for (int y = flight.base(); y <= h - 2; y++) {
					set(x, y, z, SUPPORT);
					reserve(x, y, z);
				}
				set(x, h - 1, z, flight.stairAt(i) ? stair(flight.direction()) : STEP);
				reserve(x, h - 1, z);
				for (int y = h; y <= h + 2; y++) {
					set(x, y, z, AIR);
					reserve(x, y, z);
				}
			}
			if (h >= top) {
				continue;
			}
			// A low wall along each open side, below the upper story's slab, so no body steps off.
			for (int side : new int[] {-1, flight.width()}) {
				int x = flight.cellX(i, side);
				int z = flight.cellZ(i, side);
				for (int y = flight.base(); y < h; y++) {
					if (kind(x, y, z) == AIR && !reservedAt(x, y, z)) {
						set(x, y, z, SUPPORT);
					}
				}
				if (kind(x, h, z) == AIR && !reservedAt(x, h, z)) {
					set(x, h, z, RAIL);
				}
			}
		}
		for (int w = 0; w < flight.width(); w++) {
			for (int y = 0; y <= 2; y++) {
				reserve(flight.cellX(-1, w), flight.base() + y, flight.cellZ(-1, w));
				reserve(flight.cellX(flight.length(), w), top + y, flight.cellZ(flight.length(), w));
			}
		}
	}

	private void gates(final TowerPlan plan) {
		int top = plan.stories().get(0).top();
		for (TowerPlan.Gate gate : plan.gates()) {
			for (TowerPlan.Spot cell : gate.cells()) {
				for (int y = TowerFloors.GATE_HEIGHT; y < top; y++) {
					set(cell.x(), y, cell.z(), y == TowerFloors.GATE_HEIGHT ? LAMP : LID);
				}
			}
			for (TowerPlan.Spot jamb : gate.jambs()) {
				for (int y = 0; y <= TowerFloors.GATE_HEIGHT; y++) {
					set(jamb.x(), y, jamb.z(), LAMP);
				}
			}
		}
	}

	/**
	 * A biome floor: bedrock, a filler of stone, each column's surface and water, the blocks standing on it, and the
	 * roof, stone of the biome's own kind over an open floor ({@link BiomeRoof}) and the tower's wall over a closed one.
	 * No column is left without a roof, so the sky is never seen.
	 */
	private void biome(final TowerPlan plan) {
		BiomeDraft draft = plan.biome();
		boolean open = draft.biome().openRoof();
		for (int x = 0; x < this.width; x++) {
			for (int z = 0; z < this.depth; z++) {
				int feet = draft.feet()[x][z];
				set(x, this.low, z, BEDROCK);
				for (int y = this.low + 1; y < feet - 1; y++) {
					block(x, y, z, BiomeBlocks.STONE);
				}
				if (plan.inSafeZone(x, z)) {
					set(x, feet - 1, z, SAFE);
				} else {
					block(x, feet - 1, z, draft.surface()[x][z]);
				}
				if (draft.water()[x][z]) {
					block(x, feet, z, BiomeBlocks.WATER);
				}
				if (draft.lava()[x][z]) {
					block(x, feet, z, BiomeBlocks.LAVA);
				}
				if (draft.ceiling() != null) {
					for (int y = Math.max(feet, draft.ceiling()[x][z]); y < this.high; y++) {
						block(x, y, z, draft.ceilCode()[x][z]);
					}
				}
				if (open) {
					block(x, this.high, z, BiomeRoof.at(draft.biome(), plan.seed(), plan.floor(), x, z));
				} else {
					set(x, this.high, z, ROOF);
				}
			}
		}
		int[] placed = draft.placed();
		for (int i = 0; i < placed.length; i += 4) {
			block(placed[i], placed[i + 1], placed[i + 2], (byte) placed[i + 3]);
		}
		for (TowerPlan.Station station : plan.stations()) {
			set(station.x(), 0, station.z(), STATION);
		}
	}

	private void portal(final TowerPlan plan) {
		BlockPos origin = plan.origin();
		for (BlockPos pos : plan.plinth()) {
			set(pos.getX() - origin.getX(), pos.getY() - TowerLayout.BASE_Y, pos.getZ() - origin.getZ(), PLINTH);
		}
		for (BlockPos pos : plan.portalFrame()) {
			set(pos.getX() - origin.getX(), pos.getY() - TowerLayout.BASE_Y, pos.getZ() - origin.getZ(), FRAME);
		}
		if (plan.isBiome()) {
			for (BiomeDraft.Piece piece : plan.biome().decor(plan)) {
				if (piece.kind() == BIOME_BLOCK) {
					block(piece.x(), piece.y(), piece.z(), piece.code());
				} else {
					set(piece.x(), piece.y(), piece.z(), piece.kind());
				}
			}
		}
		for (BlockPos pos : plan.portalInside()) {
			int x = pos.getX() - origin.getX();
			int y = pos.getY() - TowerLayout.BASE_Y;
			int z = pos.getZ() - origin.getZ();
			// A lost room's portal is lit on entry, to the hut; its design stays frozen.
			set(x, y, z, plan.floor() > 0 ? PORTAL_FILL : AIR);
			reserve(x, y, z);
		}
	}

	/** How high a raised spot of a cavern's floor stands, and the headroom every walked spot keeps. */
	public static final int HEADROOM = 3;
	/** The cavern's noise: the size of its cells, the share of floor raised, and stalagmites in a thousand. */
	private static final int NOISE_CELL = 6;
	private static final double RAISE_ABOVE = 0.62;
	private static final int STALAGMITES = 25;

	/**
	 * A cavern's floor and ceiling, varied spot by spot: smooth noise raises some of the floor one block, lowers
	 * the ceiling, always leaving {@link #HEADROOM} above the feet, and stands a few stalagmites in open ground. A stair's
	 * way, the portal's nook and the safe zone are left as drawn.
	 */
	private void terrain(final TowerPlan plan) {
		if (plan.archetype() != TowerArchetype.CRYSTAL_CAVERN) {
			return;
		}
		List<TowerStory> stories = plan.stories();
		for (int s = 0; s < stories.size(); s++) {
			TowerStory story = stories.get(s);
			int y = story.y();
			int top = story.top();
			for (int x = 1; x < this.width - 1; x++) {
				for (int z = 1; z < this.depth - 1; z++) {
					if (!openFloor(x, y, z) || columnReserved(x, y - 1, top, z) || s == plan.portalStory()
						&& Math.max(Math.abs(x - plan.portalX()), Math.abs(z - plan.portalZ())) <= 4) {
						continue;
					}
					boolean zone = plan.inSafeZone(x, y, z);
					int raise = !zone && noise(plan, s * 2, x, z) > RAISE_ABOVE && !nearWay(x, y, z) ? 1 : 0;
					int room = top - y - HEADROOM - raise;
					int drop = room <= 0 ? 0 : (int) Math.floor(noise(plan, s * 2 + 1, x, z) * (room + 1));
					drop = Math.min(drop, room);
					if (raise > 0) {
						set(x, y, z, WALL);
					}
					for (int d = 1; d <= drop; d++) {
						if (kind(x, top - d, z) == AIR) {
							set(x, top - d, z, WALL);
						}
					}
				}
			}
			for (int x = 2; x < this.width - 2; x++) {
				for (int z = 2; z < this.depth - 2; z++) {
					int roll = Math.floorMod(TowerFloors.hash(plan.seed(), plan.look() + 7, x, y, z), 1000);
					if (roll >= STALAGMITES || plan.inSafeZone(x, y, z) || !openRing(x, y, z) || nearWay(x, y, z)) {
						continue;
					}
					int height = 2 + roll % 2;
					for (int h = 0; h < height && kind(x, y + h, z) == AIR; h++) {
						set(x, y + h, z, WALL);
					}
				}
			}
		}
	}

	private boolean openFloor(final int x, final int y, final int z) {
		return kind(x, y, z) == AIR && kind(x, y + 1, z) == AIR && kind(x, y - 1, z) != AIR;
	}

	/** Whether the spot and the eight round it are open floor, so a stalagmite there cuts no way. */
	private boolean openRing(final int x, final int y, final int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!openFloor(x + dx, y, z + dz) || kind(x + dx, y + 2, z + dz) != AIR) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean columnReserved(final int x, final int low, final int high, final int z) {
		for (int y = low; y <= high; y++) {
			if (reservedAt(x, y, z)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a stair's or a ramp's way lies within two blocks across, about this height. */
	private boolean nearWay(final int x, final int y, final int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (columnReserved(x + dx, y - 1, y + 2, z + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Smooth value noise over the room, 0 to 1, its own for each plan and layer. */
	private static double noise(final TowerPlan plan, final int layer, final int x, final int z) {
		int gx = x / NOISE_CELL;
		int gz = z / NOISE_CELL;
		double fx = smooth((x % NOISE_CELL) / (double) NOISE_CELL);
		double fz = smooth((z % NOISE_CELL) / (double) NOISE_CELL);
		double a = corner(plan, layer, gx, gz);
		double b = corner(plan, layer, gx + 1, gz);
		double c = corner(plan, layer, gx, gz + 1);
		double d = corner(plan, layer, gx + 1, gz + 1);
		return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
	}

	private static double smooth(final double t) {
		return t * t * (3 - 2 * t);
	}

	private static double corner(final TowerPlan plan, final int layer, final int gx, final int gz) {
		return Math.floorMod(TowerFloors.hash(plan.seed(), plan.look(), gx, 1000 + layer, gz), 10_000) / 10_000.0;
	}

	/**
	 * Rails where an upper story's edge drops two or more: on a lip of slab laid out over the drop, or, where the
	 * drop is a stair's headroom or a safe zone's, on the edge itself.
	 */
	private void rails(final TowerPlan plan) {
		List<TowerStory> stories = plan.stories();
		for (int s = 1; s < stories.size(); s++) {
			int y = stories.get(s).y();
			for (int x = 0; x < this.width; x++) {
				for (int z = 0; z < this.depth; z++) {
					if (!stands(x, y, z)) {
						continue;
					}
					for (Direction side : Direction.Plane.HORIZONTAL) {
						int nx = x + side.getStepX();
						int nz = z + side.getStepZ();
						if (nx < 0 || nz < 0 || nx >= this.width || nz >= this.depth || stands(nx, y, nz) || stands(nx, y - 1, nz) || stands(nx, y + 1, nz)
							|| at(nx, y, nz) != TowerWalk.OPEN || at(nx, y + 1, nz) != TowerWalk.OPEN) {
							continue;
						}
						boolean lip = kind(nx, y - 1, nz) == AIR && !reservedAt(nx, y - 1, nz) && !reservedAt(nx, y, nz) && !plan.inSafeZone(nx, y - 1, nz);
						if (lip) {
							set(nx, y - 1, nz, SLAB);
							set(nx, y, nz, RAIL);
						} else if (!reservedAt(x, y, z)) {
							set(x, y, z, RAIL);
							break;
						}
					}
				}
			}
		}
	}

	private void stations(final TowerPlan plan) {
		for (TowerPlan.Station station : plan.stations()) {
			set(station.x(), 0, station.z(), STATION);
		}
	}

	/**
	 * Each archetype's lamps. A labyrinth and a hall floor hang theirs on a grid over the maze's cells; a great hall
	 * sets sconces into its pillars at the nave's and the gallery's height, with a lamp in each pew row's end; a cavern
	 * has none but the crystal veins set into its rock. Then any walked spot farther than {@link #LAMP_REACH} from every
	 * lamp of its story gets one: a sconce in the nearest wall, a lamp in a low ceiling, or a lamp in the floor.
	 */
	private void lamps(final TowerPlan plan) {
		List<TowerStory> stories = plan.stories();
		TowerArchetype archetype = plan.archetype();
		boolean grid = archetype == TowerArchetype.LABYRINTH || archetype == TowerArchetype.HALL;
		for (int s = 0; s < stories.size(); s++) {
			TowerStory story = stories.get(s);
			int y = story.y();
			List<int[]> lit = new ArrayList<>();
			this.lamps.add(lit);
			if (grid) {
				gridLamps(lit, y);
			}
			if (archetype == TowerArchetype.GREAT_HALL) {
				for (int x = 0; x < this.width; x++) {
					for (int z = 0; z < this.depth; z++) {
						if (story.isPillar(x, z) && kind(x, y + SCONCE_HEIGHT, z) == PILLAR && facesOpen(x, y + SCONCE_HEIGHT, z)) {
							set(x, y + SCONCE_HEIGHT, z, LAMP);
						}
					}
				}
			}
			for (int x = 0; x < this.width; x++) {
				for (int z = 0; z < this.depth; z++) {
					for (int at = y; at <= story.top(); at++) {
						if (kind(x, at, z) == LAMP) {
							lit.add(new int[] {x, at, z});
						}
					}
				}
			}
		}
		for (int s = 0; s < stories.size(); s++) {
			TowerStory story = stories.get(s);
			List<int[]> lit = this.lamps.get(s);
			for (int y = story.y(); y <= story.top() - HEADROOM; y++) {
				for (int x = 0; x < this.width; x++) {
					for (int z = 0; z < this.depth; z++) {
						if (stands(x, y, z) && !litFrom(lit, x, z)) {
							cover(lit, archetype, x, y, z);
						}
					}
				}
			}
		}
	}

	/** How high above its story a sconce is set. */
	public static final int SCONCE_HEIGHT = 3;

	private void gridLamps(final List<int[]> lit, final int y) {
		int cells = (TowerLayout.SIZE - 1) / LAMP_PITCH;
		for (int gx = 0; gx < cells; gx++) {
			for (int gz = 0; gz < cells; gz++) {
				int cx = 1 + gx * LAMP_PITCH + TowerFloors.LAMP_AT;
				int cz = 1 + gz * LAMP_PITCH + TowerFloors.LAMP_AT;
				int best = Integer.MAX_VALUE;
				int bx = -1;
				int bz = -1;
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						int away = Math.max(Math.abs(dx), Math.abs(dz)) * 8 + Math.abs(dx) + Math.abs(dz);
						if (away < best && stands(cx + dx, y, cz + dz)) {
							best = away;
							bx = cx + dx;
							bz = cz + dz;
						}
					}
				}
				if (bx >= 0) {
					ceilingLamp(lit, bx, y, bz);
				}
			}
		}
	}

	/** One more lamp for a spot left dark, in the archetype's order of choice. */
	private void cover(final List<int[]> lit, final TowerArchetype archetype, final int x, final int y, final int z) {
		boolean done = switch (archetype) {
			case LABYRINTH, HALL -> ceilingLamp(lit, x, y, z) || sconce(lit, x, y, z) || floorLamp(lit, x, y, z);
			case GREAT_HALL -> sconce(lit, x, y, z) || ceilingLamp(lit, x, y, z) || floorLamp(lit, x, y, z);
			default -> sconce(lit, x, y, z) || floorLamp(lit, x, y, z);
		};
		if (!done) {
			ceilingLamp(lit, x, y, z);
		}
	}

	/** Whether a block has open air beside it across x or z, so a lamp set in it shows. */
	private boolean facesOpen(final int x, final int y, final int z) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (kind(x + side.getStepX(), y, z + side.getStepZ()) == AIR) {
				return true;
			}
		}
		return false;
	}

	/** A lamp set into the nearest wall face within reach, at sconce height over the spot. */
	private boolean sconce(final List<int[]> lit, final int x, final int y, final int z) {
		int at = y + SCONCE_HEIGHT - 1;
		int reach = LAMP_REACH - 1;
		int best = Integer.MAX_VALUE;
		int bx = -1;
		int bz = -1;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int away = dx * dx + dz * dz;
				byte here = kind(x + dx, at, z + dz);
				if (away < best && away <= reach * reach && (here == WALL || here == PILLAR) && !reservedAt(x + dx, at, z + dz)
					&& facesOpen(x + dx, at, z + dz) && TowerWalk.inRoom(x + dx, at, z + dz)) {
					best = away;
					bx = x + dx;
					bz = z + dz;
				}
			}
		}
		if (bx < 0) {
			return false;
		}
		set(bx, at, bz, LAMP);
		lit.add(new int[] {bx, at, bz});
		return true;
	}

	/**
	 * A lamp set into the floor near the spot, in place of its ground, its slab, its runner, a pew or a stair's low wall:
	 * the nearest such block within reach, so a safe zone's floor and a stair's steps keep their own.
	 */
	private boolean floorLamp(final List<int[]> lit, final int x, final int y, final int z) {
		int reach = LAMP_REACH - 1;
		int best = Integer.MAX_VALUE;
		int[] pick = null;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int away = dx * dx + dz * dz;
				if (away >= best || away > reach * reach) {
					continue;
				}
				for (int at = y - 1; at <= y; at++) {
					byte here = kind(x + dx, at, z + dz);
					boolean floor = at == y - 1 && (here == GROUND || here == SLAB || here == RUNNER) || here == COVER || here == SUPPORT;
					if (floor && (here == SUPPORT || !reservedAt(x + dx, at, z + dz)) && TowerWalk.inRoom(x + dx, Math.max(at, 0), z + dz)) {
						best = away;
						pick = new int[] {x + dx, at, z + dz};
					}
				}
			}
		}
		if (pick == null) {
			return false;
		}
		set(pick[0], pick[1], pick[2], LAMP);
		lit.add(pick);
		return true;
	}

	/** Whether a lamp of the story hangs within reach of the spot, across the room. */
	public static boolean litFrom(final List<int[]> lamps, final int x, final int z) {
		for (int[] lamp : lamps) {
			int dx = lamp[0] - x;
			int dz = lamp[2] - z;
			if (dx * dx + dz * dz <= LAMP_REACH * LAMP_REACH) {
				return true;
			}
		}
		return false;
	}

	/** The longest chain a lantern hangs on. */
	public static final int CHAIN_LIMIT = 4;

	/** A lamp over a spot: set into a ceiling within reach, or a lantern on a short chain from one a little higher. */
	private boolean ceilingLamp(final List<int[]> lit, final int x, final int y, final int z) {
		int ceiling = y + 2;
		while (ceiling < this.high && kind(x, ceiling, z) == AIR) {
			ceiling++;
		}
		byte above = kind(x, ceiling, z);
		boolean takesLamp = above == SLAB || above == LID || above == ROOF || above == WALL || above == PILLAR || above == LAMP;
		if (ceiling - y <= CEILING_REACH && takesLamp) {
			set(x, ceiling, z, LAMP);
			lit.add(new int[] {x, ceiling, z});
			return true;
		}
		int hang = Math.max(y + HEADROOM, ceiling - 1 - CHAIN_LIMIT);
		if (hang < ceiling && hang - y <= CEILING_REACH && ceiling - 1 - hang <= CHAIN_LIMIT && !reservedAt(x, hang, z)) {
			set(x, hang, z, LANTERN);
			for (int chain = hang + 1; chain < ceiling; chain++) {
				set(x, chain, z, CHAIN);
			}
			lit.add(new int[] {x, hang, z});
			return true;
		}
		return false;
	}

	/** A crystal cavern's amethyst: a few clusters hung from high rock ceilings, well above a body's head. */
	private void decor(final TowerPlan plan) {
		if (!plan.theme().crystals()) {
			return;
		}
		for (TowerStory story : plan.stories()) {
			int y = story.y();
			for (int x = 1; x < this.width - 1; x++) {
				for (int z = 1; z < this.depth - 1; z++) {
					if (!stands(x, y, z) || Math.floorMod(TowerFloors.hash(plan.seed(), plan.look(), x, y, z), 1000) >= CLUSTER_SHARE) {
						continue;
					}
					int ceiling = y + 2;
					while (ceiling < this.high && kind(x, ceiling, z) == AIR) {
						ceiling++;
					}
					if (kind(x, ceiling, z) == WALL && ceiling - y >= HEADROOM + 1 && kind(x, ceiling - 1, z) == AIR && !reservedAt(x, ceiling - 1, z)) {
						set(x, ceiling - 1, z, CLUSTER);
					}
				}
			}
		}
	}

	private void chests(final TowerPlan plan) {
		for (TowerPlan.ChestSpot spot : plan.chestSpots()) {
			for (TowerPlan.Spot cell : spot.cells()) {
				set(cell.x(), spot.y(), cell.z(), CHEST);
			}
		}
	}

	/** Whether the cell is part of a stair or a ramp's way, its headroom, or the spots before and after it. */
	public boolean isWay(final int x, final int y, final int z) {
		return reservedAt(x, y, z);
	}
}
