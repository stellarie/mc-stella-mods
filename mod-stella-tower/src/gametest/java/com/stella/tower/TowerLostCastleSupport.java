package com.stella.tower;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Castles for the tests: a castle built over a hill of stone in an empty spot of the tower level, and a
 * walker that finds every spot a body reaches. Every castle goes down into the hill, as a real one does:
 * its top ceiling lies {@link LostCastleGrid#COVER} blocks under the lowest ground of its footprint.
 */
final class TowerLostCastleSupport {
	/** The ground of an arena: a hill {@code 0} to {@link LostCastleGrid#MAX_SLOPE} blocks over {@link #BASE}. */
	static final int BASE = 200;
	static final int LOWEST = BASE;
	static final int HIGHEST = BASE + LostCastleGrid.MAX_SLOPE;
	/** The farthest a walker drops in one step. */
	static final int DROP = 3;

	private TowerLostCastleSupport() {
	}

	/** The top solid block of the hill over a column. */
	static int hill(final int x, final int z) {
		return BASE + Math.floorMod(x * 7 + z * 3, LostCastleGrid.MAX_SLOPE + 1);
	}

	/** A castle over a hill, built whole. */
	record Arena(ServerLevel level, LostRoomPiece piece) {
		BoundingBox box() {
			return this.piece.getBoundingBox();
		}

		/** The world y of a floor's feet, from the top floor, 0. */
		int feet(final int floor) {
			return box().minY() + LostCastleGrid.feet(this.piece.floors(), floor);
		}

		/** The world y of the slab a floor stands on. */
		int slab(final int floor) {
			return box().minY() + LostCastleGrid.slabY(this.piece.floors(), floor);
		}

		BlockState at(final int x, final int y, final int z) {
			return this.level.getBlockState(this.piece.worldPos(x, y, z));
		}

		/** The plan's floor, from the top, 0. */
		LostCastlePlan.Level level(final int floor) {
			return this.piece.plan().levels().get(floor);
		}

		/** The nth column of a floor that a body must reach, at the floor's feet, in the world. */
		BlockPos key(final int floor, final int n) {
			List<LostCastlePlan.Cell> keys = level(floor).keys();
			LostCastlePlan.Cell cell = keys.get(n % keys.size());
			return spot(floor, cell.x(), cell.z());
		}

		/** A spot of the piece's frame at a floor's feet, in the world. */
		BlockPos spot(final int floor, final int x, final int z) {
			return this.piece.worldPos(x, LostCastleGrid.feet(this.piece.floors(), floor), z);
		}
	}

	/** The start of the next free spot of the tower level, a multiple of 64 along x and far north along z. */
	static int[] freshSpot() {
		int area = TowerTestSupport.freshArea();
		return new int[] {Math.floorMod(area, 4096) * 64, -200_000 - Math.floorMod(area / 4096, 1000) * 64};
	}

	/** The piece for a spot, with its hill laid and nothing else. */
	static LostRoomPiece site(final ServerLevel level, final int west, final int north, final Direction facing, final LostCastlePalette palette,
		final int floors) {
		LostRoomPiece piece = LostRoomPiece.over(west, north, facing, palette, floors, LOWEST, HIGHEST);
		ground(level, piece);
		return piece;
	}

	/** Lays the hill: stone from the cover over the top ceiling up to the hill, and air above it. */
	static void ground(final ServerLevel level, final LostRoomPiece piece) {
		BoundingBox box = piece.getBoundingBox();
		int cover = box.minY() + LostCastleGrid.topY(piece.floors()) + 1;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = cover; y <= box.maxY() + 2; y++) {
					level.setBlock(new BlockPos(x, y, z), y <= hill(x, z) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	static Arena castle(final GameTestHelper helper, final LostCastlePalette palette, final Direction facing, final int floors) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int[] spot = freshSpot();
		LostRoomPiece piece = site(tower, spot[0], spot[1], facing, palette, floors);
		piece.build(tower, BoundingBox.infinite());
		return new Arena(tower, piece);
	}

	// ---------------------------------------------------------------- walking

	/** Whether a body may be in this cell: no lava, no collision, or a carpet's at the feet; doors open. */
	static boolean passable(final ServerLevel level, final BlockPos at, final boolean feet) {
		BlockState state = level.getBlockState(at);
		if (state.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		if (state.getBlock() instanceof DoorBlock) {
			return true;
		}
		VoxelShape shape = state.getCollisionShape(level, at);
		return shape.isEmpty() || feet && shape.max(Direction.Axis.Y) <= 0.125;
	}

	/** Whether a block holds a walker up: a collision at least half high and at most one; never lava or a door. */
	static boolean support(final ServerLevel level, final BlockPos at) {
		BlockState state = level.getBlockState(at);
		if (state.getFluidState().is(FluidTags.LAVA) || state.getBlock() instanceof DoorBlock) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, at);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.5 && shape.max(Direction.Axis.Y) <= 1.0;
	}

	/** Feet here: two blocks of room and a block to stand on. */
	static boolean standable(final ServerLevel level, final BlockPos feet) {
		return passable(level, feet, true) && passable(level, feet.above(), false) && support(level, feet.below());
	}

	/**
	 * Every feet spot a walker reaches from a start: a step across, a jump of one with a third block of room
	 * over the take-off, or a drop of up to {@value #DROP}. The walk keeps within the box, two out.
	 */
	static Set<BlockPos> walk(final ServerLevel level, final BoundingBox box, final BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos here = queue.poll();
			for (Direction way : Direction.Plane.HORIZONTAL) {
				BlockPos next = here.relative(way);
				if (next.getX() < box.minX() - 2 || next.getX() > box.maxX() + 2 || next.getZ() < box.minZ() - 2 || next.getZ() > box.maxZ() + 2
					|| next.getY() < box.minY() || next.getY() > box.maxY() + 2) {
					continue;
				}
				BlockPos to = null;
				if (standable(level, next)) {
					to = next;
				} else if (standable(level, next.above()) && passable(level, here.above(2), false)) {
					to = next.above();
				} else if (passable(level, next, true) && passable(level, next.above(), false)) {
					for (int drop = 1; drop <= DROP; drop++) {
						BlockPos down = next.below(drop);
						if (standable(level, down)) {
							to = down;
							break;
						}
						if (!passable(level, down, false)) {
							break;
						}
					}
				}
				if (to != null && seen.add(to.immutable())) {
					queue.add(to.immutable());
				}
			}
		}
		return seen;
	}
}
