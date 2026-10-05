package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Acceptance items 1 and 2, and the plan's own promises: bounds, markers, a walkable route, the obelisk, and the blocks it names. */
public class WayshrineLayoutGameTest {
	static final int SEEDS = 24;
	static final int WIDE_SEEDS = 300;
	static final BlockPos ORIGIN = new BlockPos(1000, 64, 1000);

	static RuinFrame frame(final long seed, final Rotation rotation) {
		return new RuinFrame(WayshrineSite.ID, ORIGIN, rotation.ordinal(), seed, WayshrinePlan.SIZE, 16, WayshrinePlan.SIZE, WayshrineSite.SLOPE);
	}

	static WayshrinePlan planOf(final long seed) {
		return WayshrineLayout.plan(LayoutContext.forDryRun(frame(seed, Rotation.NONE)));
	}

	@GameTest(maxTicks = 400)
	public void theShrineStandsWholeOverTwentyFourSeedsAndAllFourTurns(final GameTestHelper helper) {
		int whole = 0;
		int[] shapes = new int[WayshrinePlan.VARIANTS];
		List<String> broken = new ArrayList<>();
		for (long seed = 1; seed <= SEEDS; seed++) {
			WayshrinePlan plan = planOf(seed);
			shapes[plan.params().variant()]++;
			int planned = 0;
			for (int y = -WayshrinePlan.DEPTH; y <= WayshrinePlan.TOP; y++) {
				for (int z = 0; z < WayshrinePlan.SIZE; z++) {
					for (int x = 0; x < WayshrinePlan.SIZE; x++) {
						planned += plan.at(x, y, z) == null ? 0 : 1;
					}
				}
			}
			for (Rotation rotation : Rotation.values()) {
				RuinFrame frame = frame(seed, rotation);
				Map<BlockPos, BlockState> map = new HashMap<>();
				LayoutContext ctx = LayoutContext.forCapture(frame, map, frame.box());
				WayshrineLayout.draw(ctx);
				BlockPos core = ctx.markers().get(RuinMarkers.CORE).get(0);
				BlockPos vault = ctx.markers().get(RuinMarkers.VAULT).get(0);
				long floor = map.entrySet().stream().filter(e -> e.getKey().getY() == frame.min().getY() && !e.getValue().isAir()).count();
				boolean ok = map.get(core).is(RuinBlocks.CORE)
					&& map.get(vault).is(RuinBlocks.VAULT)
					&& map.get(core.below()).is(plan.palette().accent().getBlock())
					&& map.get(frame.toWorld(10, 0, 0)).is(Blocks.DIRT_PATH)
					&& floor >= 120
					&& map.size() == planned
					&& map.keySet().stream().allMatch(frame.box()::isInside);
				if (ok) {
					whole++;
				} else {
					broken.add("seed " + seed + " turn " + rotation + " core=" + map.get(core) + " vault=" + map.get(vault) + " floor=" + floor + " written=" + map.size() + " planned=" + planned);
				}
			}
		}
		WayshrineTestSupport.result("standsWhole", "seeds=" + SEEDS + " turns=4 whole=" + whole + " ring=" + shapes[0] + " chapel=" + shapes[1] + " sunken=" + shapes[2]);
		helper.assertTrue(shapes[0] > 0 && shapes[1] > 0 && shapes[2] > 0, "the " + SEEDS + " seeds did not cover every shape: " + java.util.Arrays.toString(shapes));
		helper.assertTrue(broken.isEmpty(), broken.size() + " shrines do not stand whole, first: " + (broken.isEmpty() ? "none" : broken.get(0)));
		helper.assertTrue(whole == SEEDS * 4, "only " + whole + " of " + SEEDS * 4 + " shrines stand whole");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void theSameSeedDrawsTheSameBlocksInOneBoxOrChunkByChunk(final GameTestHelper helper) {
		int differing = 0;
		for (long seed : new long[] {3, 7, 11, 19}) {
			RuinFrame frame = frame(seed, Rotation.CLOCKWISE_90);
			Map<BlockPos, BlockState> whole = new HashMap<>();
			WayshrineLayout.draw(LayoutContext.forCapture(frame, whole, frame.box()));
			Map<BlockPos, BlockState> pieces = new HashMap<>();
			BoundingBox box = frame.box();
			int chunks = 0;
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					BoundingBox clip = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
					WayshrineLayout.draw(LayoutContext.forCapture(frame, pieces, clip));
					chunks++;
				}
			}
			helper.assertTrue(chunks >= 2, "the shrine fits in " + chunks + " chunk, so the check proves nothing");
			differing += whole.equals(pieces) ? 0 : 1;
		}
		WayshrineTestSupport.result("chunkByChunk", "seeds=4 differing=" + differing);
		helper.assertTrue(differing == 0, differing + " seeds drew different blocks chunk by chunk");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void allThreeShapesAndAllThreePalettesAppearOverThreeHundredSeeds(final GameTestHelper helper) {
		int[] shapes = new int[WayshrinePlan.VARIANTS];
		int[] palettes = new int[3];
		int minDecay = 100;
		int maxDecay = 0;
		Set<Integer> candles = new HashSet<>();
		Set<Integer> heights = new HashSet<>();
		for (long seed = 1; seed <= WIDE_SEEDS; seed++) {
			WayshrinePlan.Params params = WayshrinePlan.Params.roll(RandomSource.create(seed));
			shapes[params.variant()]++;
			palettes[params.palette()]++;
			minDecay = Math.min(minDecay, params.decayPercent());
			maxDecay = Math.max(maxDecay, params.decayPercent());
			candles.add(params.candles());
			heights.add(params.lampHeight());
		}
		WayshrineTestSupport.result("shapesAppear", "seeds=" + WIDE_SEEDS + " shapes=" + java.util.Arrays.toString(shapes) + " palettes=" + java.util.Arrays.toString(palettes)
			+ " decay=" + minDecay + ".." + maxDecay + " candles=" + candles + " lampHeights=" + heights);
		for (int shape : shapes) {
			helper.assertTrue(shape >= WIDE_SEEDS / 6, "a shape appeared only " + shape + " times in " + WIDE_SEEDS + ": " + java.util.Arrays.toString(shapes));
		}
		for (int palette : palettes) {
			helper.assertTrue(palette >= WIDE_SEEDS / 6, "a palette appeared only " + palette + " times: " + java.util.Arrays.toString(palettes));
		}
		helper.assertTrue(minDecay == WayshrinePlan.DECAY_MIN && maxDecay == WayshrinePlan.DECAY_MAX, "the decay rate ran " + minDecay + ".." + maxDecay);
		helper.assertTrue(candles.equals(Set.of(2, 3, 4)), "the candle counts were " + candles);
		helper.assertTrue(heights.equals(Set.of(8, 9, 10, 11)), "the lamp heights were " + heights);
		helper.succeed();
	}

