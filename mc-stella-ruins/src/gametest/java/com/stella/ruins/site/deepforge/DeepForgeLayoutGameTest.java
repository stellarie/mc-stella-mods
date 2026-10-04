package com.stella.ruins.site.deepforge;

import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Acceptance items 1, 2, 3 and 13, and the plan rules from the sketch review. */
public class DeepForgeLayoutGameTest {
	static final int SEEDS = 16;
	static final int PLAN_SEEDS = 200;
	/** The time one chunk of the forge may take to draw, in milliseconds. */
	static final double CHUNK_BUDGET_MS = 50.0;

	static long seedOf(final int index) {
		return 0xF0E6E000L + index * 7919L;
	}

	private void standsWhole(final GameTestHelper helper, final int turns) {
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < SEEDS; i++) {
			long seed = seedOf(i);
			ForgeTestSupport.Capture c = ForgeTestSupport.capture(seed, turns);
			Plan plan = ForgeTestSupport.plan(seed);
			String where = "seed " + Long.toHexString(seed) + " turns " + turns + ": ";

			BlockPos core = c.markers(RuinMarkers.CORE).get(0);
			BlockPos vault = c.markers(RuinMarkers.VAULT).get(0);
			helper.assertTrue(c.blocks().get(core).is(RuinBlocks.CORE), where + "the core block is missing");
			helper.assertTrue(c.blocks().get(vault).is(RuinBlocks.VAULT), where + "the vault block is missing");
			helper.assertTrue(c.markers("entry").size() == 1, where + "the forge has no entry marker");

			ForgeTestSupport.Walk walk = new ForgeTestSupport.Walk(c);
			Set<Long> seen = walk.reach(c.markers("entry").get(0));
			for (String name : new String[] {"imp", "holder", "lever", "golem", "guardian"}) {
				for (BlockPos spot : c.markers(name)) {
					helper.assertTrue(walk.reached(seen, spot), where + "a " + name + " marker at " + spot.toShortString() + " cannot be walked to from the gate");
				}
			}
			helper.assertTrue(walk.reachedBeside(seen, core), where + "the core cannot be reached");
			helper.assertTrue(walk.reachedBeside(seen, c.markers("forge").get(0)), where + "the forge block cannot be reached");
			boolean gateFront = false;
			for (BlockPos gate : c.markers("vault_gate")) {
				gateFront |= walk.reachedBeside(seen, gate);
			}
			helper.assertTrue(gateFront, where + "the vault gate cannot be reached");
			List<BlockPos> unguarded = walk.unguardedLava(seen);
			helper.assertTrue(unguarded.isEmpty(), where + unguarded.size() + " reachable spots stand beside lava with no rail, first " + (unguarded.isEmpty() ? "" : unguarded.get(0).toShortString()));
			helper.assertTrue(c.markers("imp").size() == plan.chambers().size(), where + c.markers("imp").size() + " imp markers for " + plan.chambers().size() + " chambers");
			helper.assertTrue(c.markers("holder").size() == Plan.HOLDERS, where + c.markers("holder").size() + " holders");
			helper.assertTrue(c.markers("lever").size() == Plan.LEVERS, where + c.markers("lever").size() + " levers");

			// The same forge once the guardian has fallen: the gate is open, and the vault can be reached.
			for (BlockPos gate : c.markers("vault_gate")) {
				c.blocks().put(gate, Blocks.AIR.defaultBlockState());
			}
			Set<Long> after = walk.reach(c.markers("entry").get(0));
			helper.assertTrue(walk.reachedBeside(after, vault), where + "the vault cannot be reached after the gate opens");
			line.append(' ').append(plan.chambers().size());
		}
		ForgeTestSupport.result("standsWhole", "turns=" + turns + " seeds=" + SEEDS + " chambers=" + line);
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void theForgeStandsWholeAndEveryChamberIsWalkableFacingNorth(final GameTestHelper helper) {
		standsWhole(helper, 0);
	}

	@GameTest(maxTicks = 600)
	public void theForgeStandsWholeAndEveryChamberIsWalkableTurnedOnce(final GameTestHelper helper) {
		standsWhole(helper, 1);
	}

	@GameTest(maxTicks = 600)
	public void theForgeStandsWholeAndEveryChamberIsWalkableTurnedTwice(final GameTestHelper helper) {
		standsWhole(helper, 2);
	}

	@GameTest(maxTicks = 600)
	public void theForgeStandsWholeAndEveryChamberIsWalkableTurnedThrice(final GameTestHelper helper) {
		standsWhole(helper, 3);
	}

