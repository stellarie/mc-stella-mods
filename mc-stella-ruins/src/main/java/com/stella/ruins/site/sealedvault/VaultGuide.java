package com.stella.ruins.site.sealedvault;

import com.stella.ruins.site.sealedvault.VaultPlan.Cell;
import com.stella.ruins.world.RuinFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tells the Warden which way the maze goes. The vanilla path search gives up long before it crosses a whole maze, so
 * the Warden reads the plan of its own vault, finds the route from cell to cell, and walks to the next cell on that
 * route. Each leg is short, so the vanilla search always finds it.
 */
public final class VaultGuide {
	private static final int CACHE_LIMIT = 32;
	private static final Map<Long, VaultPlan> PLANS = new HashMap<>();

	private VaultGuide() {
	}

	/** The plan of a placed vault. It is the same plan the layout drew, because it comes from the same seed. */
	public static synchronized VaultPlan planOf(final RuinFrame frame) {
		if (PLANS.size() > CACHE_LIMIT) {
			PLANS.clear();
		}
		return PLANS.computeIfAbsent(frame.seed(), seed -> VaultPlan.derive(RandomSource.create(seed)));
	}

	/** The local spot of a world block. */
	public static int[] toLocal(final RuinFrame frame, final BlockPos pos) {
		int dx = pos.getX() - frame.min().getX();
		int dz = pos.getZ() - frame.min().getZ();
		int y = pos.getY() - frame.min().getY();
		return switch (frame.turns()) {
			case 1 -> new int[] {dz, y, frame.sizeZ() - 1 - dx};
			case 2 -> new int[] {frame.sizeX() - 1 - dx, y, frame.sizeZ() - 1 - dz};
			case 3 -> new int[] {frame.sizeX() - 1 - dz, y, dx};
			default -> new int[] {dx, y, dz};
		};
	}

	/** The cell a local spot lies in, or the nearest cell for a spot in a wall line or the shell. */
	public static Cell cellOf(final int[] local) {
		int i = clamp((local[0] - VaultPlan.OFFSET) / VaultPlan.PITCH);
		int j = clamp((local[2] - VaultPlan.EXTENSION - VaultPlan.OFFSET) / VaultPlan.PITCH);
		return new Cell(local[1] >= VaultLayout.SLAB + 1 ? 1 : 0, j * VaultPlan.GRID + i);
	}

	private static int clamp(final int cell) {
		return Math.max(0, Math.min(VaultPlan.GRID - 1, cell));
	}

	/**
	 * The cells from one cell to another through the maze, both ends included, or null when the Warden cannot walk
	 * there. The spiral to the inner vault is too tight for the Warden, so a target in the inner vault is reached at
	 * the core room.
	 */
	public static @Nullable List<Cell> route(final VaultPlan plan, final Cell from, final Cell target) {
		Cell to = target.level() == 0 && target.cell() == VaultPlan.CENTER_CELL ? new Cell(1, VaultPlan.CENTER_CELL) : target;
		if (from.equals(to)) {
			return List.of(from);
		}
		Map<Cell, Cell> parent = new HashMap<>();
		List<Cell> queue = new ArrayList<>();
		queue.add(from);
		parent.put(from, from);
		for (int head = 0; head < queue.size(); head++) {
			Cell at = queue.get(head);
			for (Cell next : plan.neighbors(at)) {
				boolean spiral = at.cell() == VaultPlan.CENTER_CELL && next.cell() == VaultPlan.CENTER_CELL;
				if (spiral || parent.containsKey(next)) {
					continue;
				}
				parent.put(next, at);
				queue.add(next);
			}
		}
		if (!parent.containsKey(to)) {
			return null;
		}
		List<Cell> path = new ArrayList<>();
		for (Cell at = to; !at.equals(from); at = parent.get(at)) {
			path.add(at);
		}
		path.add(from);
		java.util.Collections.reverse(path);
		return path;
	}

	/**
	 * The world spot the Warden should walk to next, or null when it should walk straight at the target: when both
	 * stand in one cell, or when no route exists.
	 */
	public static @Nullable Vec3 waypoint(final RuinFrame frame, final Vec3 from, final Vec3 target) {
		VaultPlan plan = planOf(frame);
		Cell here = cellOf(toLocal(frame, BlockPos.containing(from)));
		Cell there = cellOf(toLocal(frame, BlockPos.containing(target)));
		List<Cell> path = route(plan, here, there);
		if (path == null || path.size() < 2) {
			return null;
		}
		Cell next = path.get(1);
		int ax = VaultPlan.cellMinX(next.i());
		int az = VaultPlan.cellMinZ(next.j());
		int x = ax + 4;
		int z = az + 4;
		if (next.cell() == here.cell() && next.level() != here.level()) {
			x = ax + 3;
			z = next.level() == 1 ? az + 5 : az + 1;
		}
		BlockPos world = frame.toWorld(x, VaultPlan.stand(next.level()), z);
		return new Vec3(world.getX() + 0.5, world.getY(), world.getZ() + 0.5);
	}
}
