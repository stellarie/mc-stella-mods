package com.stella.ruins.site.lostminerscamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The whole plan of one lost miner's camp, as a function of the seed. It reads no world block, so the layout and the
 * tests both read it. Every chunk draws the same camp.
 * <p>
 * The frame is local: x east, z north, y up from the floor of the site box. The mouth band, {@code z} 0 to
 * {@link #MOUTH}, reaches out toward cave air the way the other cave sites do. The tunnel walks north from the mouth
 * with three to five bends, one segment dropping two to four blocks on a rail ramp with a ladder beside it. Rails run
 * from the mouth and stop short of the lectern. One to three collapse points drop rubble into the tunnel.
 * <p>
 * The camp holds the last {@link #CAMP_DEPTH} blocks of the box: a tent, a campfire with seats, supply crates, the
 * tally post that is the core, and the vault in the first supply pile. The lectern stands at the dead end.
 * <p>
 * The camp never faces south: the tunnel leaves by the south face, so the camp faces away from it.
 */
public final class CampPlan {
	public static final int SIZE_X = 40;
	public static final int SIZE_Y = 12;
	public static final int SIZE_Z = 40;
	/** The mouth band, which reaches out toward cave air. */
	public static final int MOUTH = 16;
	/** The depth of the camp at the north end. */
	public static final int CAMP_DEPTH = 12;
	/** The z the tunnel aims for: the south edge of the camp. */
	public static final int CAMP_Z = SIZE_Z - CAMP_DEPTH;
	/** The local y of the mouth floor. The walk may drop MAX_DROP below it. */
	public static final int FLOOR = 5;
	public static final int TUNNEL_WIDTH = 3;
	public static final int TUNNEL_HEIGHT = 3;
	public static final int MIN_BENDS = 3;
	public static final int MAX_BENDS = 5;
	public static final int MIN_DROP = 2;
	public static final int MAX_DROP = 4;
	public static final int MIN_COLLAPSES = 1;
	public static final int MAX_COLLAPSES = 3;
	/** The rails stop this many cells short of the lectern. */
	public static final int RAIL_STOP = 3;
	public static final int MINERS = 5;
	public static final int RATS = 6;
	/** The names a miner's journal may carry. */
	public static final int NAMES = 8;

	/** One spot of the plan. */
	public record Spot(int x, int y, int z) {
	}

	private final List<Spot> centre = new ArrayList<>();
	private final List<Spot> miners = new ArrayList<>();
	private final List<Spot> rats = new ArrayList<>();
	private final Map<Integer, BlockState> blocks = new HashMap<>();
	private int bends;
	private int dropDepth;
	private int dropAt;
	private int collapses;
	private Spot core;
	private Spot vault;
	private Spot lectern = new Spot(0, 0, 0);
	private Spot fire;
	private Spot tent;
	private int journalName;
	private int journalDays;

	private CampPlan(final RandomSource random) {
		this.bends = MIN_BENDS + random.nextInt(MAX_BENDS - MIN_BENDS + 1);
		this.dropDepth = MIN_DROP + random.nextInt(MAX_DROP - MIN_DROP + 1);
		this.collapses = MIN_COLLAPSES + random.nextInt(MAX_COLLAPSES - MIN_COLLAPSES + 1);
		this.journalName = random.nextInt(NAMES);
		this.journalDays = 3 + random.nextInt(28);
		walk(random);
		dress(random);
		camp(random);
	}

	public static CampPlan derive(final RandomSource random) {
		return new CampPlan(random);
	}

	/** Shorthand for the seed of a test or a run. */
	public static CampPlan derive(final long seed) {
		return new CampPlan(RandomSource.create(seed));
	}

	// ---- the walk ----

	/** Walks the tunnel from the mouth north, bending sideways, and drops one segment on a ramp. */
	private void walk(final RandomSource random) {
		int half = TUNNEL_WIDTH / 2;
		int x = SIZE_X / 2;
		int z = 0;
		int y = FLOOR;
		int legsLeft = this.bends;
		this.dropAt = MOUTH / 2 + 2 + random.nextInt(6);
		while (z < CAMP_Z) {
			// The span left is shared by the legs left, so the walk always reaches the camp and still bends.
			int share = Math.max(2, (CAMP_Z - z + legsLeft) / (legsLeft + 1));
			int run = Math.min(share, CAMP_Z - z);
			for (int i = 0; i < run; i++) {
				// The ramp: one segment steps the floor down by the rolled drop, one block per cell.
				if (this.centre.size() >= this.dropAt && this.centre.size() < this.dropAt + this.dropDepth && y > FLOOR - this.dropDepth) {
					y--;
				}
				this.centre.add(new Spot(x, y, z));
				z++;
			}
			if (z >= CAMP_Z) {
				break;
			}
			if (legsLeft > 0) {
				legsLeft--;
				int shift = 2 + random.nextInt(3);
				int way = random.nextBoolean() ? 1 : -1;
				for (int i = 0; i < shift; i++) {
					int next = x + way;
					if (next < half + 1 || next > SIZE_X - half - 2) {
						break;
					}
					x = next;
					this.centre.add(new Spot(x, y, z - 1));
				}
			}
		}
		this.lectern = this.centre.get(this.centre.size() - 1);
	}

	// ---- the works ----

	/** Carves the tunnel, then lines it: walls, floor, beams, rails, the ramp ladder, and the collapses. */
	private void dress(final RandomSource random) {
		int half = TUNNEL_WIDTH / 2;
		for (int i = 0; i < this.centre.size(); i++) {
			Spot spot = this.centre.get(i);
			int railEnd = this.centre.size() - RAIL_STOP;
			for (int dx = -half; dx <= half; dx++) {
				for (int dy = 0; dy <= TUNNEL_HEIGHT; dy++) {
					set(spot.x() + dx, spot.y() + dy, spot.z(), Blocks.AIR.defaultBlockState());
				}
				set(spot.x() + dx, spot.y() - 1, spot.z(), Blocks.OAK_PLANKS.defaultBlockState());
			}
			if (i < railEnd) {
				set(spot.x(), spot.y(), spot.z(), Blocks.RAIL.defaultBlockState());
			}
			if (i >= this.dropAt && i < this.dropAt + this.dropDepth) {
				for (int dy = 0; dy <= TUNNEL_HEIGHT; dy++) {
					set(spot.x() + half + 1, spot.y() + dy, spot.z(), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
				}
			}
			if (i % 4 == 0 && i > 0 && i < this.centre.size() - 2) {
				// A post stands in rock only: a bend opens the wall beside it.
				if (wallFree(spot.x() - half - 1, spot.z())) {
					for (int dy = 0; dy < TUNNEL_HEIGHT; dy++) {
						set(spot.x() - half - 1, spot.y() + dy, spot.z(), Blocks.OAK_LOG.defaultBlockState());
					}
				}
				if (wallFree(spot.x() + half + 1, spot.z())) {
					for (int dy = 0; dy < TUNNEL_HEIGHT; dy++) {
						set(spot.x() + half + 1, spot.y() + dy, spot.z(), Blocks.OAK_LOG.defaultBlockState());
					}
				}
				for (int dx = -half - 1; dx <= half + 1; dx++) {
					set(spot.x() + dx, spot.y() + TUNNEL_HEIGHT, spot.z(), Blocks.OAK_LOG.defaultBlockState());
				}
				set(spot.x(), spot.y() + TUNNEL_HEIGHT - 1, spot.z(), Blocks.TORCH.defaultBlockState());
			}
		}
		// The mouth: every cell of the band from the first tunnel cell outward, at the mouth height.
		Spot first = this.centre.get(0);
		for (int z = 0; z <= first.z(); z++) {
			for (int dx = -half; dx <= half; dx++) {
				for (int dy = 0; dy <= TUNNEL_HEIGHT; dy++) {
					set(first.x() + dx, FLOOR + dy, z, Blocks.AIR.defaultBlockState());
				}
				set(first.x() + dx, FLOOR - 1, z, Blocks.OAK_PLANKS.defaultBlockState());
			}
		}
		for (int i = 0; i < this.collapses; i++) {
			int at = MOUTH / 2 + random.nextInt(Math.max(1, this.centre.size() - MOUTH));
			Spot spot = this.centre.get(Math.min(at, this.centre.size() - 1));
			for (int dx = -half; dx <= half; dx++) {
				set(spot.x() + dx, spot.y() + TUNNEL_HEIGHT - 1, spot.z(), Blocks.GRAVEL.defaultBlockState());
				set(spot.x() + dx, spot.y() + TUNNEL_HEIGHT - 2, spot.z(), dx == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
			}
		}
	}

	// ---- the camp ----

	/** True when no cell of the walk at the same z carves the tunnel at x. */
	private boolean wallFree(final int x, final int z) {
		int half = TUNNEL_WIDTH / 2;
		for (Spot spot : this.centre) {
			if (spot.z() == z && Math.abs(spot.x() - x) <= half) {
				return false;
			}
		}
		return true;
	}

	/** The camp at the north end: the tally post, the supply pile, the tent and the fire. */
	private void camp(final RandomSource random) {
		int cx = SIZE_X / 2;
		int cz = SIZE_Z - 6;
		// The camp stands where the walk arrives, so the tunnel opens into it.
		int y = this.lectern.y();
		// The room of the camp first, so its own walls and seats stand in open air.
		// The room reaches the dead end, wherever the walk left the tunnel.
		int west = Math.min(cx - CAMP_DEPTH / 2 - 1, this.lectern.x() - 2);
		int east = Math.max(cx + CAMP_DEPTH / 2 + 2, this.lectern.x() + 2);
		for (int x = west; x <= east; x++) {
			for (int dz = 0; dz < CAMP_DEPTH; dz++) {
				set(x, y - 1, CAMP_Z + dz, Blocks.COARSE_DIRT.defaultBlockState());
				for (int dy = 0; dy < 4; dy++) {
					set(x, y + dy, CAMP_Z + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		this.core = new Spot(cx, y + 1, cz);
		set(cx, y, cz, Blocks.OAK_FENCE.defaultBlockState());
		set(cx, y + 1, cz, Blocks.OAK_FENCE.defaultBlockState());
		set(cx, y + 2, cz, Blocks.OAK_LOG.defaultBlockState());
		this.fire = new Spot(cx, y, cz - 2);
		set(cx, y, cz - 2, Blocks.CAMPFIRE.defaultBlockState());
		for (int[] seat : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			set(cx + seat[0] * 2, y, cz - 2 + seat[1] * 2, Blocks.OAK_LOG.defaultBlockState());
		}
		this.vault = new Spot(cx - 4, y + 1, cz + 2);
		set(cx - 5, y, cz + 1, Blocks.BARREL.defaultBlockState());
		set(cx - 4, y, cz + 1, Blocks.BARREL.defaultBlockState());
		set(cx - 4, y + 1, cz + 2, Blocks.BARREL.defaultBlockState());
		this.tent = new Spot(cx + 5, y, cz);
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				boolean wall = dx == 0 || dx == 4 || dz == 0 || dz == 4;
				for (int dy = 0; dy < 3; dy++) {
					if (dy == 2 && (dx == 2 || dz == 2)) {
						continue;
					}
					if (wall || dy == 2) {
						set(cx + 3 + dx, y + dy, cz - 2 + dz, dz == 0 && dx == 2 ? Blocks.AIR.defaultBlockState() : Blocks.WOOL.white().defaultBlockState());
					}
				}
			}
		}
		// The junctions: the tunnel meets the clearing.
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy < 5; dy++) {
				set(SIZE_X / 2 + dx, y + dy, CAMP_Z, Blocks.AIR.defaultBlockState());
			}
		}
		set(this.lectern.x(), this.lectern.y() + 1, this.lectern.z(), Blocks.LECTERN.defaultBlockState());
		place(random);
	}

	/** Five miners and six rats along the walk and the camp. */
	private void place(final RandomSource random) {
		int step = Math.max(1, this.centre.size() / (MINERS + 1));
		for (int i = 1; i <= MINERS; i++) {
			Spot spot = this.centre.get(Math.min(i * step, this.centre.size() - 1));
			this.miners.add(new Spot(spot.x(), spot.y(), spot.z()));
		}
		int rstep = Math.max(1, this.centre.size() / (RATS + 1));
		for (int i = 1; i <= RATS; i++) {
			Spot spot = this.centre.get(Math.min(i * rstep, this.centre.size() - 1));
			this.rats.add(new Spot(spot.x() + (i % 2 == 0 ? 1 : -1), spot.y(), spot.z()));
		}
		this.miners.add(new Spot(this.tent.x(), this.tent.y(), this.tent.z()));
	}

	// ---- reading ----

	private void set(final int x, final int y, final int z, final BlockState state) {
		if (x < 0 || z < 0 || y < 0 || x >= SIZE_X || z >= SIZE_Z || y >= SIZE_Y) {
			return;
		}
		this.blocks.put(pack(x, y, z), state);
	}

	private static int pack(final int x, final int y, final int z) {
		return (x * SIZE_Y + y) * SIZE_Z + z;
	}

	/** The block at a local spot, or null when the camp leaves that spot to the world. */
	public BlockState stateAt(final int x, final int y, final int z) {
		return this.blocks.get(pack(x, y, z));
	}

	/** Every cell the camp writes, for a layout to walk. */
	public Map<Integer, BlockState> blocks() {
		return this.blocks;
	}

	/** Unpacks a key of {@link #blocks()} back to a spot. */
	public static Spot unpack(final int key) {
		int z = key % SIZE_Z;
		int rest = key / SIZE_Z;
		int y = rest % SIZE_Y;
		int x = rest / SIZE_Y;
		return new Spot(x, y, z);
	}

	public List<Spot> centre() {
		return this.centre;
	}

	public List<Spot> miners() {
		return this.miners;
	}

	public List<Spot> rats() {
		return this.rats;
	}

	public Spot core() {
		return this.core;
	}

	public Spot vault() {
		return this.vault;
	}

	public Spot lectern() {
		return this.lectern;
	}

	public Spot fire() {
		return this.fire;
	}

	public Spot tent() {
		return this.tent;
	}

	public int bends() {
		return this.bends;
	}

	public int dropDepth() {
		return this.dropDepth;
	}

	public int dropAt() {
		return this.dropAt;
	}

	public int collapses() {
		return this.collapses;
	}

	public int journalName() {
		return this.journalName;
	}

	public int journalDays() {
		return this.journalDays;
	}

	/** The first cell of the mouth, where the tunnel meets cave air. */
	public Spot mouth() {
		return this.centre.get(0);
	}
}
