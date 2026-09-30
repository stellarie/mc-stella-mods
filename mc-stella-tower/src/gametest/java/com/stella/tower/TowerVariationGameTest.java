package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The floor variations: how a floor's variation is drawn (weights and rules), what each variation's walls look like,
 * that every plan of every variation has a walk from arrival to portal, that mobs still spawn on them, and the chest
 * spots every plan carries.
 */
public class TowerVariationGameTest {
	private static final long SEED = 20_260_930L;
	private static final int S = TowerLayout.SIZE;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final TowerPlan.Variation[] ALL = TowerPlan.Variation.values();

	private static int rank(final int floor) {
		return TowerMaze.hall(floor) == 0 ? 0 : TowerMaze.hall(floor) == TowerMaze.ELITE_HALL ? 1 : 2;
	}

	/** The first floors from 2 on, in a tower of the test seed, whose variation is this one (and ordinary, if asked). */
	private static List<Integer> floorsOf(final TowerPlan.Variation variation, final int count, final boolean ordinary) {
		List<Integer> floors = new ArrayList<>();
		for (int floor = 2; floors.size() < count && floor < 1_000_000; floor++) {
			if (TowerPlan.variationOf(SEED, floor) == variation && (!ordinary || TowerMaze.hall(floor) == 0)) {
				floors.add(floor);
			}
		}
		return floors;
	}

	/** One floor of a block of fresh floors for each wish, no two alike: the tower holds no other run's floor among them. */
	private static int[] pick(final long seed, final int start, final int size, final List<java.util.function.IntPredicate> wishes) {
		int[] picked = new int[wishes.size()];
		java.util.Arrays.fill(picked, -1);
		for (int floor = start; floor < start + size - 3; floor++) {
			for (int i = 0; i < picked.length; i++) {
				if (picked[i] < 0 && wishes.get(i).test(floor) && !contains(picked, floor)) {
					picked[i] = floor;
					break;
				}
			}
		}
		for (int i = 0; i < picked.length; i++) {
			if (picked[i] < 0) {
				throw new IllegalStateException("no floor in " + start + ".." + (start + size) + " fits wish " + i);
			}
		}
		return picked;
	}