	/** The cells where decay did something: a mossy block, a cracked block, or a column that lost blocks. */
	static Set<String> decayedCells(final WayshrinePlan plan) {
		Set<String> cells = new HashSet<>();
		for (int y = 1; y <= WayshrinePlan.TOP; y++) {
			for (int z = 0; z < WayshrinePlan.SIZE; z++) {
				for (int x = 0; x < WayshrinePlan.SIZE; x++) {
					BlockState state = plan.at(x, y, z);
					if (state != null && (state == plan.palette().wallMossy() || state == plan.palette().wallCracked())) {
						cells.add(x + "," + y + "," + z);
					}
				}
			}
		}
		for (int z = 0; z < WayshrinePlan.SIZE; z++) {
			for (int x = 0; x < WayshrinePlan.SIZE; x++) {
				if (plan.removedAt(x, z)) {
					cells.add("gone " + x + "," + z);
				}
			}
		}
		return cells;
	}

	/** Two seeds of one shape and one palette, so only decay and the candles can tell them apart. */
	static long[] sameShapeSamePalette(final int variant) {
		Map<Integer, Long> firstOfPalette = new LinkedHashMap<>();
		for (long seed = 1; seed < 5000; seed++) {
			WayshrinePlan.Params params = WayshrinePlan.Params.roll(RandomSource.create(seed));
			if (params.variant() == variant) {
				Long first = firstOfPalette.putIfAbsent(params.palette(), seed);
				if (first != null) {
					return new long[] {first, seed};
				}
			}
		}
		throw new IllegalStateException("no two seeds share shape " + variant);
	}

