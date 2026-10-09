package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
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

	/** One storey: its kind, its spawner turrets, its chests and its ruin. */
	public record FloorPlan(int storey, LostFloorKind kind, List<Turret> turrets, List<Chest> chests, List<Scatter> scatter) {
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
				TowerHex.Nook nook = turretSite(free, below, rng);
				free.remove(nook);
				reserved.add(nook);
				turrets.add(new Turret(nook, null));
			}
			List<Chest> chests = new ArrayList<>();
			List<TowerHex.Nook> chestNooks = new ArrayList<>();
			for (int i = 0; i < kind.doubles(); i++) {
				TowerHex.Nook nook = pick(free, rng, candidate -> !candidate.hub());
				free.remove(nook);
				reserved.add(nook);
				chestNooks.add(nook);
				chests.add(site(nook, true, rng));
			}
			for (int i = 0; i < kind.singles(); i++) {
				TowerHex.Nook nook = pick(free, rng, candidate -> chestNooks.stream().noneMatch(used -> used.cx() == candidate.cx() && used.cz() == candidate.cz()));
				free.remove(nook);
				reserved.add(nook);
				chestNooks.add(nook);
				chests.add(site(nook, false, rng));
			}
			List<Scatter> ruin = scatter(every, reserved, top, storey, rng);
			List<Turret> covered = new ArrayList<>();
			for (Turret turret : turrets) {
				covered.add(new Turret(turret.nook(), Cover.values()[Math.min(rng.nextInt(4), Cover.SOLID.ordinal())]));
			}
			floors.add(new FloorPlan(storey, kind, List.copyOf(covered), List.copyOf(chests), List.copyOf(ruin)));
			below = covered.stream().map(Turret::nook).toList();
		}
		return new LostCastlePlan(List.copyOf(floors));
	}

	/** How many spawner turrets a storey holds: one, and on the top storey one in every room but the portal room. */
	public static int turretsAt(final int storey) {
		return storey == TowerHex.STOREYS - 1 ? TowerHex.cells().size() - 1 : 1;
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

	/** The columns of the corners the ruin may stand in, in a fixed order, then the ruin dealt out over them. */
	private static List<Scatter> scatter(final List<TowerHex.Nook> every, final List<TowerHex.Nook> reserved, final boolean top,
		final int storey, final RandomSource rng) {
		List<int[]> spots = new ArrayList<>();
		for (TowerHex.Nook nook : every) {
			if (reserved.contains(nook)) {
				continue;
			}
			for (int[] column : nook.columns()) {
				if (TowerHex.kind(column[0], column[1]) == TowerHex.Part.WAY && !LostCastleHex.inStairLane(column[0], column[1])
					&& !(top && LostCastleHex.inPortalHall(column[0], LostCastleHex.portalSpot().getY(), column[1]))) {
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