	private static boolean contains(final int[] values, final int value) {
		for (int each : values) {
			if (each == value) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the draw

	/**
	 * Over 8000 floors the shares of each rank of floor match what the rules and weights give. What they give is worked out
	 * here by walking the chain floor by floor from its kernel (the lambdas the floor after another draws by), so it includes the
	 * way elite and boss floors, which bar the spiral and wide rooms, change what follows them. The bound is five standard
	 * deviations of a count of this many floors, stretched by the square root of two for a chain's own correlation, and a
	 * small allowance for the block edges the chain is cut at.
	 */
	@GameTest
	public void variationsFollowTheirWeightsAndRules(final GameTestHelper helper) {
		int n = 8000;
		int sum = 0;
		for (TowerPlan.Variation each : ALL) {
			sum += each.weight();
		}
		helper.assertTrue(sum == 100, "the weights add up to " + sum);
		helper.assertTrue(TowerPlan.Variation.MAZE.weight() == 35 && TowerPlan.Variation.WIDE_ROOMS.weight() == 20 && TowerPlan.Variation.STAGGERED.weight() == 15
			&& TowerPlan.Variation.TALL_WALLS.weight() == 15 && TowerPlan.Variation.SPIRAL.weight() == 15, "the weights are not the ones decided");
		double[] lambda = TowerVariations.lambda();
		double[] pi = new double[ALL.length];
		pi[TowerPlan.Variation.MAZE.ordinal()] = 1.0;
		double[][] expected = new double[3][ALL.length];
		int[] size = new int[3];
		int[][] got = new int[3][ALL.length];
		TowerPlan.Variation before = null;
		int repeats = 0;
		int barred = 0;
		for (int floor = 1; floor <= n; floor++) {
			TowerPlan.Variation variation = TowerPlan.variationOf(SEED, floor);
			List<TowerPlan.Variation> allowed = TowerVariations.allowed(floor);
			if (floor > 1) {
				double[] next = new double[ALL.length];
				for (int from = 0; from < ALL.length; from++) {
					double total = 0.0;
					for (TowerPlan.Variation to : allowed) {
						total += to.ordinal() == from ? 0.0 : lambda[to.ordinal()];
					}
					for (TowerPlan.Variation to : allowed) {
						if (to.ordinal() != from) {
							next[to.ordinal()] += pi[from] * lambda[to.ordinal()] / total;
						}
					}
				}
				pi = next;
			}
			int rank = rank(floor);
			size[rank]++;
			for (int k = 0; k < ALL.length; k++) {
				expected[rank][k] += pi[k];
			}
			got[rank][variation.ordinal()]++;
			repeats += variation == before ? 1 : 0;
			before = variation;
			int hall = TowerMaze.hall(floor);
			barred += hall > 0 && variation == TowerPlan.Variation.SPIRAL || hall == TowerMaze.BOSS_HALL && variation == TowerPlan.Variation.WIDE_ROOMS ? 1 : 0;
			helper.assertTrue(floor != 1 || variation == TowerPlan.Variation.MAZE, "floor 1 is " + variation);
		}
		StringBuilder line = new StringBuilder("floors=" + n + " repeats=" + repeats + " barred=" + barred);
		double worst = 0.0;
		String worstAt = "";
		for (int rank = 0; rank < 3; rank++) {
			for (int k = 0; k < ALL.length; k++) {
				double p = expected[rank][k] / size[rank];
				double measured = got[rank][k] / (double) size[rank];
				double bound = 5.0 * Math.sqrt(p * (1.0 - p) / size[rank]) * Math.sqrt(2.0) + 0.004;
				line.append(String.format(" r%d%s=%.3f/%.3f", rank, ALL[k].name().substring(0, 2), measured, p));
				if (Math.abs(measured - p) / bound > worst) {
					worst = Math.abs(measured - p) / bound;
					worstAt = "rank " + rank + " " + ALL[k] + " measured " + measured + " expected " + p + " bound " + bound;
				}
				if (p == 0.0) {
					helper.assertTrue(got[rank][k] == 0, "a rank " + rank + " floor took the barred " + ALL[k]);
				}
			}
		}
		TowerTestSupport.result("variationDraw", line + " worstOfBound=" + String.format("%.2f", worst));
		helper.assertTrue(repeats == 0, repeats + " floors took the variation of the floor before");
		helper.assertTrue(barred == 0, barred + " floors took a variation barred to their rank");
		helper.assertTrue(worst <= 1.0, "a share is off its expected one past the bound: " + worstAt);
		helper.succeed();
	}

	/** A plan that finds no walk in eight draws falls back to the plain maze; how often that happens is measured. */
	@GameTest
	public void theFallbackToTheMazeIsRareAndReported(final GameTestHelper helper) {
		int floors = 3000;
		int fallbacks = 0;
		int reseeded = 0;
		int[] byVariation = new int[ALL.length];
		for (int floor = 1; floor <= floors; floor++) {
			TowerPlan plan = TowerPlan.generate(SEED, floor, drawn -> false);
			TowerPlan.Variation chosen = TowerPlan.variationOf(SEED, floor);
			if (plan.attempt() == TowerPlan.FALLBACK) {
				fallbacks++;
				byVariation[chosen.ordinal()]++;
				helper.assertTrue(plan.variation() == TowerPlan.Variation.MAZE, "a fallback floor is not the maze");
			} else {
				reseeded += plan.attempt() > 0 ? 1 : 0;
				helper.assertTrue(plan.variation() == chosen, "floor " + floor + " was drawn as " + plan.variation() + ", not its " + chosen);
			}
		}
		TowerTestSupport.result("variationFallback", "floors=" + floors + " fallbacks=" + fallbacks + " reseeded=" + reseeded + " fallbackByVariation=" + java.util.Arrays.toString(byVariation));
		helper.assertTrue(fallbacks * 100 <= floors, fallbacks + " of " + floors + " floors fell back to the maze");
		helper.succeed();
	}

	// ---------------------------------------------------------------- tall walls

	/** A tall-wall floor as built: its maze walls two blocks high, open air above up to the ceiling; a plain maze's reach the ceiling. */
	@GameTest(maxTicks = 100)
	public void tallWallsAreBuiltTwoHighWithAirAbove(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int[] picked = pick(seed, TowerTestSupport.freshBlock(200), 200, List.of(
			f -> TowerMaze.hall(f) == 0 && TowerPlan.variationOf(seed, f) == TowerPlan.Variation.TALL_WALLS,
			f -> TowerMaze.hall(f) == 0 && TowerPlan.variationOf(seed, f) == TowerPlan.Variation.MAZE));
		int floor = picked[0];
		int plain = picked[1];
		TowerFloors.ensure(tower, floor);
		TowerFloors.ensure(tower, plain);
		int[] tall = heights(tower, TowerPlan.at(tower, floor));
		int[] maze = heights(tower, TowerPlan.at(tower, plain));
		// A boss floor of this variation keeps the room's ring and the hall's enclosure full height.
		int bossFloor = -1;
		for (int f = 2; f < 100_000 && bossFloor < 0; f++) {
			bossFloor = TowerMaze.hall(f) == TowerMaze.BOSS_HALL && TowerPlan.variationOf(SEED, f) == TowerPlan.Variation.TALL_WALLS ? f : -1;
		}
		TowerPlan boss = TowerPlan.of(SEED, bossFloor);
		int ringLow = 0;
		int hallLow = 0;
		for (int x = 0; x < S; x++) {
			for (int z = 0; z < S; z++) {
				ringLow += boss.isRing(x, z) && boss.wallHeightAt(x, z) != TowerLayout.HEIGHT ? 1 : 0;
				hallLow += boss.wall(x, z) && Math.abs(x - S / 2) <= boss.hall() / 2 + 1 && Math.abs(z - S / 2) <= boss.hall() / 2 + 1
					&& boss.wallHeightAt(x, z) != TowerLayout.HEIGHT ? 1 : 0;
			}
		}
		TowerTestSupport.result("tallWalls", "floor=" + floor + " wallCells=" + tall[0] + " wrongHeight=" + tall[1] + " airAboveFaults=" + tall[2] + " height=" + TowerPlan.TALL_WALL_HEIGHT
			+ " mazeWallCells=" + maze[0] + " mazeWrongHeight=" + maze[1] + " bossFloor=" + bossFloor + " ringShort=" + ringLow + " hallWallShort=" + hallLow);
		helper.assertTrue(tall[0] > 500 && maze[0] > 500, "the case proves little: " + tall[0] + " and " + maze[0] + " wall cells");
		helper.assertTrue(tall[1] == 0, tall[1] + " wall cells are not exactly " + TowerPlan.TALL_WALL_HEIGHT + " blocks of maze wall");
		helper.assertTrue(tall[2] == 0, tall[2] + " blocks above the walls are not air");
		helper.assertTrue(maze[1] == 0, maze[1] + " cells of a plain maze's walls are not full height");
		helper.assertTrue(ringLow == 0 && hallLow == 0, ringLow + " ring and " + hallLow + " hall-wall cells of a boss floor are short");
		helper.succeed();
	}

	/** {wall cells, cells whose blocks are not the plan's wall height of maze wall, blocks above a tall wall that are not air}. */
	private static int[] heights(final ServerLevel tower, final TowerPlan plan) {
		BlockPos o = plan.origin();
		int cells = 0;
		int wrong = 0;
		int above = 0;
		for (int x = 0; x < S; x++) {
			for (int z = 0; z < S; z++) {
				if (!plan.wall(x, z)) {
					continue;
				}
				cells++;
				int height = plan.wallHeightAt(x, z);
				boolean cellWrong = false;
				for (int y = 0; y < TowerLayout.HEIGHT; y++) {
					BlockState state = tower.getBlockState(o.offset(x, y, z));
					boolean solid = y < height;
					if (solid && !state.is(TowerBlocks.TOWER_MAZE_WALL) || !solid && !state.isAir()) {
						cellWrong = true;
						above += !solid && !state.isAir() ? 1 : 0;
					}
				}
				wrong += cellWrong ? 1 : 0;
			}
		}
		return new int[] {cells, wrong, above};
	}

	// ---------------------------------------------------------------- staggered rooms

	/** The wall lines of a brick row read off the walls: the columns that are wall along all but a door's height of the row. */
	private static List<Integer> separators(final boolean[][] walls, final int row) {
		List<Integer> lines = new ArrayList<>();
		for (int x = 0; x < S; x++) {
			int solid = 0;
			for (int dz = 0; dz < TowerMaze.PATH; dz++) {
				solid += walls[x][TowerShapes.brickZ(row) + dz] ? 1 : 0;
			}
			if (solid >= TowerMaze.PATH - TowerShapes.DOOR) {
				lines.add(x);
			}
		}
		return lines;
	}

	/** The longest run of open cells along a stretch. */
	private static int longestOpen(final boolean[][] walls, final int fromX, final int fromZ, final int dx, final int dz, final int length) {
		int best = 0;
		int run = 0;
		for (int i = 0; i < length; i++) {
			run = walls[fromX + dx * i][fromZ + dz * i] ? 0 : run + 1;
			best = Math.max(best, run);
		}
		return best;
	}

	/**
	 * The staggered rooms: rooms in a row a fixed width, every other row offset by half a room, and a door at least two
	 * wide between every two rooms that touch, in a row and from row to row. Read off 300 draws' walls, not off the
	 * generator's own lists of rooms.
	 */
	@GameTest
	public void staggeredRoomsAreOffsetEveryOtherRowWithADoorBetweenNeighbours(final GameTestHelper helper) {
		int draws = 300;
		int pairs = 0;
		int missing = 0;
		int narrow = 0;
		int unshifted = 0;
		int[] offsets = new int[2];
		for (int i = 0; i < draws; i++) {
			int offset = i % 2 == 0 ? 4 : 5;
			boolean[][] walls = TowerShapes.bricks(new Random(i * 7919L + 1), offset, -1, 0);
			List<List<Integer>> lines = new ArrayList<>();
			for (int row = 0; row < TowerShapes.BRICK_ROWS; row++) {
				lines.add(separators(walls, row));
			}
			for (int row = 0; row < TowerShapes.BRICK_ROWS; row++) {
				// Even rows start a whole room in from the wall, odd rows half a room: within a block of half.
				int first = lines.get(row).get(1);
				boolean shifted = row % 2 == 1 && Math.abs(2 * first - TowerShapes.BRICK_PITCH) <= 1;
				boolean whole = row % 2 == 0 && first == TowerShapes.BRICK_PITCH;
				unshifted += shifted || whole ? 0 : 1;
				offsets[row % 2] += 1;
				List<Integer> here = lines.get(row);
				for (int k = 1; k + 1 < here.size(); k++) {
					// A door in every wall between two rooms of the row.
					pairs++;
					int door = longestOpen(walls, here.get(k), TowerShapes.brickZ(row), 0, 1, TowerMaze.PATH);
					missing += door == 0 ? 1 : 0;
					narrow += door > 0 && door < TowerShapes.DOOR ? 1 : 0;
				}
				if (row + 1 < TowerShapes.BRICK_ROWS) {
					List<Integer> next = lines.get(row + 1);
					int between = TowerShapes.brickZ(row) + TowerMaze.PATH;
					for (int a = 0; a + 1 < here.size(); a++) {
						for (int b = 0; b + 1 < next.size(); b++) {
							int low = Math.max(here.get(a), next.get(b)) + 1;
							int high = Math.min(here.get(a + 1), next.get(b + 1)) - 1;
							if (high - low + 1 < 1) {
								continue;
							}
							pairs++;
							int door = longestOpen(walls, low, between, 1, 0, high - low + 1);
							missing += door == 0 ? 1 : 0;
							narrow += door > 0 && door < Math.min(TowerShapes.DOOR, high - low + 1) ? 1 : 0;
						}
					}
				}
			}
		}
		TowerTestSupport.result("staggeredRooms", "draws=" + draws + " neighbourPairs=" + pairs + " withoutDoor=" + missing + " narrowDoors=" + narrow + " rowsNotOffsetRight=" + unshifted);
		helper.assertTrue(pairs > 20_000, "the case proves little: " + pairs + " pairs");
		helper.assertTrue(unshifted == 0, unshifted + " rows are not laid like bricks");
		helper.assertTrue(missing == 0, missing + " pairs of neighbouring rooms have no door");
		helper.assertTrue(narrow == 0, narrow + " doors are narrower than " + TowerShapes.DOOR);
		helper.succeed();
	}

	// ---------------------------------------------------------------- wide rooms

	/**
	 * Wide rooms: on an ordinary floor every cell of the two-wide grid is open (paths two wide), and every plan, elite floors
	 * included, has at least three rooms of 6 x 3, 4 or 5 either way round, open from wall to wall, clear of each other and of
	 * the zone, with open ground beside them that a path leads in by.
	 */
	@GameTest
	public void wideRoomsHaveTwoWidePathsAndAtLeastThreeRooms(final GameTestHelper helper) {
		List<Integer> floors = floorsOf(TowerPlan.Variation.WIDE_ROOMS, 400, false);
		int thin = 0;
		int tiled = 0;
		int fewRooms = 0;
		int badSize = 0;
		int shut = 0;
		int overlapping = 0;
		int inZone = 0;
		int unjoined = 0;
		int rooms = 0;
		int hallFloors = 0;
		java.util.Set<String> sizes = new java.util.TreeSet<>();
		for (int floor : floors) {
			TowerPlan plan = TowerPlan.of(SEED, floor);
			hallFloors += plan.hall() > 0 ? 1 : 0;
			if (plan.hall() == 0) {
				tiled++;
				for (int cx = 0; cx < TowerShapes.WIDE_CELLS; cx++) {
					for (int cz = 0; cz < TowerShapes.WIDE_CELLS; cz++) {
						boolean open = true;
						for (int dx = 0; dx < TowerShapes.WIDE_PATH; dx++) {
							for (int dz = 0; dz < TowerShapes.WIDE_PATH; dz++) {
								open &= !plan.wall(1 + cx * TowerShapes.WIDE_PITCH + dx, 1 + cz * TowerShapes.WIDE_PITCH + dz);
							}
						}
						thin += open ? 0 : 1;
					}
				}
			}
			List<TowerPlan.Room> found = plan.rooms();
			fewRooms += found.size() < TowerShapes.MIN_ROOMS ? 1 : 0;
			for (int i = 0; i < found.size(); i++) {
				TowerPlan.Room room = found.get(i);
				rooms++;
				int small = Math.min(room.width(), room.length());
				int large = Math.max(room.width(), room.length());
				boolean allowed = large == TowerShapes.ROOM_LONG && java.util.Arrays.stream(TowerShapes.ROOM_SHORT).anyMatch(side -> side == small);
				badSize += allowed ? 0 : 1;
				sizes.add(room.width() + "x" + room.length());
				boolean open = true;
				boolean joined = false;
				for (int x = room.x() - 1; x <= room.x() + room.width(); x++) {
					for (int z = room.z() - 1; z <= room.z() + room.length(); z++) {
						boolean inside = x >= room.x() && x < room.x() + room.width() && z >= room.z() && z < room.z() + room.length();
						if (inside) {
							open &= !plan.wall(x, z);
						} else if (x >= 0 && z >= 0 && x < S && z < S && !plan.wall(x, z)) {
							joined = true;
						}
					}
				}
				shut += open ? 0 : 1;
				unjoined += joined ? 0 : 1;
				for (int j = i + 1; j < found.size(); j++) {
					overlapping += room.touches(found.get(j), 0) ? 1 : 0;
				}
				for (TowerMaze.Zone zone : plan.safeZones()) {
					inZone += room.touches(new TowerPlan.Room(zone.x(), zone.z(), TowerMaze.SAFE, TowerMaze.SAFE), 0) ? 1 : 0;
				}
			}
		}
		TowerTestSupport.result("wideRooms", "plans=" + floors.size() + " hallFloors=" + hallFloors + " tiledPlans=" + tiled + " pathCellsNotTwoWide=" + thin + " rooms=" + rooms + " plansWithFewRooms=" + fewRooms
			+ " badSizes=" + badSize + " roomsNotOpen=" + shut + " overlapping=" + overlapping + " inZone=" + inZone + " unjoined=" + unjoined + " sizes=" + sizes);
		helper.assertTrue(floors.size() == 400 && hallFloors > 10 && tiled > 200, "the case proves little: " + floors.size() + " plans, " + hallFloors + " with halls");
		helper.assertTrue(thin == 0, thin + " cells of the wide maze's grid are not two wide and open");
		helper.assertTrue(fewRooms == 0, fewRooms + " plans have fewer than " + TowerShapes.MIN_ROOMS + " rooms");
		helper.assertTrue(badSize == 0, badSize + " rooms are not 6 x 3, 4 or 5");
		helper.assertTrue(sizes.size() == 6, "only these sizes came up: " + sizes);
		helper.assertTrue(shut == 0 && overlapping == 0 && inZone == 0, shut + " rooms are not open, " + overlapping + " overlap, " + inZone + " lie in a zone");
		helper.assertTrue(unjoined == 0, unjoined + " rooms have no open ground beside them");
		helper.succeed();
	}

	// ---------------------------------------------------------------- spiral

	/**
	 * The spiral is one corridor: its seven by seven cells are all open, joined by forty-eight openings, none with more than
	 * two, the arrival's and the portal's the two ends, and the walk from one to the other covers the floor.
	 */
	@GameTest
	public void theSpiralIsASingleCorridorFromArrivalToPortal(final GameTestHelper helper) {
		List<Integer> floors = floorsOf(TowerPlan.Variation.SPIRAL, 300, true);
		int n = TowerShapes.SPIRAL_CELLS;
		int notAll = 0;
		int wrongEdges = 0;
		int overTwo = 0;
		int wrongEnds = 0;
		int disconnected = 0;
		int thinFloors = 0;
		int shortWalks = 0;
		int shortest = Integer.MAX_VALUE;
		double leastOpen = 1.0;
		for (int floor : floors) {
			TowerPlan plan = TowerPlan.of(SEED, floor);
			int pitch = TowerShapes.SPIRAL_PITCH;
			int path = TowerShapes.SPIRAL_PATH;
			boolean[][] open = new boolean[n][n];
			int opened = 0;
			for (int cx = 0; cx < n; cx++) {
				for (int cz = 0; cz < n; cz++) {
					int cells = 0;
					for (int dx = 0; dx < path; dx++) {
						for (int dz = 0; dz < path; dz++) {
							cells += plan.wall(1 + cx * pitch + dx, 1 + cz * pitch + dz) ? 0 : 1;
						}
					}
					// The portal's cell keeps solid behind the portal, a quarter of it and less.
					open[cx][cz] = cells >= path * path * 3 / 4 - 4;
					opened += open[cx][cz] ? 0 : 1;
				}
			}
			notAll += opened;
			int[][] degree = new int[n][n];
			int edges = 0;
			for (int cx = 0; cx < n; cx++) {
				for (int cz = 0; cz < n; cz++) {
					for (int[] way : new int[][] {{1, 0}, {0, 1}}) {
						int nx = cx + way[0];
						int nz = cz + way[1];
						if (nx >= n || nz >= n) {
							continue;
						}
						// The wall line between two cells, along the cells' width: open where a way is cut through it.
						int lineX = way[0] == 1 ? 1 + cx * pitch + path : 1 + cx * pitch;
						int lineZ = way[1] == 1 ? 1 + cz * pitch + path : 1 + cz * pitch;
						int cut = 0;
						for (int i = 0; i < path; i++) {
							cut += plan.wall(lineX + (way[0] == 1 ? 0 : i), lineZ + (way[1] == 1 ? 0 : i)) ? 0 : 1;
						}
						if (cut > 0) {
							edges++;
							degree[cx][cz]++;
							degree[nx][nz]++;
						}
					}
				}
			}
			wrongEdges += edges == n * n - 1 ? 0 : 1;
			int ends = 0;
			int arrivalCx = (plan.arrivalX() - 1) / pitch;
			int arrivalCz = (plan.arrivalZ() - 1) / pitch;
			int portalCx = (plan.portalX() - 1) / pitch;
			int portalCz = (plan.portalZ() - 1) / pitch;
			boolean over = false;
			for (int cx = 0; cx < n; cx++) {
				for (int cz = 0; cz < n; cz++) {
					over |= degree[cx][cz] > 2;
					ends += degree[cx][cz] == 1 ? 1 : 0;
				}
			}
			overTwo += over ? 1 : 0;
			boolean endsAreTheTwo = ends == 2 && degree[arrivalCx][arrivalCz] == 1 && degree[portalCx][portalCz] == 1 && (arrivalCx != portalCx || arrivalCz != portalCz);
			wrongEnds += endsAreTheTwo ? 0 : 1;
			int openCells = 0;
			for (int x = 1; x < S - 1; x++) {
				for (int z = 1; z < S - 1; z++) {
					openCells += plan.wall(x, z) ? 0 : 1;
				}
			}
			double share = openCells / (double) ((S - 2) * (S - 2));
			leastOpen = Math.min(leastOpen, share);
			thinFloors += share < 0.75 ? 1 : 0;
			int walk = TowerChestSpots.walk(plan)[plan.portalX()][plan.portalZ()];
			shortest = Math.min(shortest, walk);
			shortWalks += walk < (n * n - 1) * pitch * 4 / 5 ? 1 : 0;
			disconnected += walk < 0 ? 1 : 0;
		}
		TowerTestSupport.result("spiral", "plans=" + floors.size() + " cellsNotOpen=" + notAll + " wrongOpeningCounts=" + wrongEdges + " cellsWithOverTwo=" + overTwo + " wrongEnds=" + wrongEnds
			+ " disconnected=" + disconnected + " leastOpenShare=" + String.format("%.3f", leastOpen) + " shortestWalk=" + shortest);
		helper.assertTrue(floors.size() == 300, "only " + floors.size() + " spiral floors found");
		helper.assertTrue(wrongEdges == 0, wrongEdges + " spirals are not a chain of " + (n * n) + " cells");
		helper.assertTrue(overTwo == 0, overTwo + " spirals have a cell with more than two openings");
		helper.assertTrue(wrongEnds == 0, wrongEnds + " spirals do not end at the arrival and the portal");
		helper.assertTrue(disconnected == 0 && shortWalks == 0, "a spiral's walk is short or missing: " + shortWalks + " short, " + disconnected + " missing");
		helper.assertTrue(thinFloors == 0, thinFloors + " spirals leave less than three quarters of the floor open");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the walk

	/** Every cell the plan's arrival reaches on foot, the frame's posts in the way. */
	private static boolean[][] reached(final TowerPlan plan) {
		boolean[][] seen = new boolean[S][S];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		seen[plan.arrivalX()][plan.arrivalZ()] = true;
		queue.add(new int[] {plan.arrivalX(), plan.arrivalZ()});
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] way : WAYS) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x >= 0 && z >= 0 && x < S && z < S && !seen[x][z] && !plan.wall(x, z) && !plan.isFramePost(x, z)) {
					seen[x][z] = true;
					queue.add(new int[] {x, z});
				}
			}
		}
		return seen;
	}

	/** Two thousand plans of each variation, of every rank of floor it may take, all with a walk to the portal (and through the boss room to the hall). */
	@GameTest
	public void everyVariationsPlansHaveAWalkFromArrivalToPortal(final GameTestHelper helper) {
		StringBuilder line = new StringBuilder();
		int failures = 0;
		for (TowerPlan.Variation variation : ALL) {
			int plans = 0;
			int stuck = 0;
			int fellBack = 0;
			int halls = 0;
			int firstStuck = -1;
			for (int floor : floorsOf(variation, 2000, false)) {
				TowerPlan plan = TowerPlan.of(SEED, floor);
				plans++;
				fellBack += plan.attempt() == TowerPlan.FALLBACK ? 1 : 0;
				boolean[][] seen = reached(plan);
				boolean walk = seen[plan.portalX()][plan.portalZ()] && seen[plan.portalX()][plan.portalZ() + 1];
				if (plan.hall() > 0) {
					halls++;
					int middle = S / 2;
					walk &= seen[middle][middle];
					if (plan.hall() == TowerMaze.BOSS_HALL) {
						TowerMaze.Zone room = plan.safeZones().get(1);
						walk &= seen[room.x() + TowerMaze.SAFE / 2][room.z() + TowerMaze.SAFE / 2];
					}
				}
				if (!walk) {
					stuck++;
					firstStuck = firstStuck < 0 ? floor : firstStuck;
				}
			}
			line.append(variation).append(" plans=").append(plans).append(" stuck=").append(stuck).append(" fellBack=").append(fellBack).append(" hallFloors=").append(halls).append("; ");
			failures += stuck;
			helper.assertTrue(plans == 2000, "only " + plans + " floors of " + variation + " found");
			helper.assertTrue(stuck == 0, stuck + " " + variation + " plans have no walk, first floor " + firstStuck);
		}
		TowerTestSupport.result("variationWalks", line.toString().trim() + " stuckTotal=" + failures);
		helper.succeed();
	}

	/**
	 * The walk on foot over floors the real builder built, two of each variation, and an elite and a boss floor of the ones that
	 * may take them: the model of the plan's blocks must agree with the world, and find the portal.
	 */
	@GameTest(maxTicks = 200)
	public void builtFloorsOfEveryVariationCanBeWalkedOnFoot(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		List<String> names = new ArrayList<>();
		List<java.util.function.IntPredicate> wanted = new ArrayList<>();
		for (TowerPlan.Variation variation : ALL) {
			for (int i = 0; i < 2; i++) {
				wanted.add(f -> TowerMaze.hall(f) == 0 && TowerPlan.variationOf(seed, f) == variation);
				names.add(variation.name());
			}
		}
		for (TowerPlan.Variation variation : new TowerPlan.Variation[] {TowerPlan.Variation.STAGGERED, TowerPlan.Variation.TALL_WALLS}) {
			wanted.add(f -> TowerMaze.hall(f) == TowerMaze.ELITE_HALL && TowerPlan.variationOf(seed, f) == variation);
			names.add(variation + "-elite");
			wanted.add(f -> TowerMaze.hall(f) == TowerMaze.BOSS_HALL && TowerPlan.variationOf(seed, f) == variation);
			names.add(variation + "-boss");
		}
		int[] picked = pick(seed, TowerTestSupport.freshBlock(2500), 2500, wanted);
		List<Integer> floors = new ArrayList<>();
		for (int floor : picked) {
			floors.add(floor);
		}
		int stuck = 0;
		int disagree = 0;
		int shortest = Integer.MAX_VALUE;
		for (int i = 0; i < floors.size(); i++) {
			int floor = floors.get(i);
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.at(tower, floor);
			helper.assertTrue(plan.variation() == TowerPlan.variationOf(seed, floor) || plan.attempt() == TowerPlan.FALLBACK, "floor " + floor + " is " + plan.variation());
			BlockPos from = BlockPos.containing(plan.arrivalPos());
			Set<BlockPos> goals = new java.util.HashSet<>(plan.portalInside());
			int built = TowerRaisedPortalGameTest.climb(tower, from, goals);
			int model = TowerRaisedPortalGameTest.climb(TowerRaisedPortalGameTest.modelOf(plan), from, goals);
			stuck += built < 0 ? 1 : 0;
			disagree += built != model ? 1 : 0;
			shortest = Math.min(shortest, built);
			if (built < 0 || built != model) {
				TowerTestSupport.result("builtWalkFault", "floor=" + floor + " kind=" + names.get(i) + " built=" + built + " model=" + model);
			}
		}
		TowerTestSupport.result("builtVariationWalks", "floors=" + floors.size() + " stuck=" + stuck + " modelDisagrees=" + disagree + " shortest=" + shortest);
		helper.assertTrue(stuck == 0, stuck + " built floors have no walk from arrival to portal on foot");
		helper.assertTrue(disagree == 0, "the model of the plan's blocks disagrees with the built world on " + disagree + " floors");
		helper.succeed();
	}

	// ---------------------------------------------------------------- spawns

	private static AABB room(final int floor) {
		BlockPos o = TowerLayout.origin(floor);
		return new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + TowerLayout.SIZE, o.getY() + TowerLayout.HEIGHT, o.getZ() + TowerLayout.SIZE);
	}

	private void fills(final GameTestHelper helper, final String name, final TowerPlan.Variation variation) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0 && TowerPlan.variationOf(seed, f) == variation, 150);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
		int[] filledAt = {-1};
		int[] most = {0};
		helper.onEachTick(() -> {
			int now = tower.getEntitiesOfClass(Mob.class, room(floor), mob -> mob.isAlive() && mob.entityTags().contains(TowerMobs.TAG)
				&& TowerMobs.stateOf(mob) != null && TowerMobs.stateOf(mob).rank() == TowerRank.MOB && !(mob instanceof TowerGhost)).size();
			most[0] = Math.max(most[0], now);
			if (filledAt[0] < 0 && now >= TowerMobs.FLOOR_CAP) {
				filledAt[0] = (int) helper.getTick();
			}
		});
		helper.runAfterDelay(380L, () -> {
			List<Mob> mobs = tower.getEntitiesOfClass(Mob.class, room(floor), mob -> mob.entityTags().contains(TowerMobs.TAG) && !(mob instanceof TowerGhost));
			TowerPlan plan = TowerPlan.at(tower, floor);
			int inWall = 0;
			int inZone = 0;
			for (Mob mob : mobs) {
				BlockPos at = mob.blockPosition().subtract(plan.origin());
				// A spider climbs a tall wall and stands on top of it: only a body inside the wall's own blocks is in it.
				inWall += plan.wall(at.getX(), at.getZ()) && at.getY() < plan.wallHeightAt(at.getX(), at.getZ()) ? 1 : 0;
				inZone += plan.inSafeZone(at.getX(), at.getZ()) ? 1 : 0;
			}
			TowerTestSupport.result(name, "floor=" + floor + " variation=" + plan.variation() + " filledAtTick=" + filledAt[0] + " most=" + most[0] + " cap=" + TowerMobs.FLOOR_CAP
				+ " mobs=" + mobs.size() + " inWall=" + inWall + " inZone=" + inZone);
			for (Mob mob : new ArrayList<>(mobs)) {
				mob.discard();
			}
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(filledAt[0] >= 0, "the " + variation + " floor never filled: at most " + most[0] + " of " + TowerMobs.FLOOR_CAP);
			helper.assertTrue(most[0] <= TowerMobs.FLOOR_CAP, "the floor held " + most[0] + ", past its cap of " + TowerMobs.FLOOR_CAP);
			helper.assertTrue(inWall == 0, inWall + " mobs stand in a wall");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void aSpiralFloorFillsToItsCap(final GameTestHelper helper) {
		fills(helper, "spiralFills", TowerPlan.Variation.SPIRAL);
	}

	@GameTest(maxTicks = 400)
	public void aTallWallsFloorFillsToItsCap(final GameTestHelper helper) {
		fills(helper, "tallWallsFills", TowerPlan.Variation.TALL_WALLS);
	}

	// ---------------------------------------------------------------- chest spots

	/** A plan's chest spots: at least five, each a double chest against a wall with its front clear, far, and out of the places chests keep out of; one more on every fifth floor. */
	@GameTest
	public void everyPlanHasAtLeastFiveChestSpotsThatMeetTheRules(final GameTestHelper helper) {
		int plans = 0;
		int fewest = Integer.MAX_VALUE;
		int faults = 0;
		int nearest = Integer.MAX_VALUE;
		int guaranteedFloors = 0;
		int guaranteedFaults = 0;
		String first = "";
		for (int floor = 1; floor <= 3000; floor++) {
			TowerPlan plan = TowerPlan.of(SEED + floor % 3, floor);
			plans++;
			int[][] walk = TowerChestSpots.walk(plan);
			List<TowerTraps.Trap> traps = TowerTraps.layout(plan.seed(), floor, plan);
			int regular = 0;
			int guaranteed = 0;
			List<TowerPlan.ChestSpot> spots = plan.chestSpots();
			for (TowerPlan.ChestSpot spot : spots) {
				String fault = null;
				if (spot.guaranteed()) {
					guaranteed++;
					fault = guaranteedFault(plan, spot);
					guaranteedFaults += fault == null ? 0 : 1;
				} else {
					regular++;
					fault = spotFault(plan, spot, walk, traps, spots);
					faults += fault == null ? 0 : 1;
					nearest = Math.min(nearest, Math.min(walk[spot.x()][spot.z()], walk[spot.second().x()][spot.second().z()]));
				}
				if (fault != null && first.isEmpty()) {
					first = "floor " + floor + " " + plan.variation() + " " + spot + ": " + fault;
				}
			}
			fewest = Math.min(fewest, regular);
			guaranteedFloors += guaranteed;
			boolean fifth = floor % TowerElites.ELITE_EVERY == 0;
			if (regular < TowerChestSpots.MIN_SPOTS || guaranteed != (fifth ? 1 : 0)) {
				faults++;
				first = first.isEmpty() ? "floor " + floor + " has " + regular + " regular and " + guaranteed + " guaranteed spots" : first;
			}
		}
		TowerPlan legacy = TowerPlan.legacy(SEED, 7);
		TowerTestSupport.result("chestSpots", "plans=" + plans + " fewestRegular=" + fewest + " nearestByWalk=" + nearest + " guaranteedSpots=" + guaranteedFloors + " faults=" + faults
			+ " guaranteedFaults=" + guaranteedFaults + " legacySpots=" + legacy.chestSpots().size() + " first=" + first);
		helper.assertTrue(faults == 0 && guaranteedFaults == 0, faults + " spots or floors break the rules, first: " + first);
		helper.assertTrue(guaranteedFloors == 3000 / TowerElites.ELITE_EVERY, "there are " + guaranteedFloors + " guaranteed spots over 3000 floors");
		helper.assertTrue(legacy.chestSpots().isEmpty(), "a legacy plan has chest spots");
		helper.succeed();
	}

	private static String spotFault(final TowerPlan plan, final TowerPlan.ChestSpot spot, final int[][] walk, final List<TowerTraps.Trap> traps, final List<TowerPlan.ChestSpot> all) {
		net.minecraft.core.Direction face = spot.facing();
		for (TowerPlan.Spot cell : spot.cells()) {
			if (cell.x() < 1 || cell.z() < 1 || cell.x() > S - 2 || cell.z() > S - 2 || plan.wall(cell.x(), cell.z())) {
				return "a block is not open ground";
			}
			if (!plan.wall(cell.x() - face.getStepX(), cell.z() - face.getStepZ())) {
				return "no wall behind a block";
			}
			if (plan.wall(cell.x() + face.getStepX(), cell.z() + face.getStepZ())) {
				return "the front of a block is shut";
			}
			if (walk[cell.x()][cell.z()] < TowerChestSpots.STAGES[0][0]) {
				return "a block is " + walk[cell.x()][cell.z()] + " steps from the arrival";
			}
			if (plan.inSafeZone(cell.x(), cell.z())) {
				return "in a safe zone";
			}
			if (plan.hall() > 0 && Math.abs(cell.x() - S / 2) <= plan.hall() / 2 + 1 && Math.abs(cell.z() - S / 2) <= plan.hall() / 2 + 1) {
				return "in the hall";
			}
			if (Math.max(Math.abs(cell.x() - plan.portalX()), Math.abs(cell.z() - plan.portalZ())) <= TowerChestSpots.PORTAL_CLEAR) {
				return "in the portal's approach";
			}
			for (TowerTraps.Trap trap : traps) {
				if (Math.max(Math.abs(cell.x() - trap.x()), Math.abs(cell.z() - trap.z())) <= TowerChestSpots.TRAP_CLEAR) {
					return "beside a trap";
				}
			}
			for (TowerPlan.ChestSpot other : all) {
				if (other != spot && !other.guaranteed() && other.cells().contains(cell)) {
					return "overlaps another spot";
				}
			}
		}
		return null;
	}

	private static String guaranteedFault(final TowerPlan plan, final TowerPlan.ChestSpot spot) {
		boolean boss = plan.floor() % TowerElites.BOSS_EVERY == 0;
		TowerMaze.Zone zone = plan.safeZones().get(boss ? 1 : 0);
		net.minecraft.core.Direction face = spot.facing();
		for (TowerPlan.Spot cell : spot.cells()) {
			if (!zone.contains(cell.x(), cell.z()) || plan.wall(cell.x(), cell.z()) || plan.wall(cell.x() + face.getStepX(), cell.z() + face.getStepZ())) {
				return "not in the " + (boss ? "boss room" : "entrance zone") + " on open ground";
			}
			if (boss && (plan.isRing(cell.x(), cell.z()) || plan.isRing(cell.x() + face.getStepX(), cell.z() + face.getStepZ()))) {
				return "on the ring";
			}
			if (boss) {
				for (TowerPlan.Gate gate : plan.gates()) {
					for (TowerPlan.Spot gateCell : gate.cells()) {
						if (gateCell.x() == cell.x() || gateCell.z() == cell.z()) {
							return "on the line between the openings";
						}
					}
				}
			}
		}
		return null;
	}
}
