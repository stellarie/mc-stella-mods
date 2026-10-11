package com.stella.tower.colosseum;

/**
 * Where an arrow lands. The step is the arrow's own: it moves, then loses a hundredth of its speed, then falls by 0.05
 * a tick. A guardian aims by asking for the elevation that lands the arrow on a spot.
 */
final class Ballistics {
	private static final double DRAG = 0.99;
	private static final double GRAVITY = 0.05;
	private static final int MAX_TICKS = 400;
	private static final double STEP_DEGREES = 0.25;

	private Ballistics() {
	}

	/**
	 * How far along the ground an arrow shot at this speed and elevation has gone when it falls through the height
	 * {@code rise} over its start; negative if it never gets there.
	 */
	static double range(final double speed, final double elevation, final double rise) {
		double x = 0.0;
		double y = 0.0;
		double vx = speed * Math.cos(elevation);
		double vy = speed * Math.sin(elevation);
		for (int tick = 0; tick < MAX_TICKS; tick++) {
			double nx = x + vx;
			double ny = y + vy;
			if (vy < 0.0 && ny <= rise) {
				double along = (y - rise) / (y - ny);
				return x + (nx - x) * along;
			}
			x = nx;
			y = ny;
			vx *= DRAG;
			vy *= DRAG;
			vy -= GRAVITY;
		}
		return -1.0;
	}

	/**
	 * The elevation in radians that lands the arrow {@code distance} blocks out and {@code rise} blocks up, on the
	 * flat arc or the high one; the nearest it can reach if the spot is out of range.
	 */
	static double elevation(final double speed, final double distance, final double rise, final boolean high) {
		double from = high ? 45.0 : -20.0;
		double to = high ? 89.5 : 50.0;
		double best = Math.toRadians(high ? 70.0 : 0.0);
		double bestError = Double.MAX_VALUE;
		for (double degrees = from; degrees <= to; degrees += STEP_DEGREES) {
			double elevation = Math.toRadians(degrees);
			double reach = range(speed, elevation, rise);
			if (reach < 0.0) {
				continue;
			}
			double error = Math.abs(reach - distance);
			if (error < bestError) {
				bestError = error;
				best = elevation;
			}
		}
		return best;
	}
}
