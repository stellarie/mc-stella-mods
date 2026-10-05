package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Acceptance items 1 and 2, and the plan's own promises: bounds, markers, a walkable route, the gate and pillar rules,
 * and the floor features, braziers and grates the sketch review added.
 */
public class ProvingLayoutGameTest {
	static final int SEEDS = 16;
	static final int WIDE_SEEDS = 200;
	static final BlockPos ORIGIN = new BlockPos(1000, 64, 1000);

	static RuinFrame frame(final long seed, final Rotation rotation) {
		return new RuinFrame(ProvingGroundsSite.ID, ORIGIN, rotation.ordinal(), seed, ArenaPlan.SIZE, ProvingGroundsSite.HEIGHT, ArenaPlan.SIZE, ProvingGroundsSite.SLOPE);
	}

	static ArenaPlan planOf(final long seed) {
		return ArenaLayout.plan(LayoutContext.forDryRun(frame(seed, Rotation.NONE)));
	}

	private static int planned(final ArenaPlan plan) {
		int planned = 0;
		for (int y = -ArenaPlan.DEPTH; y <= ArenaPlan.TOP; y++) {
			for (int z = 0; z < ArenaPlan.SIZE; z++) {
				for (int x = 0; x < ArenaPlan.SIZE; x++) {
					planned += plan.at(x, y, z) == null ? 0 : 1;
				}
			}
		}
		return planned;
	}

