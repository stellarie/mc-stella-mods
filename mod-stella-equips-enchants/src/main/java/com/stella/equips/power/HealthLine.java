package com.stella.equips.power;

import net.minecraft.network.chat.Component;

/** The owner's own line: level and health as numbers, on the channel the power tick uses. */
public final class HealthLine {
	private HealthLine() {
	}

	public static Component of(final int level, final float health, final float maxHealth) {
		return Component.literal("Lv " + level + "  " + shown(health) + "/" + shown(maxHealth) + " HP");
	}

	/** A live player never reads as zero, and a whole number never grows a decimal tail. */
	private static int shown(final float health) {
		return (int) Math.ceil(health);
	}
}
