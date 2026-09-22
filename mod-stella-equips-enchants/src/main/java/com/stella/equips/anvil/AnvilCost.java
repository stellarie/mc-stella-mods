package com.stella.equips.anvil;

public final class AnvilCost {
	public static final int CLAMPED = 1;

	private AnvilCost() {
	}

	public static int clamp(final int vanillaCost) {
		return Math.min(vanillaCost, CLAMPED);
	}
}
