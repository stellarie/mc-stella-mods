package com.stella.tower;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/** Every biome: it comes about as often as the others, it draws a walkable floor, it is built as planned, and it stands still. */
public class TowerBiomeSetGameTest {
	private static final long SEED = 0x5E7B_10E5_0002L;

	private static List<Integer> floorsOf(final long seed, final FloorBiome biome, final int count) {
		List<Integer> floors = new ArrayList<>();
		for (int floor = 2; floors.size() < count && floor < 100_000; floor++) {
			if (FloorBiomes.of(seed, floor) == biome) {
				floors.add(floor);
			}
		}
		return floors;
	}

	/** Catches a draw that favours a biome, a start that is not gentle, and two floors in a row of one look. */
	@GameTest
	public void everyBiomeComesAboutAsOftenAsTheOthersAndNeverTwiceInARow(final GameTestHelper helper) {
		int floors = 6000;
		Map<FloorBiome, Integer> counts = new EnumMap<>(FloorBiome.class);
		int adjacent = 0;
		FloorBiome before = null;
		for (int floor = 2; floor < 2 + floors; floor++) {
			FloorBiome biome = FloorBiomes.of(SEED, floor);
			counts.merge(biome, 1, Integer::sum);
			adjacent += biome == before ? 1 : 0;
			before = biome;
		}
		double share = 1.0 / FloorBiome.values().length;
		double sigma = Math.sqrt(floors * share * (1 - share));
		StringBuilder off = new StringBuilder();
		for (FloorBiome biome : FloorBiome.DRAWN) {
			// A block holds each biome once, so the count is steadier than chance's; the bound is chance's.
			if (Math.abs(counts.getOrDefault(biome, 0) - floors * share) > 4 * sigma) {
				off.append(biome).append('=').append(counts.getOrDefault(biome, 0)).append(' ');
			}
		}
		int twoBack = 0;
		if (FloorBiome.values().length >= 6) {
			for (int floor = 4; floor < 2 + floors; floor++) {
				twoBack += FloorBiomes.of(SEED, floor) == FloorBiomes.of(SEED, floor - 2) ? 1 : 0;
			}
		}
		boolean gentle = true;
		for (long seed = 0; seed < 50; seed++) {
			gentle &= FloorBiomes.of(seed, 1).gentle();
		}
		TowerTestSupport.result("biomeShare", "floors=" + floors + " counts=" + counts + " adjacent=" + adjacent + " gentleStart=" + gentle);
		helper.assertTrue(off.length() == 0, "biomes off by more than 4 sigma: " + off);
		helper.assertTrue(adjacent == 0, adjacent + " floors share the biome of the floor below");
		helper.assertTrue(twoBack == 0, twoBack + " floors share the biome of the floor two below");
		helper.assertTrue(gentle, "floor 1 drew a biome that is not a gentle one");
		helper.assertTrue(counts.size() == FloorBiome.values().length, "only " + counts.keySet() + " came up");
		helper.succeed();
	}

	/** Catches a biome whose draw leaves the portal out of reach, or falls back to the plain yard. */
	@GameTest(maxTicks = 100)
	public void everyBiomeDrawsAWalkableFloor(final GameTestHelper helper) {
		int per = 60;
		StringBuilder report = new StringBuilder();
		int stuck = 0;
		int fallbacks = 0;
		int wrong = 0;
		for (FloorBiome biome : FloorBiome.DRAWN) {
			int own = 0;
			int myStuck = 0;
			int myFallbacks = 0;
			for (int floor : floorsOf(SEED, biome, per)) {
				TowerPlan plan = BiomeGen.plan(SEED, floor);
				TowerVolume volume = TowerVolume.of(plan);
				own++;
				wrong += plan.biome().biome() != biome ? 1 : 0;
				myStuck += volume.reaches(plan) ? 0 : 1;
				myFallbacks += plan.attempt() == TowerPlan.FALLBACK ? 1 : 0;
			}
			stuck += myStuck;
			fallbacks += myFallbacks;
			report.append(biome).append(" floors=").append(own).append(" stuck=").append(myStuck).append(" fallbacks=").append(myFallbacks).append("; ");
		}
		TowerTestSupport.result("biomeDraws", report.toString());
		helper.assertTrue(wrong == 0, wrong + " plans carry another biome than the one asked");
		helper.assertTrue(stuck == 0, stuck + " plans have no walk to the portal: " + report);
		helper.assertTrue(fallbacks * 200 <= per * FloorBiome.values().length, fallbacks + " plans fell back to the plain yard: " + report);
		helper.succeed();
	}

