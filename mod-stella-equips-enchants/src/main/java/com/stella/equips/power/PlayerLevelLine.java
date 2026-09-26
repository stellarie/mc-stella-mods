package com.stella.equips.power;

import net.minecraft.network.chat.Component;

public final class PlayerLevelLine {
	private PlayerLevelLine() {
	}

	public static Component of(final int level) {
		return Component.literal("Lv " + level);
	}
}