	@GameTest(maxTicks = 600)
	public void theArenaStandsWholeOverSixteenSeedsAndAllFourTurns(final GameTestHelper helper) {
		int whole = 0;
		int[] shapes = new int[2];
		List<String> broken = new ArrayList<>();
		for (long i = 1; i <= SEEDS; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			shapes[plan.params().shape()]++;
			int planned = planned(plan);
			for (Rotation rotation : Rotation.values()) {
				RuinFrame frame = frame(seed, rotation);
				Map<BlockPos, BlockState> map = new HashMap<>();
				LayoutContext ctx = LayoutContext.forCapture(frame, map, frame.box());
				ArenaLayout.draw(ctx);
				BlockPos core = ctx.markers().get(RuinMarkers.CORE).get(0);
				BlockPos vault = ctx.markers().get(RuinMarkers.VAULT).get(0);
				BlockPos gong = ctx.markers().get(ArenaPlan.GONG).get(0);
				boolean lecterns = true;
				for (int tier = 1; tier <= 3; tier++) {
					BlockPos lectern = ctx.markers().get(ArenaPlan.LECTERN + tier).get(0);
					lecterns &= map.get(lectern).is(Blocks.LECTERN);
				}
				long floor = map.entrySet().stream().filter(e -> e.getKey().getY() == frame.min().getY() && !e.getValue().isAir()).count();
				boolean ok = map.get(core).is(RuinBlocks.CORE)
					&& map.get(vault).is(RuinBlocks.VAULT)
					&& map.get(gong).is(Blocks.BELL)
					&& lecterns
					&& floor >= 1200
					&& map.size() == planned
					&& map.keySet().stream().allMatch(frame.box()::isInside);
				if (ok) {
					whole++;
				} else {
					broken.add("seed " + seed + " turn " + rotation + " core=" + map.get(core) + " vault=" + map.get(vault) + " gong=" + map.get(gong) + " lecterns=" + lecterns + " floor=" + floor + " written=" + map.size() + " planned=" + planned);
				}
			}
		}
		ProvingTestSupport.result("standsWhole", "seeds=" + SEEDS + " turns=4 whole=" + whole + " round=" + shapes[0] + " octagon=" + shapes[1]);
		helper.assertTrue(shapes[0] > 0 && shapes[1] > 0, "the " + SEEDS + " seeds did not cover both shapes: " + java.util.Arrays.toString(shapes));
		helper.assertTrue(broken.isEmpty(), broken.size() + " arenas do not stand whole, first: " + (broken.isEmpty() ? "none" : broken.get(0)));
		helper.assertTrue(whole == SEEDS * 4, "only " + whole + " of " + SEEDS * 4 + " arenas stand whole");
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void theSameSeedDrawsTheSameBlocksInOneBoxOrChunkByChunk(final GameTestHelper helper) {
		int differing = 0;
		for (long seed : new long[] {ProvingTestSupport.seed(3), ProvingTestSupport.seed(7), ProvingTestSupport.seed(11), ProvingTestSupport.seed(19)}) {
			RuinFrame frame = frame(seed, Rotation.CLOCKWISE_90);
			Map<BlockPos, BlockState> whole = new HashMap<>();
			ArenaLayout.draw(LayoutContext.forCapture(frame, whole, frame.box()));
			Map<BlockPos, BlockState> pieces = new HashMap<>();
			BoundingBox box = frame.box();
			int chunks = 0;
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					BoundingBox clip = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
					ArenaLayout.draw(LayoutContext.forCapture(frame, pieces, clip));
					chunks++;
				}
			}
			helper.assertTrue(chunks >= 9, "the arena fits in " + chunks + " chunks, so the check proves little");
			differing += whole.equals(pieces) ? 0 : 1;
		}
		ProvingTestSupport.result("chunkByChunk", "seeds=4 differing=" + differing);
		helper.assertTrue(differing == 0, differing + " seeds drew different blocks chunk by chunk");
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void shapeRadiusPillarsDecayGatesFeaturesAndBannersVaryOverTwoHundredSeeds(final GameTestHelper helper) {
		int[] shapes = new int[2];
		Set<Integer> radii = new HashSet<>();
		Set<Integer> pillarCounts = new HashSet<>();
		Set<Integer> gateCounts = new HashSet<>();
		Set<Integer> gateSlots = new HashSet<>();
		Set<Integer> featureKinds = new HashSet<>();
		Set<Integer> featureCounts = new HashSet<>();
		Set<Integer> dyes = new HashSet<>();
		Set<String> pillarPlans = new HashSet<>();
		Set<String> gatePlans = new HashSet<>();
		int minDecay = 100;
		int maxDecay = 0;
		int minPillars = 99;
		int minFeatures = 99;
		int decayedTotal = 0;
		for (long i = 1; i <= WIDE_SEEDS; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			ArenaPlan.Params params = plan.params();
			shapes[params.shape()]++;
			radii.add(params.radius());
			pillarCounts.add(plan.pillarList().size());
			minPillars = Math.min(minPillars, plan.pillarList().size());
			gateCounts.add(plan.gateSlots().size());
			plan.gateSlots().forEach(gateSlots::add);
			plan.featureList().forEach(feature -> featureKinds.add(feature.kind()));
			featureCounts.add(plan.featureList().size());
			minFeatures = Math.min(minFeatures, plan.featureList().size());
			dyes.add(params.mainDye());
			minDecay = Math.min(minDecay, params.decayPercent());
			maxDecay = Math.max(maxDecay, params.decayPercent());
			pillarPlans.add(plan.pillarList().toString());
			gatePlans.add(plan.gateSlots().toString());
			decayedTotal += plan.mossy() + plan.cracked() + plan.air();
		}
		ProvingTestSupport.result("shapesAppear", "seeds=" + WIDE_SEEDS + " shapes=" + java.util.Arrays.toString(shapes) + " radii=" + radii + " decay=" + minDecay + ".." + maxDecay + " gateCounts=" + gateCounts
			+ " gateSlots=" + gateSlots + " pillarCounts=" + pillarCounts + " pillarPlans=" + pillarPlans.size() + " gatePlans=" + gatePlans.size() + " featureKinds=" + featureKinds + " featureCounts=" + featureCounts + " dyes=" + dyes.size());
		helper.assertTrue(shapes[0] >= WIDE_SEEDS / 4 && shapes[1] >= WIDE_SEEDS / 4, "the shapes were " + java.util.Arrays.toString(shapes));
		helper.assertTrue(radii.equals(Set.of(16, 17, 18, 19, 20)), "the radii were " + radii);
		helper.assertTrue(minDecay == ArenaPlan.DECAY_MIN && maxDecay == ArenaPlan.DECAY_MAX, "the decay rate ran " + minDecay + ".." + maxDecay);
		helper.assertTrue(gateCounts.equals(Set.of(2, 3, 4)), "the gate counts were " + gateCounts);
		helper.assertTrue(gateSlots.equals(Set.of(0, 1, 2, 3, 5, 6, 7)), "the gate slots were " + gateSlots + ": never the loge slot, always the main gate");
		helper.assertTrue(pillarCounts.equals(Set.of(4, 5, 6, 7, 8)), "the pillar counts were " + pillarCounts);
		helper.assertTrue(minPillars >= ArenaPlan.PILLARS_MIN, "a seed placed only " + minPillars + " pillars");
		helper.assertTrue(pillarPlans.size() >= WIDE_SEEDS * 9 / 10, "only " + pillarPlans.size() + " distinct pillar plans in " + WIDE_SEEDS + " seeds");
		helper.assertTrue(gatePlans.size() >= 20, "only " + gatePlans.size() + " distinct gate plans");
		helper.assertTrue(featureKinds.equals(Set.of(0, 1, 2)) && featureCounts.equals(Set.of(1, 2, 3)), "the floor features were " + featureKinds + " in counts " + featureCounts);
		helper.assertTrue(minFeatures >= ArenaPlan.FEATURES_MIN, "a seed placed only " + minFeatures + " floor features");
		helper.assertTrue(dyes.size() >= 12, "only " + dyes.size() + " banner colors");
		helper.assertTrue(decayedTotal > 0, "no block decayed in " + WIDE_SEEDS + " seeds");
		helper.succeed();
	}

