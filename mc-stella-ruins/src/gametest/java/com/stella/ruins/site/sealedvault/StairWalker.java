package com.stella.ruins.site.sealedvault;

import com.stella.ruins.site.sealedvault.VaultTestSupport.Capture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A walker that reads the real collision shape of every block, on a grid of half blocks, so a stair is a half-block
 * step and not a full block. A body is 1.8 high. It climbs 0.6 on foot, up to 1.0 by a jump, and drops up to 3.
 */
final class StairWalker {
	private static final double BODY = 1.8;
	private static final double STEP = 0.6;
	private static final double JUMP = 1.0;
	private static final double DROP = 3.0;
	private static final double EPS = 1.0E-4;
	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** A standing spot: a half-block column and a height in hundredths. */
	record Spot(int sx, int sz, int h100) {
		double height() {
			return this.h100 / 100.0;
		}

		BlockPos block() {
			return new BlockPos(Math.floorDiv(this.sx, 2), (int) Math.floor(height() - EPS), Math.floorDiv(this.sz, 2));
		}
	}

	private final Capture capture;
	private final double ceiling;

	StairWalker(final Capture capture) {
		this(capture, Double.MAX_VALUE);
	}

	/** A walker that never stands at or above a height, so a walk on the lower level cannot use a stair. */
	StairWalker(final Capture capture, final double ceiling) {
		this.capture = capture;
		this.ceiling = ceiling;
	}

	private static double centre(final int sub) {
		return (Math.floorMod(sub, 2) + 0.5) / 2.0;
	}

	private List<AABB> boxes(final int sx, final int sz, final int y) {
		BlockPos pos = new BlockPos(Math.floorDiv(sx, 2), y, Math.floorDiv(sz, 2));
		BlockState state = this.capture.at(pos);
		double cx = centre(sx);
		double cz = centre(sz);
		List<AABB> hits = new ArrayList<>();
		for (AABB box : state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
			if (box.minX < cx && cx < box.maxX && box.minZ < cz && cz < box.maxZ) {
				hits.add(box.move(0.0, y, 0.0));
			}
		}
		return hits;
	}

	/** True when something solid stands in the column between two heights. */
	private boolean blocked(final int sx, final int sz, final double low, final double high) {
		for (int y = (int) Math.floor(low); y <= (int) Math.floor(high); y++) {
			for (AABB box : boxes(sx, sz, y)) {
				if (box.maxY > low + EPS && box.minY < high - EPS) {
					return true;
				}
			}
		}
		return false;
	}

	/** Every height at which a body stands in a column with its full height free above. */
	private List<Integer> surfaces(final int sx, final int sz) {
		int floorY = this.capture.frame().min().getY();
		int topY = this.capture.frame().box().maxY();
		List<Integer> found = new ArrayList<>();
		for (int y = floorY; y <= topY; y++) {
			for (AABB box : boxes(sx, sz, y)) {
				double top = box.maxY;
				int key = (int) Math.round(top * 100.0);
				if (!found.contains(key) && !blocked(sx, sz, top, top + BODY)) {
					found.add(key);
				}
			}
		}
		return found;
	}

	/** The spot of a body that stands at a block, or null when nobody can stand there. */
	Spot spotAt(final BlockPos feet) {
		int sx = feet.getX() * 2;
		int sz = feet.getZ() * 2;
		for (int h : surfaces(sx, sz)) {
			if (Math.abs(h / 100.0 - feet.getY()) < 0.01) {
				return new Spot(sx, sz, h);
			}
		}
		return null;
	}

	Set<Spot> reach(final Spot start) {
		Set<Spot> seen = new HashSet<>();
		Deque<Spot> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			Spot at = queue.poll();
			for (int[] side : SIDES) {
				int nx = at.sx() + side[0];
				int nz = at.sz() + side[1];
				for (int h : surfaces(nx, nz)) {
					Spot next = new Spot(nx, nz, h);
					if (!seen.contains(next) && canMove(at, next)) {
						seen.add(next);
						queue.add(next);
					}
				}
			}
		}
		return seen;
	}

	private boolean canMove(final Spot from, final Spot to) {
		double rise = to.height() - from.height();
		if (to.height() >= this.ceiling) {
			return false;
		}
		if (rise > JUMP + EPS || rise < -DROP - EPS) {
			return false;
		}
		if (rise > STEP + EPS && blocked(from.sx(), from.sz(), from.height() + BODY, to.height() + BODY)) {
			return false;
		}
		if (rise < 0.0 && blocked(to.sx(), to.sz(), from.height(), from.height() + BODY)) {
			return false;
		}
		return true;
	}

	/** True when the reach holds a standing spot on the floor of a room, inside its 6 x 6 blocks. */
	boolean reachesRoom(final Set<Spot> reach, final int level, final int cell) {
		int ax = VaultPlan.cellMinX(cell % VaultPlan.GRID);
		int az = VaultPlan.cellMinZ(cell / VaultPlan.GRID);
		Set<Long> floor = new HashSet<>();
		for (int u = 0; u <= 5; u++) {
			for (int v = 0; v <= 5; v++) {
				floor.add(this.capture.world(ax + u, VaultPlan.stand(level), az + v).asLong());
			}
		}
		for (Spot spot : reach) {
			BlockPos feet = new BlockPos(spot.block().getX(), (int) Math.round(spot.height()), spot.block().getZ());
			if (floor.contains(feet.asLong())) {
				return true;
			}
		}
		return false;
	}
}
