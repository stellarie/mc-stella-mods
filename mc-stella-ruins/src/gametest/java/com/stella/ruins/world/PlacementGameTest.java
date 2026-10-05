package com.stella.ruins.world;

import java.util.Optional;
import java.util.function.IntBinaryOperator;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Item 3: placement rules read the ground through a stub, so each refusal has its own check. */
public class PlacementGameTest {
	static final int SIZE_X = 20;
	static final int SIZE_Y = 12;
	static final int SIZE_Z = 20;
	static final int WEST = 100;
	static final int NORTH = 200;

	/** A ground that a test describes with a few numbers and one liquid spot. */
	static final class Stub implements Placement.Probe {
		IntBinaryOperator ground = (x, z) -> 60;
		IntBinaryOperator surface = (x, z) -> 60;
		BlockPos liquidAt;
		BlockState liquid = Blocks.WATER.defaultBlockState();

		@Override
		public int groundY(final int x, final int z) {
			return this.ground.applyAsInt(x, z);
		}

		@Override
		public int surfaceY(final int x, final int z) {
			return this.surface.applyAsInt(x, z);
		}

		@Override
		public BlockState stateAt(final int x, final int y, final int z) {
			return this.liquidAt != null && this.liquidAt.equals(new BlockPos(x, y, z)) ? this.liquid : Blocks.STONE.defaultBlockState();
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

	/** A cave rule whose floor is fixed at {@code floor}: its range holds exactly one floor. */
	static Placement caveAt(final int floor, final int cover) {
		return Placement.cave(floor, floor + SIZE_Y - 1, cover);
	}

	static Optional<BlockPos> choose(final Placement placement, final Placement.Probe probe) {
		return placement.choose(probe, RandomSource.create(1L), WEST, NORTH, SIZE_X, SIZE_Y, SIZE_Z);
	}

	@GameTest
	public void aCaveSiteAcceptsExactlyTheCoverItNeeds(final GameTestHelper helper) {
		int floor = 10;
		int cover = 8;
		int roof = floor + SIZE_Y - 1;
		Stub enough = new Stub();
		enough.surface = (x, z) -> roof + cover;
		Stub short1 = new Stub();
		short1.surface = (x, z) -> roof + cover - 1;
		Optional<BlockPos> accepted = choose(caveAt(floor, cover), enough);
		Optional<BlockPos> refused = choose(caveAt(floor, cover), short1);
		WorldTestSupport.result("caveCover", "cover=" + cover + " accepted=" + accepted.isPresent() + " oneShortRefused=" + refused.isEmpty());
		helper.assertTrue(accepted.isPresent() && accepted.get().equals(new BlockPos(WEST, floor, NORTH)), "a site with exactly its cover was refused");
		helper.assertTrue(refused.isEmpty(), "a site one block short of its cover was accepted");
		helper.succeed();
	}

	@GameTest
	public void aCaveSiteIsRefusedWhereOneColumnIsTooShallow(final GameTestHelper helper) {
		int floor = 10;
		int cover = 8;
		Stub thin = new Stub();
		thin.surface = (x, z) -> x == WEST + SIZE_X - 1 && z == NORTH + SIZE_Z - 1 ? floor + SIZE_Y : floor + SIZE_Y + 50;
		helper.assertTrue(choose(caveAt(floor, cover), thin).isEmpty(), "a site was accepted although one far corner breaks the surface");
		helper.succeed();
	}

	@GameTest
	public void aCaveSiteIsRefusedInLiquid(final GameTestHelper helper) {
		int floor = 10;
		Placement rule = caveAt(floor, 4);
		Stub dry = new Stub();
		dry.surface = (x, z) -> 200;
		helper.assertTrue(choose(rule, dry).isPresent(), "the dry control was refused");
		BlockPos[] wet = {
			new BlockPos(WEST, floor, NORTH),
			new BlockPos(WEST + SIZE_X - 1, floor + SIZE_Y - 1, NORTH + SIZE_Z - 1),
			new BlockPos(WEST + 8, floor + SIZE_Y / 2, NORTH + 8)
		};
		for (BlockState fluid : new BlockState[] {Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState()}) {
			for (BlockPos spot : wet) {
				Stub stub = new Stub();
				stub.surface = (x, z) -> 200;
				stub.liquid = fluid;
				stub.liquidAt = spot;
				helper.assertTrue(choose(rule, stub).isEmpty(), fluid.getBlock() + " at " + spot + " did not refuse the site");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void aCaveSiteStaysInsideItsDepthRange(final GameTestHelper helper) {
		Placement rule = Placement.cave(-40, 20, 0);
		Stub stub = new Stub();
		stub.surface = (x, z) -> 200;
		for (long seed = 0; seed < 50; seed++) {
			Optional<BlockPos> at = rule.choose(stub, RandomSource.create(seed), WEST, NORTH, SIZE_X, SIZE_Y, SIZE_Z);
			helper.assertTrue(at.isPresent() && at.get().getY() >= -40 && at.get().getY() + SIZE_Y - 1 <= 20, "seed " + seed + " put a cave site outside -40..20: " + at);
		}
		helper.assertTrue(Placement.cave(0, 5, 0).choose(stub, RandomSource.create(1L), WEST, NORTH, SIZE_X, SIZE_Y, SIZE_Z).isEmpty(), "a range too short for the site was accepted");
		helper.succeed();
	}

	@GameTest
	public void aSurfaceSiteStandsOneBlockAboveItsHighestGround(final GameTestHelper helper) {
		Stub hill = new Stub();
		hill.ground = (x, z) -> x == WEST + 8 ? 62 : 60;
		hill.surface = hill.ground;
		Optional<BlockPos> at = choose(Placement.surface(5), hill);
		helper.assertTrue(at.isPresent() && at.get().equals(new BlockPos(WEST, 63, NORTH)), "the floor is not one above the highest ground: " + at);
		helper.succeed();
	}

	@GameTest
	public void aSurfaceSiteRefusesAnyGroundSteeperThanItsSlope(final GameTestHelper helper) {
		int slope = 3;
		Stub exact = new Stub();
		exact.ground = (x, z) -> x == WEST ? 60 + slope : 60;
		exact.surface = exact.ground;
		Stub steep = new Stub();
		steep.ground = (x, z) -> x == WEST ? 60 + slope + 1 : 60;
		steep.surface = steep.ground;
		helper.assertTrue(choose(Placement.surface(slope), exact).isPresent(), "ground exactly at the slope limit was refused");
		helper.assertTrue(choose(Placement.surface(slope), steep).isEmpty(), "ground one block past the slope limit was accepted");
		helper.succeed();
	}

	@GameTest
	public void aSurfaceSiteRefusesWater(final GameTestHelper helper) {
		Stub lake = new Stub();
		lake.surface = (x, z) -> x == WEST + SIZE_X - 1 && z == NORTH ? 63 : 60;
		helper.assertTrue(choose(Placement.surface(3), lake).isEmpty(), "a surface site was accepted over water");
		helper.succeed();
	}

	@GameTest
	public void siteBoundsAreLimitedToTheStructureBox(final GameTestHelper helper) {
		boolean tooWide = refuses(RuinSite.MAX_X + 1, 10, 10);
		boolean tooTall = refuses(10, RuinSite.MAX_Y + 1, 10);
		boolean tooDeep = refuses(10, 10, RuinSite.MAX_Z + 1);
		boolean empty = refuses(0, 10, 10);
		boolean largest = !refuses(RuinSite.MAX_X, RuinSite.MAX_Y, RuinSite.MAX_Z);
		WorldTestSupport.result("siteBounds", "max=" + RuinSite.MAX_X + "x" + RuinSite.MAX_Y + "x" + RuinSite.MAX_Z + " refusesBigger=" + (tooWide && tooTall && tooDeep) + " acceptsLargest=" + largest);
		helper.assertTrue(tooWide && tooTall && tooDeep && empty, "a site outside 64 x 48 x 64 was accepted");
		helper.assertTrue(largest, "the largest allowed box was refused");
		helper.succeed();
	}

	private static boolean refuses(final int x, final int y, final int z) {
		try {
			RuinSite.builder("bounds_probe").bounds(x, y, z);
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}
}