	@GameTest
	public void theWalkerCannotCrossLavaWithoutBarsSoTheGuardCheckCanFail(final GameTestHelper helper) {
		// The control: strip the bars off one capture, and the unguarded-lava check must find an edge.
		ForgeTestSupport.Capture c = ForgeTestSupport.capture(seedOf(0), 0);
		ForgeTestSupport.Walk walk = new ForgeTestSupport.Walk(c);
		Set<Long> seen = walk.reach(c.markers("entry").get(0));
		helper.assertTrue(walk.unguardedLava(seen).isEmpty(), "the untouched forge already has an unguarded edge");
		c.blocks().replaceAll((pos, state) -> state.is(Blocks.IRON_BARS) ? Blocks.AIR.defaultBlockState() : state);
		Set<Long> bare = walk.reach(c.markers("entry").get(0));
		int edges = walk.unguardedLava(bare).size();
		ForgeTestSupport.result("guardControl", "edgesWithoutBars=" + edges);
		helper.assertTrue(edges > 0, "with every bar removed the check still found no unguarded lava, so it proves nothing");
		helper.succeed();
	}

	@GameTest
	public void everyBlockLandsInsideTheBoxAndNaturalAirInsideItIsSealed(final GameTestHelper helper) {
		for (int turns = 0; turns < 4; turns++) {
			ForgeTestSupport.Capture c = ForgeTestSupport.capture(seedOf(3), turns);
			BoundingBox box = c.frame().box();
			int outside = 0;
			for (BlockPos pos : c.blocks().keySet()) {
				if (!box.isInside(pos)) {
					outside++;
				}
			}
			helper.assertTrue(outside == 0, "turns " + turns + ": " + outside + " blocks fell outside the box");
			int unwritten = 0;
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						if (!c.blocks().containsKey(new BlockPos(x, y, z))) {
							unwritten++;
						}
					}
				}
			}
			ForgeTestSupport.result("sealsTheBox", "turns=" + turns + " cells=" + box.getXSpan() * box.getYSpan() * box.getZSpan() + " unwritten=" + unwritten);
			helper.assertTrue(unwritten == 0, "turns " + turns + ": " + unwritten + " cells were never written, so natural air could remain");
		}
		helper.succeed();
	}

	@GameTest
	public void aDryRunFindsTheMarkersAWorldDrawFinds(final GameTestHelper helper) {
		RuinFrame frame = ForgeTestSupport.frame(seedOf(5), 2);
		com.stella.ruins.world.LayoutContext dry = com.stella.ruins.world.LayoutContext.forDryRun(frame);
		ForgeTestSupport.site().layout().draw(dry);
		ForgeTestSupport.Capture c = ForgeTestSupport.capture(seedOf(5), 2);
		helper.assertTrue(dry.markers().equals(c.markers()), "the dry run found other markers than a real draw");
		helper.succeed();
	}

	@GameTest
	public void theChamberCountVariesFromFourToSevenAndTheLeverChambersVary(final GameTestHelper helper) {
		Set<Integer> counts = new HashSet<>();
		Set<String> leverSets = new HashSet<>();
		Set<String> gates = new HashSet<>();
		Set<Integer> routes = new HashSet<>();
		for (int i = 0; i < PLAN_SEEDS; i++) {
			Plan plan = Plan.roll(RandomSource.create(seedOf(i)));
			counts.add(plan.chambers().size());
			gates.add(plan.gate().name());
			routes.add(plan.route());
			StringBuilder levers = new StringBuilder();
			for (Plan.ChamberPlan c : plan.chambers()) {
				if (c.lever()) {
					levers.append(c.slot()).append(c.level()).append(',');
				}
			}
			leverSets.add(levers.toString());
			helper.assertTrue(plan.chambers().size() >= Plan.MIN_CHAMBERS && plan.chambers().size() <= Plan.MAX_CHAMBERS, "seed " + i + " rolled " + plan.chambers().size() + " chambers");
		}
		ForgeTestSupport.result("planVaries", "counts=" + counts + " leverSets=" + leverSets.size() + " gates=" + gates + " routes=" + routes);
		helper.assertTrue(counts.equals(Set.of(4, 5, 6, 7)), "the chamber counts over " + PLAN_SEEDS + " seeds were " + counts);
		helper.assertTrue(leverSets.size() >= 20, "only " + leverSets.size() + " different lever chamber sets in " + PLAN_SEEDS + " seeds");
		helper.assertTrue(gates.size() == 3 && routes.size() == Plan.ROUTES, "gates " + gates + " routes " + routes);
		helper.succeed();
	}

	@GameTest
	public void everyPlanHoldsThreeHoldersInThreeChambersAndThreeLeversAndKeepsTheLevelRules(final GameTestHelper helper) {
		for (int i = 0; i < PLAN_SEEDS; i++) {
			Plan plan = Plan.roll(RandomSource.create(seedOf(i)));
			String where = "seed " + i + ": ";
			helper.assertTrue(plan.holderCount() == Plan.HOLDERS, where + plan.holderCount() + " holder chambers");
			helper.assertTrue(plan.leverCount() == Plan.LEVERS, where + plan.leverCount() + " lever chambers");
			int both = 0;
			Set<String> cells = new HashSet<>();
			for (Plan.ChamberPlan c : plan.chambers()) {
				both += c.holder() && c.lever() ? 1 : 0;
				helper.assertTrue(cells.add(c.slot() + "@" + c.level()), where + "two chambers share the cell " + c.slot() + "@" + c.level());
				helper.assertTrue(!(c.level() == Geo.Level.C && c.slot() == Geo.Slot.N), where + "a chamber stands where the vault is");
				helper.assertTrue(!(c.level() == Geo.Level.A && c.slot() == plan.gate()), where + "a chamber stands in the gate's cell");
				boolean allowed = false;
				for (int offset : c.level().offsets()) {
					allowed |= offset == c.offset();
				}
				helper.assertTrue(allowed, where + "offset " + c.offset() + " is not allowed on level " + c.level());
			}
			helper.assertTrue(plan.chambers().stream().map(Plan.ChamberPlan::level).distinct().count() >= 2, where + "the forge uses one level");
			if (plan.chambers().size() >= 6) {
				helper.assertTrue(both == 0, where + both + " chambers hold a holder and a lever with " + plan.chambers().size() + " chambers");
			} else {
				helper.assertTrue(both >= Plan.HOLDERS + Plan.LEVERS - plan.chambers().size(), where + "levers did not fall back onto holder chambers");
			}
			for (Plan.ChamberPlan c : plan.chambers()) {
				for (Plan.ChamberPlan d : plan.chambers()) {
					if (c != d && c.slot() == d.slot()) {
						helper.assertTrue(c.offset() == 0 && d.offset() == 0, where + "stacked chambers in " + c.slot() + " do not sit at offset 0");
					}
				}
			}
			helper.assertTrue(!plan.spurs().isEmpty() && plan.spurs().size() <= 3, where + plan.spurs().size() + " spurs");
		}
		helper.succeed();
	}

	/** A ground a test describes with one surface height and a few liquid cells. */
	static final class Ground implements Placement.Probe {
		final int surface;
		final Set<BlockPos> liquid;

		Ground(final int surface, final Set<BlockPos> liquid) {
			this.surface = surface;
			this.liquid = liquid;
		}

		@Override
		public int groundY(final int x, final int z) {
			return this.surface;
		}

		@Override
		public int surfaceY(final int x, final int z) {
			return this.surface;
		}

		@Override
		public BlockState stateAt(final int x, final int y, final int z) {
			return this.liquid.contains(new BlockPos(x, y, z)) ? Blocks.LAVA.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
		}

		@Override
		public int minY() {
			return -64;
		}

		@Override
		public int maxY() {
			return 320;
		}
	}

	@GameTest
	public void theCavePlacementKeepsTheTopTwelveBlocksUnderTheSurfaceAndTheBottomAboveMinusSixty(final GameTestHelper helper) {
		Placement.Cave rule = (Placement.Cave) ForgeTestSupport.site().placement();
		int sizeY = ForgeTestSupport.site().sizeY();
		int west = 1000;
		int north = 1000;
		int lowest = Math.max(rule.minY(), -64);
		int highest = Math.min(rule.maxY() - sizeY + 1, 320 - sizeY);
		helper.assertTrue(rule.minCover() == 12, "the site asks for " + rule.minCover() + " blocks of cover, not 12");
		for (int surface : new int[] {64, 80, 120}) {
			for (long seed = 1; seed <= 8; seed++) {
				Optional<BlockPos> chosen = rule.choose(new Ground(surface, Set.of()), RandomSource.create(seed), west, north, 64, sizeY, 64);
				helper.assertTrue(chosen.isPresent(), "a forge was refused under a surface at " + surface);
				int bottom = chosen.get().getY();
				int top = bottom + sizeY - 1;
				helper.assertTrue(surface - top >= 12, "the top at " + top + " lies under only " + (surface - top) + " blocks");
				helper.assertTrue(bottom > -60, "the bottom at " + bottom + " is not above -60");
				if (seed == 1) {
					ForgeTestSupport.result("cavePlacement", "surface=" + surface + " bottom=" + bottom + " top=" + top + " cover=" + (surface - top));
				}
			}
		}
		int lowestRoof = lowest + sizeY - 1;
		int highestRoof = highest + sizeY - 1;
		helper.assertTrue(rule.choose(new Ground(highestRoof + 12, Set.of()), RandomSource.create(1L), west, north, 64, sizeY, 64).isPresent(), "a surface with exactly the cover over the highest roof was refused");
		helper.assertTrue(rule.choose(new Ground(lowestRoof + 11, Set.of()), RandomSource.create(1L), west, north, 64, sizeY, 64).isEmpty(), "a surface one block short of the cover over the lowest roof was accepted");
		Set<BlockPos> wet = new HashSet<>();
		for (int floor = lowest; floor <= highest; floor++) {
			wet.add(new BlockPos(west + 32, floor + sizeY / 2, north + 32));
		}
		for (long seed = 1; seed <= 8; seed++) {
			helper.assertTrue(rule.choose(new Ground(120, wet), RandomSource.create(seed), west, north, 64, sizeY, 64).isEmpty(), "a forge was placed over lava at its middle");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 12000)
	public void aFullForgeDrawsEveryChunkInsideTheBudget(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, seedOf(7), arena -> {
			BoundingBox box = arena.frame().box();
			List<Double> times = new ArrayList<>();
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					BoundingBox clip = new BoundingBox(Math.max(cx << 4, box.minX()), box.minY(), Math.max(cz << 4, box.minZ()),
						Math.min((cx << 4) + 15, box.maxX()), box.maxY(), Math.min((cz << 4) + 15, box.maxZ()));
					long begun = System.nanoTime();
					arena.piece().build(arena.level(), clip);
					times.add((System.nanoTime() - begun) / 1_000_000.0);
				}
			}
			double max = times.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
			double mean = times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
			ForgeTestSupport.result("generationTime", "chunks=" + times.size() + " meanMs=" + String.format("%.2f", mean) + " maxMs=" + String.format("%.2f", max) + " budgetMs=" + CHUNK_BUDGET_MS);
			helper.assertTrue(max < CHUNK_BUDGET_MS, "the slowest chunk took " + max + " ms, over the " + CHUNK_BUDGET_MS + " ms budget");
		});
	}

	@GameTest(maxTicks = 12000)
	public void theWorldDrawMatchesTheCaptureBlockForBlock(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, seedOf(11), arena -> {
			ForgeTestSupport.Capture c = captureOf(arena.frame());
			int wrong = 0;
			BlockPos first = null;
			for (java.util.Map.Entry<BlockPos, BlockState> entry : c.blocks().entrySet()) {
				if (arena.level().getBlockState(entry.getKey()).getBlock() != entry.getValue().getBlock() && !entry.getValue().is(Blocks.DEEPSLATE)) {
					wrong++;
					first = first == null ? entry.getKey() : first;
				}
			}
			helper.assertTrue(wrong == 0, wrong + " blocks of the world draw differ from the capture, first at " + first);
		});
	}

	private static ForgeTestSupport.Capture captureOf(final RuinFrame frame) {
		java.util.Map<BlockPos, BlockState> sink = new java.util.HashMap<>(1 << 19);
		com.stella.ruins.world.LayoutContext ctx = com.stella.ruins.world.LayoutContext.forCapture(frame, sink, frame.box());
		ForgeTestSupport.site().layout().draw(ctx);
		return new ForgeTestSupport.Capture(frame, sink, ctx.markers());
	}

	@GameTest
	public void theForgeDrawsFourRotationsWithTurnedMarkers(final GameTestHelper helper) {
		RuinFrame north = ForgeTestSupport.frame(seedOf(2), 0);
		com.stella.ruins.world.LayoutContext dry = com.stella.ruins.world.LayoutContext.forDryRun(north);
		ForgeTestSupport.site().layout().draw(dry);
		BlockPos core = dry.markers().get(RuinMarkers.CORE).get(0);
		helper.assertTrue(core.equals(north.toWorld(DeepForgeLayout.CORE_X, DeepForgeLayout.CORE_Y, DeepForgeLayout.CORE_Z)), "the core is not where the layout names it");
		Set<BlockPos> corners = new HashSet<>();
		for (Rotation rotation : Rotation.values()) {
			RuinFrame turned = RuinFrame.of(ForgeTestSupport.site(), new BlockPos(5, 100, 9), rotation, seedOf(2));
			corners.add(turned.toWorld(DeepForgeLayout.CORE_X, DeepForgeLayout.CORE_Y, DeepForgeLayout.CORE_Z));
		}
		helper.assertTrue(corners.size() == 4, "the core lands on " + corners.size() + " spots over four turns, not 4");
		helper.succeed();
	}
}
