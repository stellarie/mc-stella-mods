package com.stella.learn;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * The mod's behaviour log, in the format the other mods use: `[BEHAVIOR] test=<name> <VERB> key=value ...`, one line
 * per state change, through the mod logger at INFO. `behavior_log` in the config switches it off. A tap lets a test
 * read the lines a run wrote, so a test asserts on the same text a server log carries.
 */
public final class LearnLog {
	private static volatile Consumer<String> tap = line -> {
	};

	private LearnLog() {
	}

	/** Sends every line that is written to `listener` as well. Meant for tests. */
	public static void tap(final Consumer<String> listener) {
		tap = listener;
	}

	public static String line(final String test, final String verb, final String detail) {
		return "[BEHAVIOR] test=" + test + " " + verb + (detail.isEmpty() ? "" : " " + detail);
	}

	public static void info(final String test, final String verb, final String detail) {
		info(LearnConfigStore.get(), test, verb, detail);
	}

	public static void info(final LearnConfig config, final String test, final String verb, final String detail) {
		if (!config.behaviorLog()) {
			return;
		}

		String line = line(test, verb, detail);
		StellaLearn.LOGGER.info(line);
		tap.accept(line);
	}

	/** A line for the debug log only: something that happens too often to belong at INFO. */
	public static void debug(final String test, final String verb, final String detail) {
		StellaLearn.LOGGER.debug(line(test, verb, detail));
	}

	/**
	 * A per-decision line, sampled by its caller: debug level in the server log, so a busy server does not write it, and
	 * always to a test's tap. `behavior_log` still switches it off.
	 */
	public static void sampled(final String test, final String verb, final String detail) {
		if (!LearnConfigStore.get().behaviorLog()) {
			return;
		}

		String line = line(test, verb, detail);
		StellaLearn.LOGGER.debug(line);
		tap.accept(line);
	}

	public static String who(final Mob mob) {
		return "mob=" + BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()) + " id=" + mob.getUUID();
	}

	public static String who(final ServerPlayer player) {
		return "player=" + player.getName().getString() + " pid=" + player.getUUID();
	}

	public static String num(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
