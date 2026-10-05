package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinSite;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

/** Items 1 to 3: the grotto stands whole, its random elements vary, and it keeps its cover. */
public class GrottoPlanGameTest {
	static final int SEEDS = 24;
	static final int VARIETY_SEEDS = 200;
	static final int PLAYABLE_SEEDS = 3000;
	static final BlockState STONE = Blocks.STONE.defaultBlockState();

	/** Draws the grotto into a map over solid stone, so the tunnel lining has rock to cling to. */
	static Map<BlockPos, BlockState> capture(final RuinFrame frame) {
		Map<BlockPos, BlockState> sink = new HashMap<>();
		BoundingBox box = frame.box();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					sink.put(new BlockPos(x, y, z), STONE);
				}
			}
		}
		GrottoTestSupport.site().layout().draw(LayoutContext.forCapture(frame, sink, box));
		return sink;
	}

	static RuinFrame frame(final long seed, final Rotation rotation) {
		return RuinFrame.of(GrottoTestSupport.site(), new BlockPos(5000, 40, -3000), rotation, seed);
	}

	static BlockState at(final Map<BlockPos, BlockState> sink, final RuinFrame frame, final int x, final int y, final int z) {
		return sink.get(frame.toWorld(x, y, z + GrottoPlan.EXTENSION));
	}

	@GameTest(maxTicks = 600)
	public void theGrottoStandsWholeOverManySeedsAndEveryRotation(final GameTestHelper helper) {
		int pillarsSeen = 0;
		int drawn = 0;
		for (int s = 0; s < SEEDS; s++) {
			long seed = 0x6A07700L + s * 7919L;
			for (Rotation rotation : Rotation.values()) {
				RuinFrame frame = frame(seed, rotation);
				GrottoPlan plan = GrottoPlan.derive(RandomSource.create(seed));
				Map<BlockPos, BlockState> sink = capture(frame);
				String where = "seed " + seed + " " + rotation;
				helper.assertTrue(plan.pillars().size() >= 4 && plan.pillars().size() <= 6, where + ": " + plan.pillars().size() + " pillars");
				helper.assertTrue(sink.get(frame.toWorld(12, 4, 12 + GrottoPlan.EXTENSION)).is(RuinBlocks.CORE), where + ": the core is missing");
				helper.assertTrue(sink.get(frame.toWorld(12, 4, 20 + GrottoPlan.EXTENSION)).is(RuinBlocks.VAULT), where + ": the vault is missing");
				for (GrottoPlan.Pillar pillar : plan.pillars()) {
					for (int y = 4; y <= pillar.shaftTop(); y++) {
						BlockState shaft = at(sink, frame, pillar.x(), y, pillar.z());
						helper.assertTrue(shaft.is(ResonantCrystalBlock.BLOCK) && shaft.getValue(ResonantCrystalBlock.TINT) == pillar.tint(),
							where + ": pillar " + pillar.index() + " has no " + pillar.tint() + " crystal at y" + y + ": " + shaft);
					}
					helper.assertTrue(at(sink, frame, pillar.x(), pillar.shaftTop() + 1, pillar.z()).is(Blocks.AMETHYST_CLUSTER), where + ": pillar " + pillar.index() + " has no crown");
					helper.assertTrue(at(sink, frame, pillar.x(), 3, pillar.z()).is(Blocks.CALCITE), where + ": pillar " + pillar.index() + " has no plinth");
				}
				for (int x = 11; x <= 13; x++) {
					for (int y = 3; y <= 5; y++) {
						for (int z = GrottoPlan.TUNNEL_START; z <= 6; z++) {
							helper.assertTrue(at(sink, frame, x, y, z).isAir(), where + ": the tunnel is blocked at x" + x + " y" + y + " z" + z + ": " + at(sink, frame, x, y, z));
						}
					}
				}
				helper.assertTrue(at(sink, frame, 12, 3, 12 - 6).isAir() && at(sink, frame, 12, 9, 12).isAir(), where + ": the chamber is not open");
				pillarsSeen += plan.pillars().size();
				drawn++;
			}
		}
		GrottoTestSupport.result("standsWhole", "grottoes=" + drawn + " pillars=" + pillarsSeen);
		helper.assertTrue(drawn == SEEDS * 4, "drew " + drawn);
		helper.succeed();
	}

	@GameTest
	public void theTunnelLiningHugsRockAndSkipsCaveAir(final GameTestHelper helper) {
		RuinFrame frame = frame(0x77AA, Rotation.NONE);
		Map<BlockPos, BlockState> rock = capture(frame);
		BlockState lining = at(rock, frame, 12, 6, -8);
		helper.assertTrue(lining.is(Blocks.CALCITE), "no calcite lining over rock above the tunnel: " + lining);
		Map<BlockPos, BlockState> open = new HashMap<>();
		BoundingBox box = frame.box();
		GrottoTestSupport.site().layout().draw(LayoutContext.forCapture(frame, open, box));
		helper.assertTrue(!open.containsKey(frame.toWorld(12, 6, -8 + GrottoPlan.EXTENSION)), "the lining stood in open cave air");
		helper.assertTrue(open.getOrDefault(frame.toWorld(12, 4, -8 + GrottoPlan.EXTENSION), STONE).isAir() || !open.containsKey(frame.toWorld(12, 4, -8 + GrottoPlan.EXTENSION)), "the tunnel floor was not air");
		GrottoTestSupport.result("tunnelLining", "overRock=" + lining + " inAir=absent");
		helper.succeed();
	}

	@GameTest
	public void pillarCountPositionsAndColoursVaryAcrossSeeds(final GameTestHelper helper) {
		Set<Integer> counts = new HashSet<>();
		Set<String> signatures = new HashSet<>();
		Set<String> positionSets = new HashSet<>();
		Set<Set<Tint>> colourSets = new HashSet<>();
		Set<Tint> firstColours = EnumSet.noneOf(Tint.class);
		for (int s = 0; s < VARIETY_SEEDS; s++) {
			GrottoPlan plan = GrottoPlan.derive(RandomSource.create(1000L + s));
			counts.add(plan.pillars().size());
			signatures.add(plan.signature());
			StringBuilder positions = new StringBuilder();
			Set<Tint> colours = EnumSet.noneOf(Tint.class);
			for (GrottoPlan.Pillar pillar : plan.pillars()) {
				positions.append(pillar.x()).append(',').append(pillar.z()).append(';');
				colours.add(pillar.tint());
			}
			positionSets.add(positions.toString());
			colourSets.add(colours);
			firstColours.add(plan.pillars().get(0).tint());
		}
		GrottoTestSupport.result("variety", "seeds=" + VARIETY_SEEDS + " counts=" + counts + " signatures=" + signatures.size() + " positionSets=" + positionSets.size()
			+ " colourSets=" + colourSets.size() + " firstColours=" + firstColours.size());
		helper.assertTrue(counts.equals(Set.of(4, 5, 6)), "pillar counts seen: " + counts);
		helper.assertTrue(signatures.size() >= 190, "only " + signatures.size() + " distinct grottoes in " + VARIETY_SEEDS);
		helper.assertTrue(positionSets.size() >= 25, "only " + positionSets.size() + " distinct position sets");
		helper.assertTrue(colourSets.size() >= 15, "only " + colourSets.size() + " distinct colour sets");
		helper.assertTrue(firstColours.size() == 6, "the first pillar took only " + firstColours.size() + " colours");
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void everyPlanIsPlayable(final GameTestHelper helper) {
		int minHeight = Integer.MAX_VALUE;
		int maxHeight = 0;
		for (int s = 0; s < PLAYABLE_SEEDS; s++) {
			GrottoPlan plan = GrottoPlan.derive(RandomSource.create(77_000L + s));
			List<GrottoPlan.Pillar> pillars = plan.pillars();
			Set<Tint> tints = EnumSet.noneOf(Tint.class);
			for (GrottoPlan.Pillar a : pillars) {
				tints.add(a.tint());
				minHeight = Math.min(minHeight, a.height());
				maxHeight = Math.max(maxHeight, a.height());
				String where = "seed " + (77_000L + s) + " pillar " + a.index();
				helper.assertTrue(a.height() >= GrottoPlan.MIN_HEIGHT && a.height() <= GrottoPlan.MAX_HEIGHT, where + " height " + a.height());
				helper.assertTrue(plan.isAir(a.x(), a.crownY(), a.z()), where + " crown is not in the chamber air");
				helper.assertTrue(!a.spoke().isEmpty(), where + " has no floor spoke; plan " + plan.signature() + " slot " + a.slot());
				for (GrottoPlan.Pillar b : pillars) {
					helper.assertTrue(a == b || Math.abs(a.x() - b.x()) > 2 || Math.abs(a.z() - b.z()) > 2, where + " overlaps pillar " + b.index());
				}
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int x = a.x() + dx;
						int z = a.z() + dz;
						helper.assertTrue(!(x >= 10 && x <= 14 && z >= 17 && z <= 21) && !(x >= 11 && x <= 13 && z <= 6), where + " plinth hits the niche or the tunnel at " + x + "," + z);
						helper.assertTrue(Math.abs(x - 12) > 1 || Math.abs(z - 12) > 1, where + " plinth sits on the core pedestal");
					}
				}
			}
			helper.assertTrue(tints.size() == pillars.size(), "seed " + (77_000L + s) + " repeats a colour");
			for (GrottoPlan.Cell cell : plan.sentinelCells()) {
				helper.assertTrue(clear(plan, cell) && plan.isAir(cell.x(), 3, cell.z()) && plan.isAir(cell.x(), 4, cell.z()), "seed " + (77_000L + s) + " sentinel cell " + cell);
			}
			for (GrottoPlan.Cell cell : plan.slimeCells()) {
				helper.assertTrue(clear(plan, cell) && plan.isAir(cell.x(), 3, cell.z()) && plan.isAir(cell.x(), 4, cell.z()), "seed " + (77_000L + s) + " slime cell " + cell);
			}
		}
		GrottoTestSupport.result("playable", "seeds=" + PLAYABLE_SEEDS + " heights=" + minHeight + ".." + maxHeight);
		helper.assertTrue(minHeight == 3 && maxHeight == 5, "heights spanned " + minHeight + ".." + maxHeight);
		helper.succeed();
	}

	private static boolean clear(final GrottoPlan plan, final GrottoPlan.Cell cell) {
		for (GrottoPlan.Pillar pillar : plan.pillars()) {
			if (Math.abs(cell.x() - pillar.x()) <= 1 && Math.abs(cell.z() - pillar.z()) <= 1) {
				return false;
			}
		}
		boolean pedestal = Math.abs(cell.x() - 12) <= 1 && Math.abs(cell.z() - 12) <= 1;
		boolean dais = cell.x() >= 11 && cell.x() <= 13 && cell.z() >= 19 && cell.z() <= 20;
		return !pedestal && !dais;
	}

	/** A probe with flat ground at a given surface height, and open air below it. */
	private static Placement.Probe flat(final int surface) {
		return new Placement.Probe() {
			@Override
			public int groundY(final int x, final int z) {
				return surface;
			}

			@Override
			public int surfaceY(final int x, final int z) {
				return surface;
			}

			@Override
			public BlockState stateAt(final int x, final int y, final int z) {
				return Blocks.STONE.defaultBlockState();
			}

			@Override
			public int minY() {
				return -64;
			}

			@Override
			public int maxY() {
				return 320;
			}
		};
	}

	@GameTest
	public void aCaveSpotKeepsTheGrottoTopAtLeastEightBlocksUnderTheSurface(final GameTestHelper helper) {
		RuinSite site = GrottoTestSupport.site();
		helper.assertTrue(site.placement().equals(Placement.cave(-48, 40, 8)), "the placement is " + site.placement());
		helper.assertTrue(site.sizeX() == 25 && site.sizeY() == 16 && site.sizeZ() == 41, "the bounds are " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		int accepted = 0;
		for (int surface : new int[] {14, 20, 30, 60, 120}) {
			for (int seed = 0; seed < 40; seed++) {
				Optional<BlockPos> floor = site.placement().choose(flat(surface), RandomSource.create(seed), 0, 0, 25, 16, 41);
				if (floor.isPresent()) {
					accepted++;
					int roof = floor.get().getY() + site.sizeY() - 1;
					helper.assertTrue(surface - roof >= 8, "surface " + surface + " roof " + roof + " leaves " + (surface - roof) + " blocks of cover");
				}
			}
		}
		helper.assertTrue(accepted > 20, "only " + accepted + " tries were accepted, so the cover check proves little");
		Optional<BlockPos> shallow = site.placement().choose(flat(-45), RandomSource.create(1), 0, 0, 25, 16, 41);
		GrottoTestSupport.result("caveCover", "accepted=" + accepted + " shallowRefused=" + shallow.isEmpty());
		helper.assertTrue(shallow.isEmpty(), "a surface 3 blocks over the lowest floor took a grotto: " + shallow);
		helper.succeed();
	}

	@GameTest
	public void theMarkersMatchThePlan(final GameTestHelper helper) {
		for (int s = 0; s < 20; s++) {
			long seed = 5150L + s;
			RuinFrame frame = frame(seed, Rotation.CLOCKWISE_90);
			GrottoPlan plan = GrottoPlan.derive(RandomSource.create(seed));
			LayoutContext dry = LayoutContext.forDryRun(frame);
			GrottoTestSupport.site().layout().draw(dry);
			Map<String, List<BlockPos>> marks = dry.markers();
			helper.assertTrue(marks.get(GrottoMarkers.PILLAR).size() == plan.pillars().size(), "pillar markers: " + marks.get(GrottoMarkers.PILLAR).size());
			helper.assertTrue(marks.get(GrottoMarkers.SENTINEL).size() == 4, "sentinel markers: " + marks.get(GrottoMarkers.SENTINEL).size());
			helper.assertTrue(marks.get(RuinMarkers.SPAWN).size() == CrystalGrottoSite.SLIMES, "spawn markers: " + marks.get(RuinMarkers.SPAWN).size());
			helper.assertTrue(marks.get(RuinMarkers.CORE).size() == 1 && marks.get(RuinMarkers.VAULT).size() == 1, "core or vault marker count");
			for (GrottoPlan.Pillar pillar : plan.pillars()) {
				helper.assertTrue(marks.get(GrottoMarkers.PILLAR).get(pillar.index()).equals(frame.toWorld(pillar.x(), 4, pillar.z() + GrottoPlan.EXTENSION)), "pillar marker " + pillar.index() + " is off its plan");
				helper.assertTrue(marks.get(GrottoMarkers.spoke(pillar.index())).size() == pillar.spoke().size(), "spoke marker " + pillar.index());
			}
		}
		helper.succeed();
	}
}
