package com.stella.equips.readout;

import java.util.ArrayList;
import java.util.List;

/**
 * The plate as flat rectangles in text pixels, y pointing down. It holds no client class, so a
 * headless game test can read the geometry. No two rectangles overlap, so the renderer needs no
 * depth ordering between them.
 */
public final class ReadoutPlateLayout {
	public static final int RING_RADIUS = 8;
	public static final int RING_THICKNESS = 2;
	public static final int RING_GAP = 4;
	public static final int NAME_HEIGHT = 9;
	public static final int BAR_GAP = 2;
	public static final int HP_BAR_WIDTH = 60;
	public static final int HP_BAR_HEIGHT = 4;
	public static final int ABSORPTION_BAR_WIDTH = 40;
	public static final int ABSORPTION_BAR_HEIGHT = 2;
	public static final int HP_FILL_COLOR = 0xFF3FD13F;
	public static final int ABSORPTION_FILL_COLOR = 0xFFFFD83A;
	public static final int BAR_BACK_COLOR = 0xFF202020;
	public static final int RING_INNER_COLOR = 0xC0101010;
	public static final int NAME_BACK_COLOR = 0x80000000;
	public static final int NAME_PADDING = 2;

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

	public record Plan(
		int width,
		int height,
		int numberCenterX,
		int numberCenterY,
		int nameX,
		int nameY,
		List<Rect> ring,
		Rect nameBack,
		Rect hpFill,
		Rect hpBack,
		Rect absorptionFill,
		Rect absorptionBack
	) {
		/** Every rectangle the plate draws, in draw order. */
		public List<Rect> all() {
			List<Rect> all = new ArrayList<>(this.ring);
			all.add(this.nameBack);
			all.add(this.hpFill);
			all.add(this.hpBack);
			if (this.absorptionBack != null) {
				all.add(this.absorptionFill);
				all.add(this.absorptionBack);
			}

			return all.stream().filter(rect -> rect != null && rect.width() > 0 && rect.height() > 0).toList();
		}
	}

	private ReadoutPlateLayout() {
	}

	/** The absorption bar exists only when `absorption` is above zero. */
	public static Plan plan(
		final int ringColor,
		final float health,
		final float maxHealth,
		final float absorption,
		final int nameWidth
	) {
		int diameter = RING_RADIUS * 2;
		int nameX = diameter + RING_GAP;
		int barsWidth = Math.max(nameWidth, HP_BAR_WIDTH);
		int nextY = NAME_HEIGHT + BAR_GAP;
		Rect absorptionFill = null;
		Rect absorptionBack = null;

		if (absorption > 0.0F) {
			int fill = fillWidth(absorption, Math.max(absorption, maxHealth), ABSORPTION_BAR_WIDTH);
			absorptionFill = new Rect(nameX, nextY, nameX + fill, nextY + ABSORPTION_BAR_HEIGHT, ABSORPTION_FILL_COLOR);
			absorptionBack = new Rect(
				nameX + fill, nextY, nameX + ABSORPTION_BAR_WIDTH, nextY + ABSORPTION_BAR_HEIGHT, BAR_BACK_COLOR
			);
			nextY += ABSORPTION_BAR_HEIGHT + BAR_GAP;
		}

		int hpFillWidth = fillWidth(health, maxHealth, HP_BAR_WIDTH);
		Rect hpFill = new Rect(nameX, nextY, nameX + hpFillWidth, nextY + HP_BAR_HEIGHT, HP_FILL_COLOR);
		Rect hpBack = new Rect(nameX + hpFillWidth, nextY, nameX + HP_BAR_WIDTH, nextY + HP_BAR_HEIGHT, BAR_BACK_COLOR);
		int height = Math.max(diameter, nextY + HP_BAR_HEIGHT);
		return new Plan(
			nameX + barsWidth, height, RING_RADIUS, RING_RADIUS, nameX, 0, ring(ringColor),
			new Rect(nameX - NAME_PADDING, 0, nameX + nameWidth + NAME_PADDING, NAME_HEIGHT, NAME_BACK_COLOR),
			hpFill, hpBack, absorptionFill, absorptionBack
		);
	}

	/** A live bar keeps one pixel, so a nearly dead mob does not read as an empty bar. */
	static int fillWidth(final float value, final float max, final int width) {
		if (max <= 0.0F || value <= 0.0F) {
			return 0;
		}

		int fill = Math.round(width * Math.min(1.0F, value / max));
		return Math.max(1, Math.min(width, fill));
	}

	/** Rows of a disc: a band-colored rim, a dark middle, and a band-colored rim again. */
	private static List<Rect> ring(final int ringColor) {
		List<Rect> rows = new ArrayList<>();
		int diameter = RING_RADIUS * 2;

		for (int y = 0; y < diameter; y++) {
			double dy = y + 0.5 - RING_RADIUS;
			int outer = halfWidth(RING_RADIUS, dy);
			int inner = halfWidth(RING_RADIUS - RING_THICKNESS, dy);

			if (inner <= 0) {
				rows.add(new Rect(RING_RADIUS - outer, y, RING_RADIUS + outer, y + 1, ringColor));
				continue;
			}

			rows.add(new Rect(RING_RADIUS - outer, y, RING_RADIUS - inner, y + 1, ringColor));
			rows.add(new Rect(RING_RADIUS - inner, y, RING_RADIUS + inner, y + 1, RING_INNER_COLOR));
			rows.add(new Rect(RING_RADIUS + inner, y, RING_RADIUS + outer, y + 1, ringColor));
		}

		return rows;
	}

	private static int halfWidth(final int radius, final double dy) {
		double square = radius * radius - dy * dy;
		return square <= 0.0 ? 0 : (int) Math.floor(Math.sqrt(square));
	}
}
