package com.stella.spells;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;

/** Loads the mobs mod's threat API only when that mod is installed. */
final class SummonThreatBridge {
	private static final Method HIGHEST = highestMethod();
	private static final double HEALTH_BONUS = healthBonusRate();

	private SummonThreatBridge() {
	}

	static int peak(final MinecraftServer server) {
		if (HIGHEST == null) {
			return 0;
		}
		try {
			return ((Number) HIGHEST.invoke(null, server)).intValue();
		} catch (ReflectiveOperationException failure) {
			Spellbooks.LOGGER.error("Could not read the mobs mod's world threat peak", failure);
			return 0;
		}
	}

	static double healthBonus(final int peak) {
		return peak * HEALTH_BONUS;
	}

	private static Method highestMethod() {
		try {
			return Class.forName("com.stella.mobs.scaling.WorldThreatPeak").getMethod("highest", MinecraftServer.class);
		} catch (ReflectiveOperationException failure) {
			Spellbooks.LOGGER.error("The loaded mobs mod lacks its world threat peak API", failure);
			return null;
		}
	}

	private static double healthBonusRate() {
		try {
			Field rate = Class.forName("com.stella.mobs.scaling.SpawnScaling").getField("LEVEL_HEALTH_BONUS");
			return rate.getDouble(null);
		} catch (ReflectiveOperationException failure) {
			Spellbooks.LOGGER.error("The loaded mobs mod lacks its health scaling rate", failure);
			return 0.0;
		}
	}
}