	/** Standing spots: a solid block below and two free blocks above. */
	private static boolean solid(final BlockState state) {
		return state != null && !state.isAir() && !state.is(BlockTags.BANNERS);
	}

	private static List<Integer> stands(final ArenaPlan plan, final int x, final int z) {
		List<Integer> spots = new ArrayList<>();
		for (int y = -ArenaPlan.DEPTH + 1; y < ArenaPlan.TOP - 1; y++) {
			if (solid(plan.at(x, y - 1, z)) && !solid(plan.at(x, y, z)) && !solid(plan.at(x, y + 1, z))) {
				spots.add(y);
			}
		}
		return spots;
	}

	/** Every column a walker reaches from the south approach, stepping up one block or down three. */
	static boolean[] reach(final ArenaPlan plan) {
		boolean[] seen = new boolean[ArenaPlan.SIZE * ArenaPlan.SIZE];
		Set<Long> states = new HashSet<>();
		int startZ = ArenaPlan.CENTER - plan.params().radius() - 3;
		List<Integer> first = stands(plan, ArenaPlan.CENTER, startZ);
		if (first.isEmpty()) {
			return seen;
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {ArenaPlan.CENTER, startZ, first.get(0)});
		seen[startZ * ArenaPlan.SIZE + ArenaPlan.CENTER] = true;
		states.add(key(ArenaPlan.CENTER, startZ, first.get(0)));
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			for (int[] s : steps) {
				int nx = c[0] + s[0];
				int nz = c[1] + s[1];
				if (nx < 0 || nz < 0 || nx >= ArenaPlan.SIZE || nz >= ArenaPlan.SIZE) {
					continue;
				}
				for (int y : stands(plan, nx, nz)) {
					if (y - c[2] > 1 || c[2] - y > 3 || !states.add(key(nx, nz, y))) {
						continue;
					}
					seen[nz * ArenaPlan.SIZE + nx] = true;
					queue.add(new int[] {nx, nz, y});
				}
			}
		}
		return seen;
	}

	private static long key(final int x, final int z, final int y) {
		return ((long) y + 64L) * 10_000L + (long) z * 100L + x;
	}

	private static boolean touches(final boolean[] seen, final BlockPos spot) {
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] s : steps) {
			int x = spot.getX() + s[0];
			int z = spot.getZ() + s[1];
			if (x >= 0 && z >= 0 && x < ArenaPlan.SIZE && z < ArenaPlan.SIZE && seen[z * ArenaPlan.SIZE + x]) {
				return true;
			}
		}
		return false;
	}

	@GameTest(maxTicks = 1200)
	public void aWalkerFromTheSouthGateReachesEveryLecternTheCoreTheVaultAndTheArenaOverTwoHundredSeeds(final GameTestHelper helper) {
		int bad = 0;
		double worst = 1.0;
		String first = "none";
		for (long i = 1; i <= WIDE_SEEDS; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			boolean[] seen = reach(plan);
			List<BlockPos> goals = new ArrayList<>();
			for (int tier = 1; tier <= 3; tier++) {
				goals.add(plan.markers(ArenaPlan.LECTERN + tier).get(0).local());
			}
			goals.add(plan.core());
			goals.add(plan.vault());
			boolean ok = goals.stream().allMatch(goal -> touches(seen, goal));
			int floorCells = 0;
			int reached = 0;
			for (int z = 0; z < ArenaPlan.CELLS; z++) {
				for (int x = 0; x < ArenaPlan.CELLS; x++) {
					if (plan.ring(x, z) <= plan.params().radius() - 1 && solid(plan.at(x, 0, z)) && !solid(plan.at(x, 1, z))) {
						floorCells++;
						reached += seen[z * ArenaPlan.SIZE + x] ? 1 : 0;
					}
				}
			}
			worst = Math.min(worst, (double) reached / Math.max(1, floorCells));
			if (!ok && bad == 0) {
				first = "seed " + seed;
			}
			bad += ok ? 0 : 1;
		}
		ProvingTestSupport.result("walker", "seeds=" + WIDE_SEEDS + " bad=" + bad + " worstFloorShare=" + worst + " first=" + first);
		helper.assertTrue(bad == 0, bad + " seeds block the walk to a lectern, the core or the vault, first: " + first);
		helper.assertTrue(worst >= 0.9, "a walker reaches only " + worst + " of the arena floor");
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void everySeedNamesTheMarkersTheContestNeeds(final GameTestHelper helper) {
		int bad = 0;
		String first = "none";
		for (long i = 1; i <= WIDE_SEEDS; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			int gates = plan.gateSlots().size();
			boolean ok = plan.markers(ArenaPlan.SPAWN_PREFIX + GroundsMobs.GLADIATOR).size() == 8
				&& plan.markers(ArenaPlan.SPAWN_PREFIX + GroundsMobs.RETIARIUS).size() == 8
				&& plan.markers(ArenaPlan.SPAWN_PREFIX + GroundsMobs.HOUND).size() == 2 * (gates - 1)
				&& plan.markers(ArenaPlan.SPAWN_PREFIX + GroundsMobs.PIT_BEAST).size() == 1
				&& plan.markers(ArenaPlan.BRAZIER).size() == ArenaPlan.BRAZIERS
				&& plan.markers(ArenaPlan.GRATE + 1).size() == 4
				&& plan.markers(ArenaPlan.GRATE + 2).size() == 4
				&& plan.markers(ArenaPlan.PORTCULLIS).size() == 7
				&& plan.markers(ArenaPlan.GONG).size() == 1;
			if (!ok && bad == 0) {
				first = "seed " + seed + " markers " + plan.markers().stream().map(ArenaPlan.Marker::name).distinct().toList();
			}
			bad += ok ? 0 : 1;
		}
		helper.assertTrue(bad == 0, bad + " seeds miss a marker, first: " + first);
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void theSpawnMarkersStandOnFloorOrStandAndHaveAirAbove(final GameTestHelper helper) {
		int bad = 0;
		String first = "none";
		for (long i = 1; i <= WIDE_SEEDS; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			for (ArenaPlan.Marker marker : plan.markers()) {
				if (!marker.name().startsWith(ArenaPlan.SPAWN_PREFIX)) {
					continue;
				}
				boolean ok = solid(plan.at(marker.x(), marker.y() - 1, marker.z()))
					&& !solid(plan.at(marker.x(), marker.y(), marker.z()))
					&& !solid(plan.at(marker.x(), marker.y() + 1, marker.z()));
				if (!ok && bad == 0) {
					first = "seed " + seed + " " + marker + " below=" + plan.at(marker.x(), marker.y() - 1, marker.z()) + " at=" + plan.at(marker.x(), marker.y(), marker.z());
				}
				bad += ok ? 0 : 1;
			}
		}
		helper.assertTrue(bad == 0, bad + " spawn markers have no footing or no room, first: " + first);
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void theWallKeepsItsLowTwoCoursesSoNoMobWalksThroughItExceptAtAGate(final GameTestHelper helper) {
		int open = 0;
		for (long i = 1; i <= WIDE_SEEDS; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			int radius = plan.params().radius();
			for (int z = 0; z < ArenaPlan.CELLS; z++) {
				for (int x = 0; x < ArenaPlan.CELLS; x++) {
					if (plan.ring(x, z) != radius + 1) {
						continue;
					}
					boolean low = solid(plan.at(x, 1, z)) && solid(plan.at(x, 2, z));
					boolean gate = !solid(plan.at(x, 1, z)) || plan.at(x, 1, z).is(Blocks.IRON_BARS);
					open += low || gate ? 0 : 1;
				}
			}
		}
		helper.assertTrue(open == 0, open + " wall cells lost a low course to decay");
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void theMainGateStaysOpenAndEveryExtraGateIsBarred(final GameTestHelper helper) {
		for (long i = 1; i <= 40; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			int r = plan.params().radius();
			int wallZ = ArenaPlan.CENTER - (r + 1);
			for (int x = 20; x <= 26; x++) {
				helper.assertTrue(!solid(plan.at(x, 1, wallZ)) && !solid(plan.at(x, 2, wallZ)) && !solid(plan.at(x, 3, wallZ)), "seed " + seed + ": the main gate is closed at x" + x);
			}
			for (int slot : plan.gateSlots()) {
				if (slot == 0) {
					continue;
				}
				double[] u = ArenaPlan.SLOTS[slot];
				int bars = 0;
				for (int z = 0; z < ArenaPlan.CELLS; z++) {
					for (int x = 0; x < ArenaPlan.CELLS; x++) {
						double dx = x - ArenaPlan.CENTER;
						double dz = z - ArenaPlan.CENTER;
						boolean inBand = dx * u[0] + dz * u[1] > 0 && Math.abs(-dx * u[1] + dz * u[0]) <= 2.5;
						if (inBand && plan.ring(x, z) == r + 1 && plan.at(x, 1, z) != null && plan.at(x, 1, z).is(Blocks.IRON_BARS)) {
							bars++;
						}
					}
				}
				helper.assertTrue(bars >= 3, "seed " + seed + ": gate " + ArenaPlan.SLOT_NAMES[slot] + " has only " + bars + " barred wall cells");
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void coverPillarsKeepOutOfThePitTheStageTheLogeTheCenterLaneAndEachOther(final GameTestHelper helper) {
		int bad = 0;
		String first = "none";
		for (long s = 1; s <= WIDE_SEEDS; s++) {
			long seed = ProvingTestSupport.seed(s);
			ArenaPlan plan = planOf(seed);
			int r = plan.params().radius();
			List<ArenaPlan.Pillar> pillars = plan.pillarList();
			for (int i = 0; i < pillars.size(); i++) {
				ArenaPlan.Pillar p = pillars.get(i);
				boolean ok = p.height() >= 3 && p.height() <= 5
					&& Math.abs(p.x() + 0.5 - ArenaPlan.CENTER) >= 3.0
					&& !(p.x() + 0.5 + 2.0 >= ArenaPlan.PIT_X0 && p.x() + 0.5 - 2.0 <= ArenaPlan.PIT_X1 + 1 && p.z() + 0.5 + 2.0 >= ArenaPlan.PIT_Z0 && p.z() + 0.5 - 2.0 <= ArenaPlan.PIT_Z1 + 1)
					&& Math.hypot(p.x() + 0.5 - ArenaPlan.CENTER, p.z() + 0.5 - (ArenaPlan.CENTER - r + 2)) >= 7.5
					&& Math.hypot(p.x() + 0.5 - ArenaPlan.CENTER, p.z() + 0.5 - (ArenaPlan.CENTER + r - 2)) >= 7.5
					&& ArenaPlan.metric(plan.params().shape(), p.x() + 0.5 - ArenaPlan.CENTER, p.z() + 0.5 - ArenaPlan.CENTER) <= r - 4;
				for (int j = i + 1; j < pillars.size(); j++) {
					ok &= Math.hypot(p.x() - pillars.get(j).x(), p.z() - pillars.get(j).z()) >= 5.0;
				}
				if (!ok && bad == 0) {
					first = "seed " + seed + " pillar " + p;
				}
				bad += ok ? 0 : 1;
			}
		}
		helper.assertTrue(bad == 0, bad + " pillars break a placement rule, first: " + first);
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void braziersStandUnlitOnTheWallAndEachGrateIsATwoByTwoOverNetherrack(final GameTestHelper helper) {
		for (long i = 1; i <= 40; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			int r = plan.params().radius();
			for (ArenaPlan.Marker brazier : plan.markers(ArenaPlan.BRAZIER)) {
				BlockState state = plan.at(brazier.x(), brazier.y(), brazier.z());
				helper.assertTrue(state != null && state.is(Blocks.CAMPFIRE) && !state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT), "seed " + seed + ": brazier is " + state);
				helper.assertTrue(plan.ring(brazier.x(), brazier.z()) == r + 1 && solid(plan.at(brazier.x(), brazier.y() - 1, brazier.z())), "seed " + seed + ": a brazier floats or leaves the wall ring");
			}
			for (int g = 1; g <= 2; g++) {
				List<ArenaPlan.Marker> cells = plan.markers(ArenaPlan.GRATE + g);
				int minX = cells.stream().mapToInt(ArenaPlan.Marker::x).min().orElseThrow();
				int maxX = cells.stream().mapToInt(ArenaPlan.Marker::x).max().orElseThrow();
				int minZ = cells.stream().mapToInt(ArenaPlan.Marker::z).min().orElseThrow();
				int maxZ = cells.stream().mapToInt(ArenaPlan.Marker::z).max().orElseThrow();
				helper.assertTrue(maxX - minX == 1 && maxZ - minZ == 1, "seed " + seed + ": grate " + g + " is not 2 by 2");
				for (ArenaPlan.Marker cell : cells) {
					helper.assertTrue(plan.at(cell.x(), 0, cell.z()).is(Blocks.IRON_TRAPDOOR) && plan.at(cell.x(), -2, cell.z()).is(Blocks.NETHERRACK) && plan.at(cell.x(), -1, cell.z()).isAir(), "seed " + seed + ": grate " + g + " lacks its trapdoor over netherrack");
				}
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void theCoreStandsOnTheStageBehindTheMiddleLecternAndTheVaultSitsInTheLoge(final GameTestHelper helper) {
		for (long i = 1; i <= 40; i++) {
			long seed = ProvingTestSupport.seed(i);
			ArenaPlan plan = planOf(seed);
			int r = plan.params().radius();
			BlockPos core = plan.core();
			BlockPos middle = plan.markers(ArenaPlan.LECTERN + 2).get(0).local();
			helper.assertTrue(core.getX() == middle.getX() && core.getZ() == middle.getZ() + 1 && core.getY() == middle.getY(), "seed " + seed + ": core " + core + " is not behind the middle lectern " + middle);
			helper.assertTrue(plan.vault().getZ() == ArenaPlan.CENTER + r + 2 && plan.at(plan.vault().getX(), plan.vault().getY() - 1, plan.vault().getZ()).is(Blocks.CHISELED_STONE_BRICKS), "seed " + seed + ": the vault is not on the loge floor");
			helper.assertTrue(plan.at(ArenaPlan.CENTER, 0, ArenaPlan.CENTER).is(Blocks.IRON_TRAPDOOR) && plan.at(ArenaPlan.CENTER, -3, ArenaPlan.CENTER).is(Blocks.BLACKSTONE), "seed " + seed + ": the pit hatch is missing");
		}
		helper.succeed();
	}

	/** The seeds draw the same arena in the same order, so the stream a layout reads is the stream a test replays. */
	@GameTest
	public void theParamsRollsAreStableForTheSameSeed(final GameTestHelper helper) {
		helper.assertTrue(ArenaPlan.Params.roll(RandomSource.create(42L)).equals(ArenaPlan.Params.roll(RandomSource.create(42L))), "the same seed rolled two arenas");
		helper.succeed();
	}
}
