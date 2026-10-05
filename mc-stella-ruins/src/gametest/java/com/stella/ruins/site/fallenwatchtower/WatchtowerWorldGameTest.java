package com.stella.ruins.site.fallenwatchtower;

import java.util.ArrayDeque;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Acceptance item 1 in the world: the tower is drawn through the real piece path, and a walker with player rules
 * climbs from the ground in front of the door to the captain. The walker reads the world's own collision shapes.
 */
public class WatchtowerWorldGameTest {
	static final int SEEDS = 24;
	static final long BASE = 0x70E21A00L;
	/** The local range the walker reads: below the floor down to the deepest ground, and one block in front of the door. */
	static final int Y_LOW = -6;
	static final int Y_HIGH = WatchtowerPlan.SIZE_Y + 2;

	private static final class Grid {
		final boolean[] solid;
		final int xs = WatchtowerPlan.SIZE_X;
		final int zs = WatchtowerPlan.SIZE_Z + 1;
		final int ys = Y_HIGH - Y_LOW + 1;

		Grid(final TowerTestSupport.Arena arena) {
			this.solid = new boolean[this.xs * this.ys * this.zs];
			for (int y = Y_LOW; y <= Y_HIGH; y++) {
				for (int z = -1; z < WatchtowerPlan.SIZE_Z; z++) {
					for (int x = 0; x < WatchtowerPlan.SIZE_X; x++) {
						BlockPos pos = arena.at(x, y, z);
						BlockState state = arena.level().getBlockState(pos);
						this.solid[index(x, y, z)] = !state.getCollisionShape(arena.level(), pos).isEmpty();
					}
				}
			}
		}

		int index(final int x, final int y, final int z) {
			return ((y - Y_LOW) * this.zs + (z + 1)) * this.xs + x;
		}

		boolean in(final int x, final int y, final int z) {
			return x >= 0 && x < this.xs && z >= -1 && z < WatchtowerPlan.SIZE_Z && y >= Y_LOW && y <= Y_HIGH;
		}

		boolean solid(final int x, final int y, final int z) {
			return in(x, y, z) && this.solid[index(x, y, z)];
		}

		boolean standable(final int x, final int y, final int z) {
			return in(x, y, z) && solid(x, y - 1, z) && !solid(x, y, z) && !solid(x, y + 1, z);
		}

		/** The standing spots a player reaches from one spot: climb 1, fall 3, head room 2. */
		boolean[] reach(final int sx, final int sy, final int sz) {
			boolean[] seen = new boolean[this.solid.length];
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			seen[index(sx, sy, sz)] = true;
			queue.add(new int[] {sx, sy, sz});
			int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			while (!queue.isEmpty()) {
				int[] c = queue.poll();
				for (int[] d : around) {
					int nx = c[0] + d[0];
					int nz = c[2] + d[1];
					for (int dy = 1; dy >= -3; dy--) {
						int ny = c[1] + dy;
						if (!standable(nx, ny, nz)) {
							continue;
						}
						if (dy == 1 && solid(c[0], c[1] + 2, c[2])) {
							continue;
						}
						if (dy < 0) {
							boolean blocked = false;
							for (int y = ny + 2; y <= c[1] + 1; y++) {
								blocked |= solid(nx, y, nz);
							}
							if (blocked) {
								continue;
							}
						}
						if (!seen[index(nx, ny, nz)]) {
							seen[index(nx, ny, nz)] = true;
							queue.add(new int[] {nx, ny, nz});
						}
						break;
					}
				}
			}
			return seen;
		}
	}

