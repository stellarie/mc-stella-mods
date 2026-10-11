package com.stella.tower;

import java.util.List;
import java.util.Random;

/**
 * How big a floor is. A biome floor takes one of five shapes, drawn from the tower's seed and the floor number alone:
 * 64 by 64, 64 by 96, 96 by 96, 96 by 128 and 128 by 128 blocks, a long shape lying along x or along z. The height is
 * the same on all of them. The first floors are small and the larger shapes come more often higher up. A great hall,
 * the floors of a boss, and the destroyed castle keep the base size.
 */
public final class TowerSizes {
	/** The width along x and the depth along z of a floor's room, inside its walls. */
	public record Size(int width, int depth) {
		public int area() {
			return this.width * this.depth;
		}

		/** The floor's area against the base floor's: the factor mobs, chests and props scale by. */
		public double factor() {
			return this.area() / (double) (TowerLayout.SIZE * TowerLayout.SIZE);
		}

		/** The same shape with its long side along x: how the shape is named. */
		public Size canonical() {
			return this.width <= this.depth ? this : new Size(this.depth, this.width);
		}
	}

	public static final Size BASE = new Size(TowerLayout.SIZE, TowerLayout.SIZE);
	/** The five shapes, each named by its short side first. */
	public static final List<Size> SHAPES = List.of(BASE, new Size(64, 96), new Size(96, 96), new Size(96, 128), new Size(128, 128));
	/** The floor from which a larger shape may come, and the share of floors it takes there. */
	public static final int LARGE_FROM = 5;
	public static final double LARGE_PER_FLOOR = 0.02;
	public static final double LARGE_MOST = 0.65;
	private static final long SALT = 0x53495A45L;

	private TowerSizes() {
	}

	/** The share of floors at this number that take a larger shape than the base. */
	public static double largeShare(final int floor) {
		return Math.max(0.0, Math.min(LARGE_MOST, (floor - LARGE_FROM + 1) * LARGE_PER_FLOOR));
	}

	/** The size of a floor in a tower of this seed. Pure. */
	public static Size of(final long seed, final int floor) {
		if (floor <= 0 || TowerPlan.drawnByTheOlderGenerator(floor) || floor % TowerElites.BOSS_EVERY == 0
			|| FloorBiomes.of(seed, floor) == FloorBiome.DESTROYED_CASTLE) {
			return BASE;
		}
		Random random = new Random(TowerPlan.mix(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L)));
		if (random.nextDouble() >= largeShare(floor)) {
			return BASE;
		}
		Size shape = SHAPES.get(1 + random.nextInt(SHAPES.size() - 1));
		return shape.width() != shape.depth() && random.nextBoolean() ? new Size(shape.depth(), shape.width()) : shape;
	}
}
