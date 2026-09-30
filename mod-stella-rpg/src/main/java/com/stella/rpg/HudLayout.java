package com.stella.rpg;

/** Pure layout math for the health bar HUD. It has no client references so game tests can call it. */
public final class HudLayout {
	public static final int BAR_WIDTH = 81;
	public static final int BAR_HEIGHT = 9;
	public static final int ICON_SIZE = 9;
	public static final int ICONS_PER_ROW = 9;
	public static final int ABSORPTION_HEIGHT = 2;

	public static final int FILL_HEALTH = 0xFFC8262B;
	public static final int FILL_POISON = 0xFF5E9A2E;
	public static final int FILL_WITHER = 0xFF555555;
	public static final int FILL_FROZEN = 0xFF9BD6F2;

	private HudLayout() {
	}

	public static int fillWidth(final float health, final float max, final int width) {
		return scaled(health, max, width);
	}

	public static int absorptionWidth(final float absorption, final float max, final int width) {
		return scaled(absorption, max, width);
	}

	public static String healthText(final float health, final float max) {
		return (int) Math.ceil(health) + " / " + (int) Math.ceil(max);
	}

	public static int iconRows(final int effects, final int perRow) {
		if (effects <= 0 || perRow <= 0) {
			return 0;
		}
		return (effects + perRow - 1) / perRow;
	}

	public static int iconsInRow(final int effects, final int perRow, final int row) {
		if (row < 0 || row >= iconRows(effects, perRow)) {
			return 0;
		}
		return Math.min(perRow, effects - row * perRow);
	}

	public static float blinkAlpha(final int durationTicks) {
		int fade = 10 - durationTicks / 20;
		float base = clamp(durationTicks / 10.0f / 5.0f * 0.5f, 0.5f);
		float wave = (float) Math.cos(durationTicks * Math.PI / 5.0) * clamp(fade / 10.0f * 0.25f, 0.25f);
		return clamp(base + wave, 1.0f);
	}

	// Wither and Poison win over frozen, as they do damage.
	public static int fillColor(final boolean wither, final boolean poison, final boolean frozen) {
		if (wither) {
			return FILL_WITHER;
		}
		if (poison) {
			return FILL_POISON;
		}
		return frozen ? FILL_FROZEN : FILL_HEALTH;
	}

	private static float clamp(final float value, final float max) {
		return Math.max(0.0f, Math.min(max, value));
	}

	private static int scaled(final float value, final float max, final int width) {
		if (max <= 0.0f || value <= 0.0f) {
			return 0;
		}
		if (value >= max) {
			return width;
		}
		return (int) (value / max * width);
	}
}
