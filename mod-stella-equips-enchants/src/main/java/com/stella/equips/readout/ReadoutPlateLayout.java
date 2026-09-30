package com.stella.equips.readout;

import java.util.ArrayList;
import java.util.List;

/**
 * The badge and name as flat rectangles in text pixels, y pointing down. It holds no client
 * class, so a headless test reads the geometry. No two rectangles overlap, so the renderer needs
 * no depth ordering between them. Angles run clockwise from the top of the badge.
 */
public final class ReadoutPlateLayout {
	public static final int DISC_RADIUS = 8;
	public static final int HP_RING_OUTER = 10;
	public static final int ABSORPTION_RING_OUTER = 12;
	public static final int BADGE_CENTER = ABSORPTION_RING_OUTER;
	public static final int BADGE_SIZE = 2 * ABSORPTION_RING_OUTER;
	public static final int BADGE_GAP = 4;
	public static final int NAME_HEIGHT = 9;
	public static final float MIN_ARC = 0.02F;
	public static final int HP_COLOR = 0xFFE03030;
	public static final int ABSORPTION_COLOR = 0xFFFFD83A;
	public static final int HP_TRACK_COLOR = 0xFF202020;
	public static final int DISC_COLOR = 0xC0101010;
	public static final int BACKGROUND_COLOR = 0x80606060;
	public static final int BACKGROUND_PADDING = 3;

	public record Rect(int x0, int y0, int x1, int y1, int color) {
		public int width() {
			return this.x1 - this.x0;
		}

		public int height() {
			return this.y1 - this.y0;
		}

		public boolean overlaps(final Rect other) {
			return this.x0 < other.x1 && other.x0 < this.x1 && this.y0 < other.y1 && other.y0 < this.y1;
		}
	}

	/**
	 * `bounds` holds the padded plate. `background` tiles it around the badge, so no drawn
	 * rectangle overlaps another. `nameBounds` is the name box and is not drawn itself.
	 */
	public record Plan(
		Rect bounds,
		int numberCenterX,
		int numberCenterY,
		int nameX,
		int nameY,
		Rect nameBounds,
		List<Rect> badge,
		List<Rect> background
	) {
		/** Every rectangle the plate draws. */
		public List<Rect> all() {
			List<Rect> all = new ArrayList<>(this.background);
			all.addAll(this.badge);
			return all;
		}
	}

	private ReadoutPlateLayout() {
	}

	/** The share of the circumference the red arc covers. A live mob keeps a small arc. */
	public static float hpFraction(final float health, final float maxHealth) {
		if (maxHealth <= 0.0F || health <= 0.0F) {
			return 0.0F;
		}

		return Math.max(MIN_ARC, Math.min(1.0F, health / maxHealth));
	}

	/** The share the yellow arc covers, capped at a full circle. Zero absorption gives zero. */
	public static float absorptionFraction(final float absorption, final float maxHealth) {
		if (absorption <= 0.0F) {
			return 0.0F;
		}

		if (maxHealth <= 0.0F) {
			return 1.0F;
		}

		return Math.max(MIN_ARC, Math.min(1.0F, absorption / maxHealth));
	}

	public static Plan plan(final float health, final float maxHealth, final float absorption, final int nameWidth) {
		float hp = hpFraction(health, maxHealth);
		float shield = absorptionFraction(absorption, maxHealth);
		int nameX = BADGE_SIZE + BADGE_GAP;
		int nameY = BADGE_CENTER - NAME_HEIGHT / 2;
		Rect nameBounds = new Rect(nameX, nameY, nameX + nameWidth, nameY + NAME_HEIGHT, 0);
		Rect bounds = new Rect(
			-BACKGROUND_PADDING, -BACKGROUND_PADDING, nameBounds.x1() + BACKGROUND_PADDING,
			BADGE_SIZE + BACKGROUND_PADDING, BACKGROUND_COLOR
		);
		List<Rect> badge = badge(hp, shield);
		return new Plan(bounds, BADGE_CENTER, BADGE_CENTER, nameX, nameY, nameBounds, badge, background(bounds, badge));
	}

	/** Fills the gaps between the badge cells of each row, so the background never overlaps them. */
	private static List<Rect> background(final Rect bounds, final List<Rect> badge) {
		List<Rect> tiles = new ArrayList<>();

		for (int y = bounds.y0(); y < bounds.y1(); y++) {
			final int row = y;
			List<Rect> cells = badge.stream().filter(rect -> rect.y0() == row).sorted(java.util.Comparator.comparingInt(Rect::x0)).toList();
			int cursor = bounds.x0();

			for (Rect cell : cells) {
				if (cell.x0() > cursor) {
					tiles.add(new Rect(cursor, y, cell.x0(), y + 1, BACKGROUND_COLOR));
				}

				cursor = cell.x1();
			}

			if (cursor < bounds.x1()) {
				tiles.add(new Rect(cursor, y, bounds.x1(), y + 1, BACKGROUND_COLOR));
			}
		}

		return tiles;
	}

	/** Runs of one-pixel cells per row, merged by color. */
	private static List<Rect> badge(final float hp, final float shield) {
		List<Rect> rects = new ArrayList<>();

		for (int y = 0; y < BADGE_SIZE; y++) {
			int runStart = 0;
			int runColor = 0;

			for (int x = 0; x <= BADGE_SIZE; x++) {
				int color = x < BADGE_SIZE ? cellColor(x, y, hp, shield) : 0;

				if (color != runColor) {
					if (runColor != 0) {
						rects.add(new Rect(runStart, y, x, y + 1, runColor));
					}

					runStart = x;
					runColor = color;
				}
			}
		}

		return rects;
	}

	/** Zero means the cell stays empty. */
	private static int cellColor(final int x, final int y, final float hp, final float shield) {
		double dx = x + 0.5 - BADGE_CENTER;
		double dy = y + 0.5 - BADGE_CENTER;
		double distance = Math.sqrt(dx * dx + dy * dy);

		if (distance >= ABSORPTION_RING_OUTER) {
			return 0;
		}

		double turn = Math.atan2(dx, -dy) / (2.0 * Math.PI);
		double angle = turn < 0.0 ? turn + 1.0 : turn;

		if (distance < DISC_RADIUS) {
			return DISC_COLOR;
		}

		if (distance < HP_RING_OUTER) {
			return angle < hp ? HP_COLOR : HP_TRACK_COLOR;
		}

		return angle < shield ? ABSORPTION_COLOR : 0;
	}
}
