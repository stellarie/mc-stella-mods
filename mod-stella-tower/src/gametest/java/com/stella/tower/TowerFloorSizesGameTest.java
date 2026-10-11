package com.stella.tower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The sizes a floor of the tower takes (T16): which come, how often, and a floor built to each, walls, roof and portal. */
public class TowerFloorSizesGameTest {
	/** The floors the size draw is sampled over, from the first the test world draws as biome floors. */
	private static final int SAMPLED = 1200;

	private static String name(final TowerSizes.Size size) {
		return size.width() + "x" + size.depth();
	}

	/**
	 * Catches a draw that never gives a size, favours one, or lets a small tower floor come as big as a high one: over a
	 * long run of floors all five shapes come, a long one lies along x as often as along z, the first floors and the boss
	 * floors keep the base size, and the share of larger floors is lower low in the tower than high in it.
	 */
	@GameTest
	public void allFiveSizesComeInBothTurnsAndTheFirstFloorsStaySmall(final GameTestHelper helper) {
		long seed = TowerTestSupport.tower(helper).getSeed();
		Map<String, Integer> seen = new HashMap<>();
		int alongX = 0;
		int alongZ = 0;
		int bossLarge = 0;
		int castleLarge = 0;
		int highLarge = 0;
		int highCounted = 0;
		for (int floor = TowerTestSupport.BIOME_FLOORS_FROM; floor < TowerTestSupport.BIOME_FLOORS_FROM + SAMPLED; floor++) {
			TowerSizes.Size size = TowerSizes.of(seed, floor);
			boolean large = size.area() > TowerLayout.SIZE * TowerLayout.SIZE;
			seen.merge(name(size.canonical()), 1, Integer::sum);
			alongX += size.width() > size.depth() ? 1 : 0;
			alongZ += size.depth() > size.width() ? 1 : 0;
			bossLarge += floor % TowerElites.BOSS_EVERY == 0 && large ? 1 : 0;
			castleLarge += FloorBiomes.of(seed, floor) == FloorBiome.DESTROYED_CASTLE && large ? 1 : 0;
			if (floor % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, floor) != FloorBiome.DESTROYED_CASTLE) {
				highCounted++;
				highLarge += large ? 1 : 0;
			}
		}
		int firstLarge = 0;
		int lowLarge = 0;
		int lowCounted = 0;
		for (int floor = 1; floor <= 40; floor++) {
			boolean large = TowerSizes.of(seed, floor).area() > TowerLayout.SIZE * TowerLayout.SIZE;
			firstLarge += floor < TowerSizes.LARGE_FROM && large ? 1 : 0;
			if (floor % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, floor) != FloorBiome.DESTROYED_CASTLE) {
				lowCounted++;
				lowLarge += floor <= 12 && large ? 1 : 0;
			}
		}
		double highShare = highLarge / (double) highCounted;
		TowerTestSupport.result("floorSizes", "seen=" + seen + " alongX=" + alongX + " alongZ=" + alongZ + " highLargeShare=" + highShare
			+ " firstLarge=" + firstLarge + " bossLarge=" + bossLarge + " castleLarge=" + castleLarge + " lowLargeFloorsUpTo12=" + lowLarge);
		for (TowerSizes.Size shape : TowerSizes.SHAPES) {
			helper.assertTrue(seen.getOrDefault(name(shape), 0) >= SAMPLED / 40, "the " + name(shape) + " floor came " + seen.getOrDefault(name(shape), 0) + " times in " + SAMPLED + ": " + seen);
		}
		helper.assertTrue(seen.size() == TowerSizes.SHAPES.size(), "a size beyond the five came: " + seen);
		helper.assertTrue(alongX >= SAMPLED / 20 && alongZ >= SAMPLED / 20, "the long shapes lie along x " + alongX + " times and along z " + alongZ + " times");
		helper.assertTrue(firstLarge == 0, firstLarge + " of the first floors are larger than the base");
		helper.assertTrue(bossLarge == 0 && castleLarge == 0, "a boss floor came larger " + bossLarge + " times and a castle " + castleLarge);
		helper.assertTrue(highShare >= TowerSizes.LARGE_MOST - 0.1 && highShare <= TowerSizes.LARGE_MOST + 0.1,
			"high in the tower " + highShare + " of the floors are larger, not about " + TowerSizes.LARGE_MOST);
		helper.assertTrue(lowLarge < lowCounted * 0.5, lowLarge + " of the first twelve floors are larger: the low floors are not weighted small");
		helper.succeed();
	}

	/** A fresh biome floor of a size, with the turn that gives it this width. */
	private static int floorOf(final long seed, final int width, final int depth) {
		for (int block = 0; block < 12; block++) {
			try {
				return TowerTestSupport.freshBiomeFloorWhere(f -> {
					TowerSizes.Size size = TowerSizes.of(seed, f);
					return size.width() == width && size.depth() == depth && f % TowerElites.BOSS_EVERY != 0;
				});
			} catch (IllegalStateException e) {
				// This block of floors holds none of the shape: the next block is tried.
			}
		}
		throw new IllegalStateException("no floor of " + width + "x" + depth + " in twelve blocks of floors");
	}

	/**
	 * Catches a floor built to the wrong size: for each shape, and the long ones both ways, a floor is built and its walls,
	 * bedrock and roof stand at the room's own edges, the void lies past the walls, the portal is lit inside the room, and
	 * the arrival walks to it over the blocks as built. A sentinel in the gap to the next floor proves the build stops
	 * at the wall.
	 */
	@GameTest(maxTicks = 2400)
	public void everySizeBuildsWallsRoofAndPortalThatMatchItsSize(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int[][] shapes = {{64, 64}, {64, 96}, {96, 64}, {96, 96}, {96, 128}, {128, 96}, {128, 128}};
		StringBuilder report = new StringBuilder();
		List<String> faults = new ArrayList<>();
		for (int[] shape : shapes) {
			int floor = floorOf(seed, shape[0], shape[1]);
			TowerPlan plan = TowerPlan.of(seed, floor);
			BlockPos origin = plan.origin();
			int width = plan.width();
			int depth = plan.depth();
			String name = shape[0] + "x" + shape[1] + "#" + floor;
			if (width != shape[0] || depth != shape[1]) {
				faults.add(name + " plan is " + width + "x" + depth);
				continue;
			}
			BlockPos sentinel = origin.offset(width + 20, 5, depth + 20);
			BlockPos farSentinel = origin.offset(TowerLayout.CELL - 4, 5, TowerLayout.CELL - 4);
			tower.setBlock(sentinel, Blocks.GLASS.defaultBlockState(), 2);
			tower.setBlock(farSentinel, Blocks.GLASS.defaultBlockState(), 2);
			TowerFloors.ensure(tower, floor);
			int wallGaps = 0;
			int roofGaps = 0;
			int bedrockGaps = 0;
			for (int y = plan.low() + 1; y <= plan.roofY(); y += 5) {
				for (int z = -1; z <= depth; z += 3) {
					wallGaps += tower.getBlockState(origin.offset(-1, y, z)).is(TowerBlocks.TOWER_WALL) ? 0 : 1;
					wallGaps += tower.getBlockState(origin.offset(width, y, z)).is(TowerBlocks.TOWER_WALL) ? 0 : 1;
				}
				for (int x = -1; x <= width; x += 3) {
					wallGaps += tower.getBlockState(origin.offset(x, y, -1)).is(TowerBlocks.TOWER_WALL) ? 0 : 1;
					wallGaps += tower.getBlockState(origin.offset(x, y, depth)).is(TowerBlocks.TOWER_WALL) ? 0 : 1;
				}
			}
			for (int x = 0; x < width; x += 2) {
				for (int z = 0; z < depth; z += 2) {
					BlockState roof = tower.getBlockState(origin.offset(x, plan.roofY(), z));
					roofGaps += roof.isSolidRender() && !roof.is(Blocks.BARRIER) ? 0 : 1;
					bedrockGaps += tower.getBlockState(origin.offset(x, plan.low(), z)).is(Blocks.BEDROCK) ? 0 : 1;
				}
			}
			roofGaps += tower.getBlockState(origin.offset(width - 1, plan.roofY(), depth - 1)).isSolidRender() ? 0 : 1;
			boolean voidBeyond = tower.getBlockState(origin.offset(width + 1, 5, depth / 2)).isAir() && tower.getBlockState(origin.offset(width / 2, 5, depth + 1)).isAir();
			boolean sentinelsHold = tower.getBlockState(sentinel).is(Blocks.GLASS) && tower.getBlockState(farSentinel).is(Blocks.GLASS);
			int unlit = 0;
			for (BlockPos inside : plan.portalInside()) {
				boolean inRoom = inside.getX() >= origin.getX() && inside.getX() < origin.getX() + width && inside.getZ() >= origin.getZ() && inside.getZ() < origin.getZ() + depth;
				unlit += inRoom && tower.getBlockState(inside).is(TowerBlocks.TOWER_PORTAL) ? 0 : 1;
			}
			int[] dist = TowerWalk.distances(TowerWalk.world(tower, floor), plan.arrivalX(), 0, plan.arrivalZ());
			boolean reaches = false;
			for (int[] goal : TowerVolume.portalGoals(plan)) {
				reaches |= dist[TowerWalk.index(goal[0], goal[1], goal[2])] >= 0;
			}
			boolean arrivalIn = plan.arrivalX() >= 0 && plan.arrivalX() < width && plan.arrivalZ() >= 0 && plan.arrivalZ() < depth;
			report.append(name).append(" wallGaps=").append(wallGaps).append(" roofGaps=").append(roofGaps).append(" bedrockGaps=").append(bedrockGaps)
				.append(" void=").append(voidBeyond).append(" sentinels=").append(sentinelsHold).append(" unlit=").append(unlit).append(" reaches=").append(reaches).append("; ");
			if (wallGaps != 0 || roofGaps != 0 || bedrockGaps != 0 || !voidBeyond || !sentinelsHold || unlit != 0 || !reaches || !arrivalIn) {
				faults.add(name + " wallGaps=" + wallGaps + " roofGaps=" + roofGaps + " bedrockGaps=" + bedrockGaps + " void=" + voidBeyond
					+ " sentinels=" + sentinelsHold + " unlit=" + unlit + " reaches=" + reaches + " arrivalIn=" + arrivalIn);
			}
		}
		TowerTestSupport.result("sizesBuilt", report.toString());
		helper.assertTrue(faults.isEmpty(), "floors built wrong: " + faults);
		helper.succeed();
	}
}