	/** Builds one tower and walks it. Returns a failure text, or null when the climb holds. */
	static String climb(final ServerLevel level, final long seed, final Rotation rotation, final int lift) {
		TowerTestSupport.Arena arena = TowerTestSupport.arena(level, rotation, seed, lift);
		try {
			Grid grid = new Grid(arena);
			boolean[] seen = grid.reach(WatchtowerPlan.CENTER, -lift, -1);
			WatchtowerPlan plan = arena.plan();
			int roof = plan.floorY(plan.floors) + 1;
			int[] cap = plan.captainCell();
			if (!seen[grid.index(cap[0], roof, cap[1])]) {
				return "seed " + Long.toHexString(seed) + " " + rotation + " lift " + lift + ": no climb to the captain (" + plan.floors + " floors, " + (plan.counterClockwise ? "ccw" : "cw") + ")";
			}
			for (WatchtowerPlan.Marker marker : plan.markers) {
				if (!seen[grid.index(marker.x(), marker.y(), marker.z())]) {
					return "seed " + Long.toHexString(seed) + " " + rotation + " lift " + lift + ": no way to the " + marker.name() + " marker at " + marker.x() + "," + marker.y() + "," + marker.z();
				}
			}
			return null;
		} finally {
			arena.release();
		}
	}

	private static void climbAll(final GameTestHelper helper, final Rotation rotation) {
		for (int i = 0; i < SEEDS; i++) {
			String failure = climb(helper.getLevel(), BASE + i * 6151L, rotation, 0);
			helper.assertTrue(failure == null, String.valueOf(failure));
		}
		TowerTestSupport.result("worldClimb" + rotation, "seeds=" + SEEDS + " every climb reaches the captain and every marker");
		helper.succeed();
	}

	@GameTest(maxTicks = 2400)
	public void theTowerStandsWithAClimbableStairUnderNoRotation(final GameTestHelper helper) {
		climbAll(helper, Rotation.NONE);
	}

	@GameTest(maxTicks = 2400)
	public void theTowerStandsWithAClimbableStairUnderAQuarterTurn(final GameTestHelper helper) {
		climbAll(helper, Rotation.CLOCKWISE_90);
	}

	@GameTest(maxTicks = 2400)
	public void theTowerStandsWithAClimbableStairUnderAHalfTurn(final GameTestHelper helper) {
		climbAll(helper, Rotation.CLOCKWISE_180);
	}

	@GameTest(maxTicks = 2400)
	public void theTowerStandsWithAClimbableStairUnderAThreeQuarterTurn(final GameTestHelper helper) {
		climbAll(helper, Rotation.COUNTERCLOCKWISE_90);
	}

	@GameTest(maxTicks = 2400)
	public void aTowerOnGroundFourBlocksLowerStillHasAFoundationAndAWayIn(final GameTestHelper helper) {
		for (int i = 0; i < 8; i++) {
			String failure = climb(helper.getLevel(), BASE + 0x1000L + i * 313L, Rotation.values()[i % 4], 4);
			helper.assertTrue(failure == null, String.valueOf(failure));
		}
		TowerTestSupport.result("worldClimbSlope", "eight towers with the floor four blocks over the ground all climb");
		helper.succeed();
	}

	@GameTest(maxTicks = 2400)
	public void aStorageFloorsChestStandsInTheWorldWithTheStoreTable(final GameTestHelper helper) {
		int found = 0;
		for (int i = 0; i < 12 && found < 3; i++) {
			TowerTestSupport.Arena arena = TowerTestSupport.arena(helper.getLevel(), Rotation.values()[i % 4], BASE + 0x2000L + i * 17L);
			try {
				WatchtowerPlan plan = arena.plan();
				for (int k = 0; k < plan.floors; k++) {
					for (int z = 7; z <= 13; z++) {
						for (int x = 7; x <= 13; x++) {
							if (plan.cell(x, plan.floorY(k) + 1, z) != WatchtowerPlan.CHEST) {
								continue;
							}
							BlockPos pos = arena.at(x, plan.floorY(k) + 1, z);
							helper.assertTrue(arena.level().getBlockEntity(pos) instanceof ChestBlockEntity chest && WatchtowerMobs.STORE_LOOT.equals(chest.getLootTable()),
								"the chest at " + pos + " does not roll the store table: " + arena.level().getBlockEntity(pos));
							found++;
						}
					}
				}
			} finally {
				arena.release();
			}
		}
		TowerTestSupport.result("storeChest", "chests=" + found);
		helper.assertTrue(found >= 3, "only " + found + " store chests in 12 towers");
		helper.succeed();
	}
}