	@GameTest(maxTicks = 400)
	public void twoSeedsOfOneShapeDifferInTheirDecayBlocks(final GameTestHelper helper) {
		StringBuilder log = new StringBuilder();
		for (int variant = 0; variant < WayshrinePlan.VARIANTS; variant++) {
			long[] seeds = sameShapeSamePalette(variant);
			Set<String> a = decayedCells(planOf(seeds[0]));
			Set<String> b = decayedCells(planOf(seeds[1]));
			Set<String> again = decayedCells(planOf(seeds[0]));
			Set<String> onlyOne = new HashSet<>(a);
			onlyOne.removeAll(b);
			log.append("shape=").append(variant).append(" seeds=").append(seeds[0]).append(',').append(seeds[1]).append(" decayA=").append(a.size())
				.append(" decayB=").append(b.size()).append(" onlyInA=").append(onlyOne.size()).append("; ");
			helper.assertTrue(!a.equals(b), "two seeds of shape " + variant + " decayed the same cells");
			helper.assertTrue(a.equals(again), "the same seed decayed different cells on a second draw, shape " + variant);
			helper.assertTrue(!a.isEmpty() && !b.isEmpty(), "shape " + variant + " has no decay at all");
		}
		WayshrineTestSupport.result("decayDiffers", log.toString());
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void theDecayHitRateFollowsTheRolledPercentAndStaysBetweenTwentyAndFortyPercent(final GameTestHelper helper) {
		long candidates = 0;
		long hits = 0;
		double declared = 0.0;
		long mossy = 0;
		long cracked = 0;
		long air = 0;
		for (long seed = 1; seed <= WIDE_SEEDS; seed++) {
			WayshrinePlan plan = planOf(seed);
			int percent = plan.params().decayPercent();
			helper.assertTrue(percent >= WayshrinePlan.DECAY_MIN && percent <= WayshrinePlan.DECAY_MAX, "seed " + seed + " rolled a decay of " + percent);
			int seedHits = plan.mossy() + plan.cracked() + plan.air();
			candidates += plan.decayCandidates();
			hits += seedHits;
			declared += percent * (double) plan.decayCandidates();
			mossy += plan.mossy();
			cracked += plan.cracked();
			air += plan.air();
		}
		double measured = 100.0 * hits / candidates;
		double expected = declared / candidates;
		WayshrineTestSupport.result("decayRate", "candidates=" + candidates + " hits=" + hits + " measured=" + measured + " expected=" + expected + " mossy=" + mossy + " cracked=" + cracked + " air=" + air);
		helper.assertTrue(Math.abs(measured - expected) <= 1.5, "the decay hit " + measured + " percent of the blocks, the seeds asked for " + expected);
		helper.assertTrue(measured >= WayshrinePlan.DECAY_MIN && measured <= WayshrinePlan.DECAY_MAX, "the measured rate " + measured + " is outside 20 to 40 percent");
		for (long kind : new long[] {mossy, cracked, air}) {
			helper.assertTrue(Math.abs(kind - hits / 3.0) <= 0.1 * hits, "a decay kind took " + kind + " of " + hits + " hits, not a third");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void theLampStandsEightToElevenBlocksTallOnAnUnbrokenObelisk(final GameTestHelper helper) {
		Set<Integer> seen = new HashSet<>();
		for (long seed = 1; seed <= WIDE_SEEDS; seed++) {
			WayshrinePlan plan = planOf(seed);
			BlockPos lamp = plan.lamp();
			seen.add(lamp.getY());
			helper.assertTrue(lamp.getY() >= 8 && lamp.getY() <= 11, "seed " + seed + " puts the lamp at y" + lamp.getY());
			helper.assertTrue(plan.at(lamp.getX(), lamp.getY(), lamp.getZ()).is(WayshrineLamp.LAMP), "seed " + seed + " has no lamp at its lamp spot");
			for (int y = 1; y < lamp.getY(); y++) {
				BlockState below = plan.at(lamp.getX(), y, lamp.getZ());
				helper.assertTrue(below != null && !below.isAir(), "seed " + seed + " has a gap in the obelisk at y" + y);
			}
		}
		helper.assertTrue(seen.equals(Set.of(8, 9, 10, 11)), "the obelisks stood at " + seen);
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void everyKindHasMarkersOfItsOwnAndEveryMarkerHasRoom(final GameTestHelper helper) {
		for (long seed = 1; seed <= WIDE_SEEDS; seed++) {
			WayshrinePlan plan = planOf(seed);
			Map<String, Integer> counts = new HashMap<>();
			for (WayshrinePlan.Marker marker : plan.markers()) {
				counts.merge(marker.name(), 1, Integer::sum);
				if (marker.name().equals(WayshrinePlan.LAMP)) {
					continue;
				}
				BlockState at = plan.at(marker.x(), marker.y(), marker.z());
				BlockState above = plan.at(marker.x(), marker.y() + 1, marker.z());
				helper.assertTrue(at != null && at.isAir() && above != null && above.isAir(), "seed " + seed + " marker " + marker + " has no room");
				if (!marker.name().equals(WayshrinePlan.spawnMarker(WayshrineMobs.WISP))) {
					BlockState floor = plan.at(marker.x(), marker.y() - 1, marker.z());
					helper.assertTrue(floor != null && !floor.isAir(), "seed " + seed + " marker " + marker + " floats");
				}
			}
			helper.assertTrue(counts.getOrDefault(WayshrinePlan.spawnMarker(WayshrineMobs.PILGRIM), 0) == 4, "seed " + seed + " has " + counts + " pilgrim markers");
			helper.assertTrue(counts.getOrDefault(WayshrinePlan.spawnMarker(WayshrineMobs.WISP), 0) == 3, "seed " + seed + " has " + counts + " wisp markers");
			helper.assertTrue(counts.getOrDefault(WayshrinePlan.spawnMarker(WayshrineMobs.ACOLYTE), 0) == 1, "seed " + seed + " has " + counts + " acolyte markers");
			helper.assertTrue(counts.getOrDefault(WayshrinePlan.LAMP, 0) == 1, "seed " + seed + " names the lamp " + counts);
		}
		helper.succeed();
	}

	private static boolean solid(final WayshrinePlan plan, final int x, final int y, final int z) {
		BlockState state = plan.at(x, y, z);
		return state != null && !state.isAir() && !state.is(Blocks.CANDLE);
	}

	private static boolean air(final WayshrinePlan plan, final int x, final int y, final int z) {
		BlockState state = plan.at(x, y, z);
		return state != null && state.isAir();
	}

	private static boolean standable(final WayshrinePlan plan, final int x, final int y, final int z) {
		return solid(plan, x, y - 1, z) && air(plan, x, y, z) && air(plan, x, y + 1, z);
	}

	/** The standing spots a walker reaches from the path, one block up or up to three down at a step. */
	static Set<String> reachable(final WayshrinePlan plan) {
		Set<String> seen = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		if (standable(plan, 10, 1, 0)) {
			seen.add("10,1,0");
			queue.add(new int[] {10, 1, 0});
		}
		int[][] moves = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] move : moves) {
				int nx = at[0] + move[0];
				int nz = at[2] + move[1];
				for (int dy = 1; dy >= -3; dy--) {
					if (dy == 1 && !air(plan, at[0], at[1] + 2, at[2])) {
						continue;
					}
					if (standable(plan, nx, at[1] + dy, nz)) {
						String key = nx + "," + (at[1] + dy) + "," + nz;
						if (seen.add(key)) {
							queue.add(new int[] {nx, at[1] + dy, nz});
						}
						break;
					}
				}
			}
		}
		return seen;
	}

	private static boolean touches(final Set<String> reached, final BlockPos spot) {
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			if (reached.contains((spot.getX() + d[0]) + "," + spot.getY() + "," + (spot.getZ() + d[1]))) {
				return true;
			}
		}
		return false;
	}