	/** Catches a marsh whose spine is not a boardwalk: the road from the arrival is planks. */
	@GameTest(maxTicks = 100)
	public void theMarshsRoadIsABoardwalk(final GameTestHelper helper) {
		int planks = 0;
		int floors = 0;
		for (int floor : floorsOf(SEED, FloorBiome.MISTBOUND_MARSH, 5)) {
			TowerPlan plan = BiomeGen.plan(SEED, floor);
			floors++;
			byte[][] surface = plan.biome().surface();
			int own = 0;
			for (int x = 0; x < TowerLayout.SIZE; x++) {
				for (int z = 0; z < TowerLayout.SIZE; z++) {
					own += surface[x][z] == BiomeBlocks.PLANKS ? 1 : 0;
				}
			}
			planks += own >= 100 ? 1 : 0;
		}
		TowerTestSupport.result("marshBoardwalk", "floors=" + floors + " withBoardwalk=" + planks);
		helper.assertTrue(floors > 0 && planks == floors, planks + " of " + floors + " marsh floors have a boardwalk of 100 planks or more");
		helper.succeed();
	}

	/** Catches a closed biome with headroom under three blocks, a cave of lost pockets, or a roof that is not rock over a body. */
	@GameTest(maxTicks = 200)
	public void theBiomesUnderRockAreClosedAndOneConnectedCave(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		StringBuilder report = new StringBuilder();
		int tight = 0;
		int lost = 0;
		int roofs = 0;
		int enclosed = 0;
		for (FloorBiome biome : FloorBiome.DRAWN) {
			if (biome.openRoof()) {
				continue;
			}
			enclosed++;
			int columns = 0;
			int reached = 0;
			for (int floor : floorsOf(SEED, biome, 12)) {
				TowerPlan plan = BiomeGen.plan(SEED, floor);
				TowerVolume volume = TowerVolume.of(plan);
				int[] dist = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
				for (int x = 0; x < TowerLayout.SIZE; x++) {
					for (int z = 0; z < TowerLayout.SIZE; z++) {
						int y = plan.standY(x, z);
						if (y < TowerWalk.LOW) {
							continue;
						}
						columns++;
						boolean reachable = dist[TowerWalk.index(x, y, z)] >= 0;
						reached += reachable ? 1 : 0;
						if (reachable) {
							// Rock over the head, not less than the walk's own two blocks and a lamp's.
							// A cave and a catacomb are shut by their own rock; a plain yard in their place is no cave.
							boolean shut = biome != FloorBiome.BASALT_DELTAS;
							tight += shut && (plan.biome().ceiling() == null || plan.biome().ceiling()[x][z] - y < 4) ? 1 : 0;
						}
					}
				}
			}
			// A cave or a catacomb is joined; the basalt's lava lakes leave islands, and its roof is the room's own.
			lost += FloorBiome.BASALT_DELTAS != biome && reached * 10 < columns * 9 ? 1 : 0;
			report.append(biome).append(" columns=").append(columns).append(" reached=").append(reached).append("; ");
			int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f) == biome);
			TowerFloors.ensure(tower, floor);
			TowerPlan built = TowerPlan.at(tower, floor);
			roofs += tower.getBlockState(built.origin().offset(30, built.roofY(), 30)).is(TowerBlocks.TOWER_WALL) ? 1 : 0;
		}
		TowerTestSupport.result("rockBiomes", report.toString() + "tight=" + tight + " lost=" + lost + " roofs=" + roofs + "/" + enclosed);
		helper.assertTrue(enclosed >= 3 && tight == 0, tight + " reachable spots have less than four blocks of rock or air over them: " + report);
		helper.assertTrue(lost == 0, lost + " rock biomes leave over a tenth of their ground out of reach: " + report);
		helper.assertTrue(roofs == enclosed, "only " + roofs + " of " + enclosed + " closed biomes have a wall for a roof");
		helper.succeed();
	}

	/** Catches a biome with a dark spot: every spot a body reaches has a light within six blocks. */
	@GameTest(maxTicks = 100)
	public void everyBiomeIsLit(final GameTestHelper helper) {
		StringBuilder report = new StringBuilder();
		int dark = 0;
		for (FloorBiome biome : FloorBiome.DRAWN) {
			int own = 0;
			for (int floor : floorsOf(SEED, biome, 6)) {
				TowerPlan plan = BiomeGen.plan(SEED, floor);
				TowerVolume volume = TowerVolume.of(plan);
				List<int[]> lamps = new ArrayList<>();
				for (int x = 0; x < TowerLayout.SIZE; x++) {
					for (int z = 0; z < TowerLayout.SIZE; z++) {
						for (int y = plan.low(); y < plan.roofY(); y++) {
							if (volume.kind(x, y, z) == TowerVolume.BIOME_BLOCK && BiomeBlocks.lights(volume.code(x, y, z))) {
								lamps.add(new int[] {x, y, z});
							}
						}
					}
				}
				int[] dist = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
				for (int x = 0; x < TowerLayout.SIZE; x++) {
					for (int z = 0; z < TowerLayout.SIZE; z++) {
						for (int y = TowerWalk.LOW; y <= TowerWalk.HIGH; y++) {
							if (dist[TowerWalk.index(x, y, z)] < 0) {
								continue;
							}
							boolean lit = false;
							for (int[] lamp : lamps) {
								int dx = lamp[0] - x;
								int dy = lamp[1] - y;
								int dz = lamp[2] - z;
								lit |= dx * dx + dy * dy + dz * dz <= BiomeGen.LIGHT_REACH * BiomeGen.LIGHT_REACH;
							}
							own += lit ? 0 : 1;
						}
					}
				}
			}
			dark += own;
			report.append(biome).append(" dark=").append(own).append("; ");
		}
		TowerTestSupport.result("biomeSetLight", report.toString());
		helper.assertTrue(dark == 0, "reachable spots with no light: " + report);
		helper.succeed();
	}

	/** Catches a built biome the plan's model disagrees with, an unlit portal, or a floor that is not painted. */
	@GameTest(maxTicks = 600)
	public void everyBiomeIsBuiltAsPlannedAndPainted(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		StringBuilder report = new StringBuilder();
		int disagree = 0;
		int stuck = 0;
		int unlit = 0;
		int unpainted = 0;
		for (FloorBiome biome : FloorBiome.DRAWN) {
			int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f) == biome);
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.at(tower, floor);
			TowerVolume volume = TowerVolume.of(plan);
			int[] model = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
			int[] world = TowerWalk.distances(TowerWalk.world(tower, floor), plan.arrivalX(), 0, plan.arrivalZ());
			int off = 0;
			for (int i = 0; i < model.length; i++) {
				off += (model[i] >= 0) != (world[i] >= 0) ? 1 : 0;
			}
			boolean reaches = false;
			for (int[] goal : TowerVolume.portalGoals(plan)) {
				reaches |= world[TowerWalk.index(goal[0], goal[1], goal[2])] >= 0;
			}
			int lit = 0;
			for (BlockPos inside : plan.portalInside()) {
				lit += tower.getBlockState(inside).is(TowerBlocks.TOWER_PORTAL) ? 1 : 0;
			}
			boolean painted = TowerBiomes.painted(tower, floor, biome);
			disagree += off;
			stuck += reaches ? 0 : 1;
			unlit += lit == plan.portalInside().size() ? 0 : 1;
			unpainted += painted ? 0 : 1;
			report.append(biome).append(" floor=").append(floor).append(" off=").append(off).append(" reaches=").append(reaches).append(" chests=")
				.append(plan.chestSpots().size()).append(" attempt=").append(plan.attempt()).append("; ");
		}
		TowerTestSupport.result("biomeSetBuilt", report.toString());
		helper.assertTrue(disagree == 0, "the models disagree with the built floors: " + report);
		helper.assertTrue(stuck == 0, stuck + " built biomes have no walk to the portal");
		helper.assertTrue(unlit == 0, unlit + " biomes have an unlit portal");
		helper.assertTrue(unpainted == 0, unpainted + " biomes are not painted with their own");
		helper.succeed();
	}

	/** Catches a floor that does not hold still: no water spills, no leaf decays, no grass dies, no fire spreads in 400 ticks. */
	@GameTest(maxTicks = 500)
	public void everyBiomeStandsStillFor400Ticks(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		List<BlockState[]> before = new ArrayList<>();
		List<TowerPlan> plans = new ArrayList<>();
		List<ChunkPos> forced = new ArrayList<>();
		// Two floors of each biome: a floor's trouble may lie in its draw.
		for (FloorBiome biome : FloorBiome.DRAWN) {
			for (int copy = 0; copy < 2; copy++) {
			int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f) == biome);
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.at(tower, floor);
			BlockPos origin = plan.origin();
			for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + TowerLayout.SIZE) >> 4; cx++) {
				for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + TowerLayout.SIZE) >> 4; cz++) {
					tower.setChunkForced(cx, cz, true);
					forced.add(new ChunkPos(cx, cz));
				}
			}
			plans.add(plan);
			before.add(snapshot(tower, plan));
			}
		}
		helper.runAfterDelay(400, () -> {
			StringBuilder report = new StringBuilder();
			int changed = 0;
			for (int i = 0; i < plans.size(); i++) {
				TowerPlan plan = plans.get(i);
				BlockState[] then = before.get(i);
				BlockState[] now = snapshot(tower, plan);
				int own = 0;
				String first = "";
				BlockPos origin = plan.origin();
				int span = plan.roofY() - plan.low() + 1;
				for (int k = 0; k < now.length; k++) {
					if (now[k] != then[k]) {
						if (own == 0) {
							int y = k % span + plan.low();
							int z = k / span % TowerLayout.SIZE;
							int x = k / span / TowerLayout.SIZE;
							first = then[k] + " -> " + now[k] + " at " + x + "," + y + "," + z + " above " + (k + 1 < now.length ? then[k + 1] : "-")
								+ " beside " + around(then, k, span);
						}
						own++;
					}
				}
				changed += own;
				report.append(plan.biome().biome()).append(" changed=").append(own).append(own == 0 ? "" : " first " + first).append("; ");
			}
			for (ChunkPos chunk : forced) {
				tower.setChunkForced(chunk.x(), chunk.z(), false);
			}
			TowerTestSupport.result("biomeStill", report.toString());
			helper.assertTrue(changed == 0, "blocks moved on their own: " + report);
			helper.succeed();
		});
	}

	/** The four neighbours of a cell of a snapshot, for a report. */
	private static String around(final BlockState[] states, final int k, final int span) {
		StringBuilder text = new StringBuilder();
		int[] steps = {span, -span, span * TowerLayout.SIZE, -span * TowerLayout.SIZE};
		for (int step : steps) {
			text.append(k + step >= 0 && k + step < states.length ? states[k + step].getBlock().toString() : "-").append(' ');
		}
		return text.toString();
	}

	private static BlockState[] snapshot(final ServerLevel tower, final TowerPlan plan) {
		int span = plan.roofY() - plan.low() + 1;
		BlockState[] states = new BlockState[TowerLayout.SIZE * TowerLayout.SIZE * span];
		BlockPos origin = plan.origin();
		int k = 0;
		for (int x = 0; x < TowerLayout.SIZE; x++) {
			for (int z = 0; z < TowerLayout.SIZE; z++) {
				for (int y = plan.low(); y <= plan.roofY(); y++) {
					BlockState state = tower.getBlockState(origin.offset(x, y, z));
					// A cactus ages in place; its age is no block moving.
					states[k++] = state.is(net.minecraft.world.level.block.Blocks.CACTUS) ? net.minecraft.world.level.block.Blocks.CACTUS.defaultBlockState() : state;
				}
			}
		}
		return states;
	}
}
