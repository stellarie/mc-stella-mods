package com.stella.tower;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * What a lost castle holds, decided from its seed alone. The plan never reads a block, so every chunk of
 * one castle draws the same plan and the castle comes out whole however it is cut into chunks. Spots are
 * in the piece's own frame; {@link LostCastleHex} paints the plan.
 */
public record LostCastlePlan(List<FloorPlan> floors) {
	private static final long SALT = 0x464C4F52L;
	/** The turrets of the ground storey and of a middle storey, each in a room of its own. */
	public static final int LOWER_TURRETS = 2;
	/** Its own stream, so the clutter never moves a prop, a chest or a turret drawn before it. */
	private static final long CLUTTER_SALT = 0x434C5554L;
	/** The slab stones and woods, and the carpet colours, that clutter draws on. */
	public static final int SLAB_KINDS = 5;
	public static final int CARPET_COLOURS = 4;
	/** A room hangs this many pieces under its ceiling, and more only to reach the clutter minimum. */
	private static final int HANGING_SOFT_CAP = 4;

	/** The ruin a plan lays on a storey. */
	public enum ScatterKind {
		CANDLE,
		SKULL,
		RUBBLE,
		GROWTH,
		COBWEB,
		CHAIN,
		GAP
	}

	/**
	 * One ruin block of a column. {@code arg} is the candle count, the chain length, 0 for a mushroom or 1
	 * for moss, or for a gap 0 for air, 1 for cracked brick and 2 for mossy brick.
	 */
	public record Scatter(ScatterKind kind, int x, int z, int arg) {
	}

	/**
	 * A chest site: a single chest at {@code x}, {@code z}, or a double over two columns. A chest faces
	 * {@code facing} in the frame of a north-facing piece, away from the wall at its back.
	 */
	public record Chest(int x, int z, int x2, int z2, Direction facing) {
		public boolean isDouble() {
			return this.x != this.x2 || this.z != this.z2;
		}
	}

	/**
	 * How a turret shows its spawner. Barred: one block beside the spawner, along x or along z, is iron bars.
	 * Solid: all stone, with one accent block at the pillar's outer corner as the only tell.
	 */
	public enum Cover {
		BARRED_ALONG_X,
		BARRED_ALONG_Z,
		SOLID
	}

	/** One spawner turret: the nook it stands in and how it is covered. */
	public record Turret(TowerHex.Nook nook, Cover cover) {
	}

	/**
	 * One prop of a room, written at {@code x}, {@code z}. A bed covers a second column, {@code x2}, {@code z2}:
	 * the foot is the first and the head the second. For any other prop the two are the same. {@code facing}
	 * is in the frame of a north-facing piece, and {@code arg} is a number a prop draws on: a skull's turn.
	 */
	public record Furnishing(LostRoomTheme.Prop prop, int x, int z, int x2, int z2, Direction facing, int arg) {
	}

	/** One room of a storey, the theme it was given and the props of that theme. */
	public record Room(int cx, int cz, LostRoomTheme theme, List<Furnishing> props) {
	}

	/** The loose things a room is littered with. A high kind hangs under the ceiling; the rest lie on the floor. */
	public enum ClutterKind {
		FLOOR_SLAB(false),
		CEILING_SLAB(true),
		CARPET(false),
		TRAPDOOR(false),
		FLOWER_POT(false),
		BONE_BLOCK(false),
		SKULL(false),
		CANDLE(false),
		/** A lit candle on the full top of a prop, for a room with no free floor left. */
		PROP_CANDLE(false),
		COBWEB(true);

		private final boolean high;

		ClutterKind(final boolean high) {
			this.high = high;
		}

		/** Whether the block hangs in the top block of the storey and not on the floor. */
		public boolean high() {
			return this.high;
		}

		/** The height of the block in a storey whose first free block is {@code feet} and whose ceiling block is {@code ceiling}. */
		public int heightAt(final int feet, final int ceiling) {
			return this.high ? ceiling - 1 : this == PROP_CANDLE ? feet + 1 : feet;
		}
	}

