package com.stella.equips.unique.af;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** The chance rolls of the archer and fighter uniques. A test replaces the roller and decides by holder. */
public final class AfChance {
	/** Decides one roll. The unique id tells which effect rolls. */
	public interface Roller {
		boolean succeeds(ServerPlayer holder, String unique, double chance);
	}

	private static final Roller FAIR = (holder, unique, chance) -> holder.getRandom().nextDouble() < chance;
	private static volatile Roller roller = FAIR;

	private AfChance() {
	}

	public static boolean roll(final ServerPlayer holder, final String unique, final double chance) {
		return roller.succeeds(holder, unique, chance);
	}

	public static void setRoller(final @Nullable Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}
}