	@GameTest(maxTicks = 600)
	public void aPlayerCanWalkFromThePathToTheCoreAndTheVaultAfterDecayInEverySeed(final GameTestHelper helper) {
		int core = 0;
		int vault = 0;
		for (long seed = 1; seed <= WIDE_SEEDS; seed++) {
			WayshrinePlan plan = planOf(seed);
			Set<String> reached = reachable(plan);
			core += touches(reached, plan.core()) ? 1 : 0;
			vault += touches(reached, plan.vault()) ? 1 : 0;
			for (WayshrinePlan.Marker marker : plan.markers()) {
				if (marker.name().startsWith(WayshrinePlan.SPAWN_PREFIX) && !marker.name().endsWith(WayshrineMobs.WISP)) {
					helper.assertTrue(reached.contains(marker.x() + "," + marker.y() + "," + marker.z()), "seed " + seed + " marker " + marker + " cannot be walked to");
				}
			}
		}
		WayshrineTestSupport.result("route", "seeds=" + WIDE_SEEDS + " coreReachable=" + core + " vaultReachable=" + vault);
		helper.assertTrue(core == WIDE_SEEDS, "the core is walkable in " + core + " of " + WIDE_SEEDS + " seeds");
		helper.assertTrue(vault == WIDE_SEEDS, "the vault is walkable in " + vault + " of " + WIDE_SEEDS + " seeds");
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void theWholeDrawFitsTheChunkBudget(final GameTestHelper helper) {
		RuinFrame frame = frame(5, Rotation.NONE);
		BoundingBox box = frame.box();
		for (int i = 0; i < 20; i++) {
			WayshrineLayout.draw(LayoutContext.forCapture(frame, new HashMap<>(), box));
		}
		long worst = 0;
		long total = 0;
		int draws = 0;
		for (long seed = 1; seed <= 60; seed++) {
			RuinFrame f = frame(seed, Rotation.NONE);
			for (int cx = f.box().minX() >> 4; cx <= f.box().maxX() >> 4; cx++) {
				for (int cz = f.box().minZ() >> 4; cz <= f.box().maxZ() >> 4; cz++) {
					BoundingBox clip = new BoundingBox(cx << 4, f.box().minY(), cz << 4, (cx << 4) + 15, f.box().maxY(), (cz << 4) + 15);
					long begun = System.nanoTime();
					WayshrineLayout.draw(LayoutContext.forCapture(f, new HashMap<>(), clip));
					long took = System.nanoTime() - begun;
					worst = Math.max(worst, took);
					total += took;
					draws++;
				}
			}
		}
		double meanMs = total / 1.0e6 / draws;
		double worstMs = worst / 1.0e6;
		WayshrineTestSupport.result("drawBudget", "draws=" + draws + " meanMs=" + meanMs + " worstMs=" + worstMs + " budgetMs=50 box=21x16x21");
		helper.assertTrue(meanMs <= 50.0, "a chunk draw took " + meanMs + " ms on average, over the 50 ms budget");
		helper.succeed();
	}

	/** Reads each block id the sketch named, so a block that moved or vanished in the game version is caught here. */
	@GameTest
	public void everyBlockThePalettesNameExistsInTheRegistryUnderItsExpectedId(final GameTestHelper helper) {
		Map<String, BlockState> expected = new LinkedHashMap<>();
		expected.put("minecraft:stone_bricks", WayshrinePalette.STONE_BRICK.floor());
		expected.put("minecraft:cracked_stone_bricks", WayshrinePalette.STONE_BRICK.wallCracked());
		expected.put("minecraft:mossy_stone_bricks", WayshrinePalette.STONE_BRICK.wallMossy());
		expected.put("minecraft:chiseled_stone_bricks", WayshrinePalette.STONE_BRICK.accent());
		expected.put("minecraft:stone_brick_slab", WayshrinePalette.STONE_BRICK.slab());
		expected.put("minecraft:stone_brick_stairs", WayshrinePalette.STONE_BRICK.stairs());
		expected.put("minecraft:cobblestone", WayshrinePalette.STONE_BRICK.foundation());
		expected.put("minecraft:quartz_bricks", WayshrinePalette.CALCITE.floor());
		expected.put("minecraft:calcite", WayshrinePalette.CALCITE.wall());
		expected.put("minecraft:quartz_pillar", WayshrinePalette.CALCITE.shaft());
		expected.put("minecraft:tuff_bricks", WayshrinePalette.CALCITE.wallCracked());
		expected.put("minecraft:moss_block", WayshrinePalette.CALCITE.wallMossy());
		expected.put("minecraft:chiseled_quartz_block", WayshrinePalette.CALCITE.accent());
		expected.put("minecraft:quartz_slab", WayshrinePalette.CALCITE.slab());
		expected.put("minecraft:quartz_stairs", WayshrinePalette.CALCITE.stairs());
		expected.put("minecraft:deepslate_tiles", WayshrinePalette.DEEPSLATE.floor());
		expected.put("minecraft:cracked_deepslate_tiles", WayshrinePalette.DEEPSLATE.floorCracked());
		expected.put("minecraft:deepslate_bricks", WayshrinePalette.DEEPSLATE.wall());
		expected.put("minecraft:cracked_deepslate_bricks", WayshrinePalette.DEEPSLATE.wallCracked());
		expected.put("minecraft:chiseled_deepslate", WayshrinePalette.DEEPSLATE.accent());
		expected.put("minecraft:deepslate_brick_slab", WayshrinePalette.DEEPSLATE.slab());
		expected.put("minecraft:deepslate_brick_stairs", WayshrinePalette.DEEPSLATE.stairs());
		expected.put("minecraft:cobbled_deepslate", WayshrinePalette.DEEPSLATE.foundation());
		expected.put("minecraft:dirt_path", Blocks.DIRT_PATH.defaultBlockState());
		expected.put("minecraft:candle", Blocks.CANDLE.defaultBlockState());
		for (Map.Entry<String, BlockState> entry : expected.entrySet()) {
			Identifier id = Identifier.parse(entry.getKey());
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), entry.getKey() + " is not in the block registry");
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			helper.assertTrue(block != Blocks.AIR && entry.getValue().is(block), entry.getKey() + " is not the block the palette holds: " + entry.getValue());
		}
		WayshrineTestSupport.result("registryIds", "checked=" + expected.size());
		helper.succeed();
	}

	/** The world match: every planned block stands in a real level, in each of the four turns, for a different shape. */
	private static void standsInTheWorld(final GameTestHelper helper, final Rotation rotation, final int variant) {
		WayshrineTestSupport.inArena(helper, rotation, WayshrineTestSupport.seedOf(variant), arena -> {
			int mismatches = 0;
			int checked = 0;
			String first = "";
			for (int y = -WayshrinePlan.DEPTH; y <= WayshrinePlan.TOP; y++) {
				for (int z = 0; z < WayshrinePlan.SIZE; z++) {
					for (int x = 0; x < WayshrinePlan.SIZE; x++) {
						BlockState planned = arena.plan().at(x, y, z);
						if (planned == null) {
							continue;
						}
						BlockPos world = arena.frame().toWorld(x, y, z);
						BlockState expected = planned;
						if (world.equals(arena.corePos())) {
							expected = RuinBlocks.CORE.defaultBlockState();
						} else if (world.equals(arena.vaultPos())) {
							expected = RuinBlocks.VAULT.defaultBlockState();
						}
						BlockState actual = arena.level().getBlockState(world);
						checked++;
						if (!actual.is(expected.getBlock())) {
							mismatches++;
							if (first.isEmpty()) {
								first = "local " + x + "," + y + "," + z + " wants " + expected + " has " + actual;
							}
						}
					}
				}
			}
			WayshrineTestSupport.result("standsInWorld", "turn=" + rotation + " shape=" + variant + " checked=" + checked + " mismatches=" + mismatches);
			helper.assertTrue(mismatches == 0, mismatches + " of " + checked + " blocks differ from the plan, first: " + first);
			helper.assertTrue(arena.core() != null && arena.vault() != null && arena.lamp() != null, "the core, the vault or the lamp has no block entity");
			helper.assertTrue(arena.level().getBlockState(arena.lampPos()).is(WayshrineLamp.LAMP), "no lamp at the lamp marker");
			helper.assertTrue(arena.vault().sealed(), "the vault is open before the rite");
		});
	}

	@GameTest(maxTicks = 6000)
	public void theRingStandsInARealLevelFacingNorth(final GameTestHelper helper) {
		standsInTheWorld(helper, Rotation.NONE, WayshrinePlan.RING);
	}

	@GameTest(maxTicks = 6000)
	public void theChapelStandsInARealLevelTurnedNinetyDegrees(final GameTestHelper helper) {
		standsInTheWorld(helper, Rotation.CLOCKWISE_90, WayshrinePlan.CHAPEL);
	}

	@GameTest(maxTicks = 6000)
	public void theSunkenAltarStandsInARealLevelTurnedHalfWay(final GameTestHelper helper) {
		standsInTheWorld(helper, Rotation.CLOCKWISE_180, WayshrinePlan.SUNKEN);
	}

	@GameTest(maxTicks = 6000)
	public void theRingStandsInARealLevelTurnedTwoHundredSeventyDegrees(final GameTestHelper helper) {
		standsInTheWorld(helper, Rotation.COUNTERCLOCKWISE_90, WayshrinePlan.RING);
	}
}