	/** Whether a candle stands steady on a prop: the prop's top is one whole face. */
	public static boolean carriesCandle(final LostRoomTheme.Prop prop) {
		return switch (prop) {
			case CRAFTING_TABLE, SMITHING_TABLE, FLETCHING_TABLE, BARREL, HAY, CRATE, FURNACE, SMOKER, BLAST_FURNACE, BOOKSHELF -> true;
			default -> false;
		};
	}

	/**
	 * One piece of clutter in a column. {@code arg} picks what the kind draws on: the slab stone or wood, the
	 * carpet colour, the trapdoor wood, facing and open state, a skull's turn, or a candle count.
	 */
	public record Clutter(ClutterKind kind, int x, int z, int arg) {
	}

	/** One storey: its kind, its spawner turrets, its chests, its rooms, its ruin and its clutter. */
	public record FloorPlan(int storey, LostFloorKind kind, List<Turret> turrets, List<Chest> chests, List<Room> rooms, List<Scatter> scatter,
		List<Clutter> clutter) {
		public int chestBlocks() {
			int blocks = 0;
			for (Chest chest : this.chests) {
				blocks += chest.isDouble() ? 2 : 1;
			}
			return blocks;
		}
	}

	/** The plan a seed gives. */
	public static LostCastlePlan of(final long ruinSeed, final LostCastleVariant variant) {
		RandomSource rng = RandomSource.create(ruinSeed ^ SALT);
		List<TowerHex.Nook> usable = new ArrayList<>();
		List<TowerHex.Nook> every = TowerHex.nooks();
		for (TowerHex.Nook nook : every) {
			if (nook.usable() && !inStairLane(nook)) {
				usable.add(nook);
			}
		}
		LostFloorKind[] kinds = kinds(rng);
		java.util.Set<Long> hangingBelow = new java.util.HashSet<>();
		List<FloorPlan> floors = new ArrayList<>();
		List<TowerHex.Nook> below = List.of();
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			LostFloorKind kind = kinds[storey];
			boolean top = storey == TowerHex.STOREYS - 1;
			List<TowerHex.Nook> free = new ArrayList<>();
			for (TowerHex.Nook nook : usable) {
				boolean court = storey == 0 && nook.cx() == TowerHex.COLS / 2 && nook.cz() == 0;
				if (!court && !(top && nook.hub())) {
					free.add(nook);
				}
			}
			List<TowerHex.Nook> reserved = new ArrayList<>();
			List<Turret> turrets = new ArrayList<>();
			if (top) {
				// One turret in every room but the portal room.
				for (TowerHex.Cell room : TowerHex.cells()) {
					if (room.cx() == TowerHex.COLS / 2 && room.cz() == TowerHex.COLS / 2) {
						continue;
					}
					List<TowerHex.Nook> inRoom = new ArrayList<>();
					for (TowerHex.Nook nook : free) {
						if (nook.cx() == room.cx() && nook.cz() == room.cz()) {
							inRoom.add(nook);
						}
					}
					TowerHex.Nook nook = turretSite(inRoom, below, rng);
					free.remove(nook);
					reserved.add(nook);
					turrets.add(new Turret(nook, null));
				}
			} else {
				TowerHex.Nook first = turretSite(free, below, rng);
				free.remove(first);
				reserved.add(first);
				turrets.add(new Turret(first, null));
				// The second turret stands in another room, so one room never holds both spawners.
				List<TowerHex.Nook> elsewhere = new ArrayList<>(free);
				elsewhere.removeIf(nook -> nook.cx() == first.cx() && nook.cz() == first.cz());
				TowerHex.Nook second = turretSite(elsewhere, below, rng);
				free.remove(second);
				reserved.add(second);
				turrets.add(new Turret(second, null));
			}
			List<Chest> chests = new ArrayList<>();
			List<TowerHex.Nook> chestNooks = new ArrayList<>();
			for (int i = 0; i < kind.doubles(); i++) {
				TowerHex.Nook nook = pick(free, rng, candidate -> !candidate.hub() && spare(candidate, every, reserved));
				free.remove(nook);
				reserved.add(nook);
				chestNooks.add(nook);
				chests.add(site(nook, true, rng));
			}
			for (int i = 0; i < kind.singles(); i++) {
				TowerHex.Nook nook = pick(free, rng, candidate -> spare(candidate, every, reserved)
					&& chestNooks.stream().noneMatch(used -> used.cx() == candidate.cx() && used.cz() == candidate.cz()));
				free.remove(nook);
				reserved.add(nook);
				chestNooks.add(nook);
				chests.add(site(nook, false, rng));
			}
			java.util.Set<Long> used = new java.util.HashSet<>();
			List<Room> rooms = rooms(every, reserved, top, kind, rng, used);
			List<Scatter> ruin = scatter(every, reserved, used, hangingBelow, top, storey, rng);
			hangingBelow = new java.util.HashSet<>();
			for (Scatter item : ruin) {
				if (item.kind() == ScatterKind.CHAIN) {
					hangingBelow.add(key(item.x(), item.z()));
				}
			}
			for (Room room : rooms) {
				for (Furnishing furnishing : room.props()) {
					if (furnishing.prop() == LostRoomTheme.Prop.CHAIN_RACK) {
						hangingBelow.add(key(furnishing.x(), furnishing.z()));
					}
				}
			}
			List<Turret> covered = new ArrayList<>();
			for (Turret turret : turrets) {
				covered.add(new Turret(turret.nook(), Cover.values()[Math.min(rng.nextInt(4), Cover.SOLID.ordinal())]));
			}
			floors.add(new FloorPlan(storey, kind, List.copyOf(covered), List.copyOf(chests), List.copyOf(rooms), List.copyOf(ruin), List.of()));
			below = covered.stream().map(Turret::nook).toList();
		}
		return new LostCastlePlan(littered(floors, RandomSource.create(ruinSeed ^ SALT ^ CLUTTER_SALT)));
	}

	/** The floors with their clutter laid out, each storey drawn in turn from one stream. */
	private static List<FloorPlan> littered(final List<FloorPlan> floors, final RandomSource rng) {
		List<FloorPlan> done = new ArrayList<>();
		for (FloorPlan floor : floors) {
			Set<Long> gapped = new HashSet<>();
			if (floor.storey() + 1 < floors.size()) {
				for (Scatter item : floors.get(floor.storey() + 1).scatter()) {
					if (item.kind() == ScatterKind.GAP) {
						gapped.add(key(item.x(), item.z()));
					}
				}
			}
			done.add(new FloorPlan(floor.storey(), floor.kind(), floor.turrets(), floor.chests(), floor.rooms(), floor.scatter(),
				List.copyOf(clutter(floor, gapped, rng))));
		}
		return List.copyOf(done);
	}

	/** The free columns of one room's corners: where a piece may lie, where one may hang, and the corner tucks among those. */
	private record Slots(List<int[]> low, List<int[]> high, List<int[]> tucks) {
	}

	/**
	 * Draws the clutter of one storey. Every room takes {@link LostCastleHex#CLUTTER_MIN} to
	 * {@link LostCastleHex#CLUTTER_MAX} pieces, fewer only when its corners run out of columns, and the
	 * storey is given a floor slab and a ceiling slab first. A floor piece takes a column nothing else
	 * stands on. A hanging piece takes any corner column that holds no chain and has stone over it. The
	 * crossing, the stair lane, the portal hall, the yard path and the turret and chest nooks are never used.
	 */
	private static List<Clutter> clutter(final FloorPlan floor, final Set<Long> gapped, final RandomSource rng) {
		int storey = floor.storey();
		boolean top = storey == TowerHex.STOREYS - 1;
		Set<Long> reserved = new HashSet<>();
		for (TowerHex.Nook nook : TowerHex.nooks()) {
			boolean taken = floor.turrets().stream().anyMatch(turret -> turret.nook().equals(nook))
				|| floor.chests().stream().anyMatch(chest -> nook.holds(chest.x(), chest.z()));
			if (taken) {
				for (int[] column : nook.columns()) {
					reserved.add(key(column[0], column[1]));
				}
			}
		}
		for (Turret turret : floor.turrets()) {
			for (int[] column : turretFront(turret)) {
				reserved.add(key(column[0], column[1]));
			}
		}
		Set<Long> standing = new HashSet<>();
		Set<Long> hanging = new HashSet<>();
		for (Room room : floor.rooms()) {
			for (Furnishing prop : room.props()) {
				standing.add(key(prop.x(), prop.z()));
				standing.add(key(prop.x2(), prop.z2()));
				if (prop.prop() == LostRoomTheme.Prop.CHAIN_RACK) {
					hanging.add(key(prop.x(), prop.z()));
				}
			}
		}
		for (Scatter item : floor.scatter()) {
			standing.add(key(item.x(), item.z()));
			if (item.kind() == ScatterKind.CHAIN) {
				hanging.add(key(item.x(), item.z()));
			}
		}
		int[] court = LostCastleVariant.court();
		List<TowerHex.Cell> cells = TowerHex.cells();
		List<TowerHex.Nook> nooks = TowerHex.nooks();
		List<Slots> slots = new ArrayList<>();
		for (TowerHex.Cell cell : cells) {
			Slots room = new Slots(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
			for (TowerHex.Nook nook : nooks) {
				if (nook.cx() != cell.cx() || nook.cz() != cell.cz()) {
					continue;
				}
				for (int[] column : nook.columns()) {
					int x = column[0];
					int z = column[1];
					long at = key(x, z);
					boolean yard = storey == 0 && x >= court[0] && x <= court[2] && z >= court[1] && z <= court[3];
					if (TowerHex.kind(x, z) != TowerHex.Part.WAY || TowerHex.onCrossing(x, z) || LostCastleHex.inStairLane(x, z) || yard
						|| top && LostCastleHex.inPortalHall(x, LostCastleHex.portalSpot().getY(), z) || reserved.contains(at)) {
						continue;
					}
					if (!standing.contains(at)) {
						room.low().add(column);
					}
					if (!hanging.contains(at) && !gapped.contains(at)) {
						room.high().add(column);
						if (x == nook.tuckX() && z == nook.tuckZ()) {
							room.tucks().add(column);
						}
					}
				}
			}
			slots.add(room);
		}
		List<List<Clutter>> byRoom = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++) {
			byRoom.add(new ArrayList<>());
		}
		// The storey's two slabs come first, each in a room that can hold it.
		place(byRoom, slots, ClutterKind.FLOOR_SLAB, rng);
		place(byRoom, slots, ClutterKind.CEILING_SLAB, rng);
		for (int i = 0; i < cells.size(); i++) {
			int wanted = LostCastleHex.CLUTTER_MIN + rng.nextInt(LostCastleHex.CLUTTER_MAX - LostCastleHex.CLUTTER_MIN + 1);
			Slots room = slots.get(i);
			List<Clutter> items = byRoom.get(i);
			if (top) {
				// A candle is the only light up here, so every room is given one or two before anything else.
				for (int candles = 1 + rng.nextInt(2); candles > 0 && !room.low().isEmpty(); candles--) {
					items.add(draw(ClutterKind.CANDLE, room, rng));
				}
				if (items.stream().noneMatch(item -> item.kind() == ClutterKind.CANDLE)) {
					// No floor left: the candle stands on a prop instead.
					for (Room themed : floor.rooms()) {
						if (themed.cx() == cells.get(i).cx() && themed.cz() == cells.get(i).cz()) {
							for (Furnishing prop : themed.props()) {
								if (carriesCandle(prop.prop())) {
									items.add(new Clutter(ClutterKind.PROP_CANDLE, prop.x(), prop.z(), 1 + rng.nextInt(3)));
									break;
								}
							}
						}
					}
				}
			}
			while (items.size() < wanted && (!room.low().isEmpty() || !room.high().isEmpty())) {
				// Hanging pieces fill the gap when the floor runs out, but past the minimum they stay few.
				int hung = (int) items.stream().filter(item -> item.kind().high()).count();
				boolean mayHang = !room.high().isEmpty() && (hung < HANGING_SOFT_CAP || items.size() < LostCastleHex.CLUTTER_MIN);
				boolean high = room.low().isEmpty() ? mayHang : mayHang && rng.nextInt(5) < 2;
				if (room.low().isEmpty() && !mayHang) {
					break;
				}
				items.add(draw(high ? pickHigh(rng) : pickLow(rng), room, rng));
			}
		}
		List<Clutter> all = new ArrayList<>();
		byRoom.forEach(all::addAll);
		return all;
	}

	/** Gives the first room, from a random one, that still has a column for the kind a piece of it. */
	private static void place(final List<List<Clutter>> byRoom, final List<Slots> slots, final ClutterKind kind, final RandomSource rng) {
		int start = rng.nextInt(slots.size());
		for (int i = 0; i < slots.size(); i++) {
			int index = (start + i) % slots.size();
			Slots room = slots.get(index);
			if (!(kind.high() ? room.high() : room.low()).isEmpty()) {
				byRoom.get(index).add(draw(kind, room, rng));
				return;
			}
		}
	}

	private static ClutterKind pickLow(final RandomSource rng) {
		ClutterKind[] bag = {ClutterKind.FLOOR_SLAB, ClutterKind.FLOOR_SLAB, ClutterKind.CARPET, ClutterKind.CARPET, ClutterKind.TRAPDOOR,
			ClutterKind.FLOWER_POT, ClutterKind.BONE_BLOCK, ClutterKind.SKULL, ClutterKind.CANDLE, ClutterKind.CANDLE};
		return bag[rng.nextInt(bag.length)];
	}

	private static ClutterKind pickHigh(final RandomSource rng) {
		return rng.nextInt(3) < 2 ? ClutterKind.CEILING_SLAB : ClutterKind.COBWEB;
	}

	/** One piece of a kind in a free column of the room, which the piece then takes. A web favours the corner tucks. */
	private static Clutter draw(final ClutterKind kind, final Slots room, final RandomSource rng) {
		List<int[]> from = kind.high() ? room.high() : room.low();
		if (kind == ClutterKind.COBWEB && !room.tucks().isEmpty()) {
			from = room.tucks();
		}
		int[] column = from.get(rng.nextInt(from.size()));
		if (kind.high()) {
			room.high().remove(column);
			room.tucks().remove(column);
		} else {
			room.low().remove(column);
		}
		int arg = switch (kind) {
			case FLOOR_SLAB, CEILING_SLAB -> rng.nextInt(SLAB_KINDS);
			case CARPET -> rng.nextInt(CARPET_COLOURS);
			case TRAPDOOR, SKULL -> rng.nextInt(16);
			case CANDLE, PROP_CANDLE -> 1 + rng.nextInt(3);
			default -> 0;
		};
		return new Clutter(kind, column[0], column[1], arg);
	}

	/** Whether a room is left out of the clutter minimum: the yard room on the ground storey and the portal room on the top one. */
	public static boolean clutterExempt(final int storey, final int cx, final int cz) {
		return storey == 0 && cx == TowerHex.COLS / 2 && cz == 0 || storey == TowerHex.STOREYS - 1 && cx == TowerHex.COLS / 2 && cz == TowerHex.COLS / 2;
	}

	/**
	 * The two columns just past a turret's open face, which clutter must leave clear. A solid turret has no
	 * open face, so both of its outer sides count.
	 */
	public static List<int[]> turretFront(final Turret turret) {
		TowerHex.Nook nook = turret.nook();
		int[][] alongX = {{nook.tuckX() + 2 * nook.dx(), nook.tuckZ()}, {nook.tuckX() + 2 * nook.dx(), nook.tuckZ() + nook.dz()}};
		int[][] alongZ = {{nook.tuckX(), nook.tuckZ() + 2 * nook.dz()}, {nook.tuckX() + nook.dx(), nook.tuckZ() + 2 * nook.dz()}};
		List<int[]> front = new ArrayList<>();
		if (turret.cover() != Cover.BARRED_ALONG_Z) {
			front.addAll(List.of(alongX));
		}
		if (turret.cover() != Cover.BARRED_ALONG_X) {
			front.addAll(List.of(alongZ));
		}
		return front;
	}

	/** How many spawner turrets a storey holds: two, and on the top storey one in every room but the portal room. */
	public static int turretsAt(final int storey) {
		return storey == TowerHex.STOREYS - 1 ? TowerHex.cells().size() - 1 : LOWER_TURRETS;
	}

	/**
	 * A turret site among candidate nooks: one whose spawn range the octagon holds, and not the nook of a
	 * turret on the storey below unless it is the only one.
	 */
	private static TowerHex.Nook turretSite(final List<TowerHex.Nook> candidates, final List<TowerHex.Nook> below, final RandomSource rng) {
		List<TowerHex.Nook> held = new ArrayList<>();
		for (TowerHex.Nook nook : candidates) {
			if (reachHeld(nook)) {
				held.add(nook);
			}
		}
		if (held.isEmpty()) {
			held.addAll(candidates);
		}
		// The stair room keeps its two free corners for clutter, unless it is the only room to choose.
		List<TowerHex.Nook> beside = new ArrayList<>(held);
		beside.removeIf(nook -> nook.cx() == TowerHex.stairCell().cx() && nook.cz() == TowerHex.stairCell().cz());
		if (!beside.isEmpty()) {
			held = beside;
		}
		List<TowerHex.Nook> fresh = new ArrayList<>(held);
		fresh.removeAll(below);
		List<TowerHex.Nook> sites = fresh.isEmpty() ? held : fresh;
		return sites.get(rng.nextInt(sites.size()));
	}

	/** The kinds of the storeys, ground to top: no kind twice, and the top storey is always the sanctum. */
	private static LostFloorKind[] kinds(final RandomSource rng) {
		LostFloorKind[] kinds = new LostFloorKind[TowerHex.STOREYS];
		LostFloorKind[] ground = LostFloorKind.of(LostFloorKind.Slot.GROUND);
		kinds[0] = ground[rng.nextInt(ground.length)];
		List<LostFloorKind> pool = new ArrayList<>();
		for (int storey = 1; storey < TowerHex.STOREYS - 1; storey++) {
			if (pool.isEmpty()) {
				for (LostFloorKind kind : LostFloorKind.of(LostFloorKind.Slot.MIDDLE)) {
					if (kind != kinds[storey - 1]) {
						pool.add(kind);
					}
				}
			}
			kinds[storey] = pool.remove(rng.nextInt(pool.size()));
		}
		kinds[TowerHex.STOREYS - 1] = LostFloorKind.SANCTUM;
		return kinds;
	}

	/**
	 * Whether the octagon holds every column a spawner in the nook can raise a mob in, so no mob appears in
	 * the air outside the walls and leaves.
	 */
	public static boolean reachHeld(final TowerHex.Nook nook) {
		int reach = LostCastleSpawner.SPAWN_RANGE;
		for (int x = nook.tuckX() - reach; x <= nook.tuckX() + reach; x++) {
			for (int z = nook.tuckZ() - reach; z <= nook.tuckZ() + reach; z++) {
				if (TowerHex.kind(x, z) == TowerHex.Part.OUT) {
					return false;
				}
			}
		}
		return true;
	}

	private static TowerHex.Nook pick(final List<TowerHex.Nook> free, final RandomSource rng, final Predicate<TowerHex.Nook> prefer) {
		List<TowerHex.Nook> liked = new ArrayList<>();
		for (TowerHex.Nook nook : free) {
			if (prefer.test(nook)) {
				liked.add(nook);
			}
		}
		List<TowerHex.Nook> from = liked.isEmpty() ? free : liked;
		return from.get(rng.nextInt(from.size()));
	}

	/** A chest site in a nook: against one of its walls, facing into the room, with the nook's other columns left clear. */
	private static Chest site(final TowerHex.Nook nook, final boolean dbl, final RandomSource rng) {
		boolean alongX = rng.nextBoolean();
		int x = nook.tuckX();
		int z = nook.tuckZ();
		int stepX = alongX ? nook.dx() : 0;
		int stepZ = alongX ? 0 : nook.dz();
		Direction facing = alongX ? label(0, nook.dz()) : label(nook.dx(), 0);
		if (dbl) {
			return new Chest(x, z, x + stepX, z + stepZ, facing);
		}
		return new Chest(x + stepX, z + stepZ, x + stepX, z + stepZ, facing);
	}

	/** The direction a step names in the frame of a north-facing piece, where +z is north. */
	static Direction label(final int dx, final int dz) {
		if (dz != 0) {
			return dz > 0 ? Direction.NORTH : Direction.SOUTH;
		}
		return dx > 0 ? Direction.EAST : Direction.WEST;
	}

	/** Whether a nook's room keeps another free corner after this one is taken, so its theme still has floor. */
	private static boolean spare(final TowerHex.Nook taken, final List<TowerHex.Nook> every, final List<TowerHex.Nook> reserved) {
		int left = 0;
		for (TowerHex.Nook nook : every) {
			if (nook.cx() == taken.cx() && nook.cz() == taken.cz() && !nook.equals(taken) && !reserved.contains(nook) && !inStairLane(nook)) {
				left += nook.usable() ? 1 : 0;
			}
		}
		return left >= 2;
	}

	/** A column a prop may stand on, and the nook it belongs to. */
	private record Column(int x, int z, TowerHex.Nook nook) {
	}

	private static long key(final int x, final int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/**
	 * Gives every room of a storey one theme, a different one for each, and places the props of the theme in
	 * the free columns of the room's corners, so the crossing and every doorway stay clear. A prop that finds
	 * no column is left out.
	 */
	private static List<Room> rooms(final List<TowerHex.Nook> every, final List<TowerHex.Nook> reserved, final boolean top, final LostFloorKind kind,
		final RandomSource rng, final java.util.Set<Long> used) {
		List<LostRoomTheme> themes = LostRoomTheme.poolOf(kind);
		for (int i = themes.size() - 1; i > 0; i--) {
			LostRoomTheme swap = themes.get(i);
			int j = rng.nextInt(i + 1);
			themes.set(i, themes.get(j));
			themes.set(j, swap);
		}
		// The roomiest rooms take the themes that need the most floor, and the order within a size is drawn.
		// On the top storey the bare barracks goes to a roomier room, so the smallest room keeps a prop a candle can stand on.
		themes.sort((left, right) -> need(right) != need(left) ? Integer.compare(need(right), need(left))
			: Boolean.compare(top && right == LostRoomTheme.BARRACKS, top && left == LostRoomTheme.BARRACKS));
		List<TowerHex.Cell> cells = new ArrayList<>(TowerHex.cells());
		List<List<Column>> free = new ArrayList<>();
		for (TowerHex.Cell cell : cells) {
			List<Column> columns = new ArrayList<>();
			for (TowerHex.Nook nook : every) {
				if (nook.cx() != cell.cx() || nook.cz() != cell.cz() || reserved.contains(nook)) {
					continue;
				}
				for (int[] column : nook.columns()) {
					if (TowerHex.kind(column[0], column[1]) == TowerHex.Part.WAY && !LostCastleHex.inStairLane(column[0], column[1])
						&& !(top && LostCastleHex.inPortalHall(column[0], LostCastleHex.portalSpot().getY(), column[1]))) {
						columns.add(new Column(column[0], column[1], nook));
					}
				}
			}
			for (int i = columns.size() - 1; i > 0; i--) {
				Column swap = columns.get(i);
				int j = rng.nextInt(i + 1);
				columns.set(i, columns.get(j));
				columns.set(j, swap);
			}
			free.add(columns);
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++) {
			order.add(i);
		}
		for (int i = order.size() - 1; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			int swap = order.get(i);
			order.set(i, order.get(j));
			order.set(j, swap);
		}
		order.sort((left, right) -> Integer.compare(free.get(right).size(), free.get(left).size()));
		LostRoomTheme[] themeOf = new LostRoomTheme[cells.size()];
		for (int i = 0; i < order.size(); i++) {
			themeOf[order.get(i)] = themes.get(i % themes.size());
		}
		List<Room> rooms = new ArrayList<>();
		for (int index = 0; index < cells.size(); index++) {
			TowerHex.Cell cell = cells.get(index);
			LostRoomTheme theme = themeOf[index];
			List<Column> columns = free.get(index);
			List<Furnishing> props = new ArrayList<>();
			for (LostRoomTheme.Prop prop : theme.recipe()) {
				if (prop == LostRoomTheme.Prop.BED) {
					Furnishing bed = bed(columns, used, rng);
					if (bed != null) {
						props.add(bed);
					}
					continue;
				}
				for (Column column : columns) {
					if (used.contains(key(column.x(), column.z()))) {
						continue;
					}
					used.add(key(column.x(), column.z()));
					TowerHex.Nook nook = column.nook();
					Direction facing = rng.nextBoolean() ? label(nook.dx(), 0) : label(0, nook.dz());
					props.add(new Furnishing(prop, column.x(), column.z(), column.x(), column.z(), facing, rng.nextInt(16)));
					break;
				}
			}
			rooms.add(new Room(cell.cx(), cell.cz(), theme, List.copyOf(props)));
		}
		return rooms;
	}

	/** The floor columns a theme needs: a bed takes two. */
	private static int need(final LostRoomTheme theme) {
		int need = 0;
		for (LostRoomTheme.Prop prop : theme.recipe()) {
			need += prop.blocks();
		}
		return need;
	}

	/** A bed over two free columns that touch: the foot first, the head second. */
	private static Furnishing bed(final List<Column> columns, final java.util.Set<Long> used, final RandomSource rng) {
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (Column first : columns) {
			if (used.contains(key(first.x(), first.z()))) {
				continue;
			}
			int offset = rng.nextInt(steps.length);
			for (int i = 0; i < steps.length; i++) {
				int[] step = steps[(i + offset) % steps.length];
				for (Column second : columns) {
					if (second.x() == first.x() + step[0] && second.z() == first.z() + step[1] && !used.contains(key(second.x(), second.z()))) {
						used.add(key(first.x(), first.z()));
						used.add(key(second.x(), second.z()));
						return new Furnishing(LostRoomTheme.Prop.BED, first.x(), first.z(), second.x(), second.z(), label(step[0], step[1]), 0);
					}
				}
			}
		}
		return null;
	}

	/** The columns of the corners the ruin may stand in, in a fixed order, then the ruin dealt out over them. */
	private static List<Scatter> scatter(final List<TowerHex.Nook> every, final List<TowerHex.Nook> reserved, final java.util.Set<Long> used,
		final java.util.Set<Long> hangingBelow, final boolean top, final int storey, final RandomSource rng) {
		List<int[]> spots = new ArrayList<>();
		for (TowerHex.Nook nook : every) {
			if (reserved.contains(nook)) {
				continue;
			}
			for (int[] column : nook.columns()) {
				if (TowerHex.kind(column[0], column[1]) == TowerHex.Part.WAY && !LostCastleHex.inStairLane(column[0], column[1])
					&& !(top && LostCastleHex.inPortalHall(column[0], LostCastleHex.portalSpot().getY(), column[1]))
					&& !used.contains(key(column[0], column[1]))) {
					spots.add(column);
				}
			}
		}
		for (int i = spots.size() - 1; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			int[] swap = spots.get(i);
			spots.set(i, spots.get(j));
			spots.set(j, swap);
		}
		List<Scatter> scatter = new ArrayList<>();
		int next = 0;
		ScatterKind[] order = {ScatterKind.CANDLE, ScatterKind.SKULL, ScatterKind.RUBBLE, ScatterKind.GROWTH, ScatterKind.COBWEB, ScatterKind.GAP, ScatterKind.CHAIN};
		int[] counts = {LostCastleHex.CANDLES, LostCastleHex.SKULLS, LostCastleHex.RUBBLE, LostCastleHex.GROWTH, LostCastleHex.COBWEBS, LostCastleHex.FLOOR_GAPS, LostCastleHex.CHAINS};
		for (int kind = 0; kind < order.length; kind++) {
			for (int i = 0; i < counts[kind] && next < spots.size(); i++) {
				int[] at = spots.get(next++);
				// A floor gap under a chain or a rack of the storey above would leave it hanging from nothing.
				while (order[kind] == ScatterKind.GAP && hangingBelow.contains(key(at[0], at[1])) && next < spots.size()) {
					at = spots.get(next++);
				}
				int arg = switch (order[kind]) {
					case CANDLE -> 1 + rng.nextInt(4);
					case GROWTH -> rng.nextInt(2);
					case CHAIN -> 1 + rng.nextInt(2);
					case GAP -> storey > 0 && rng.nextInt(2) == 0 ? 0 : 1 + rng.nextInt(2);
					default -> 0;
				};
				scatter.add(new Scatter(order[kind], at[0], at[1], arg));
			}
		}
		return scatter;
	}

	private static boolean inStairLane(final TowerHex.Nook nook) {
		for (int[] column : nook.columns()) {
			if (LostCastleHex.inStairLane(column[0], column[1])) {
				return true;
			}
		}
		return false;
	}
}
