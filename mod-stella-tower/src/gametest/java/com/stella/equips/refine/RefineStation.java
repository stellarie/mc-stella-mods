package com.stella.equips.refine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * A stand-in for the equips mod's refine station, which this harness does not load. It has the three members the tower's refine
 * ward reaches by name, with the signatures of the real ones, and a refine of one roll. The real station is checked by hand.
 */
public final class RefineStation {
	/** Whether one player's step succeeds, given its chance in percent. */
	public interface Roller {
		boolean succeeds(ServerPlayer player, RandomSource random, int chancePercent);
	}

	private static final Roller FAIR = (player, random, chance) -> fairRoll(random, chance);
	private static volatile Roller roller = FAIR;

	private RefineStation() {
	}

	public static void setRoller(final Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static boolean fairRoll(final RandomSource random, final int chancePercent) {
		return random.nextInt(100) < chancePercent;
	}

	/** One refine at a chance in percent: whether it succeeded. */
	public static boolean refine(final ServerPlayer player, final int chancePercent) {
		return roller.succeeds(player, player.getRandom(), chancePercent);
	}
}
