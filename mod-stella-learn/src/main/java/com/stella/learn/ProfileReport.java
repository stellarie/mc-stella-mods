package com.stella.learn;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a player's profile holds and what mobs make of it: each counter's decayed value, and the pressure it puts on
 * mobs, which is the number the tactics read. The same numbers feed the summary line in the log and the command.
 */
public final class ProfileReport {
	private ProfileReport() {
	}

	/** One line: every counter, and the pressure each one puts on mobs. */
	public static String line(final ServerPlayer player) {
		StringBuilder line = new StringBuilder(LearnLog.who(player));
		for (Habit habit : Habit.values()) {
			line.append(' ').append(habit.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(LearnLog.num(Profiles.value(player, habit)));
		}

		return line.append(" rush=").append(LearnLog.num(LearnedBand.rushPressure(player)))
			.append(" turtle=").append(LearnLog.num(WaitOutShieldGoal.shieldPressure(player)))
			.append(" shooter=").append(LearnLog.num(Cover.shooterPressure(player)))
			.append(" retreater=").append(LearnLog.num(Choke.retreatPressure(player)))
			.toString();
	}

	/** The same, one counter to a line, for a player who asks. */
	public static List<String> describe(final ServerPlayer player) {
		LearnConfig config = LearnConfigStore.get();
		List<String> lines = new ArrayList<>();
		lines.add("What mobs have learned about " + player.getName().getString() + " (counts halve every " + config.profileHalfLifeTicks() + " ticks):");
		lines.add(entry(player, Habit.MELEE, LearnedBand.rushPressure(player), "rush", config.rushSaturation(), "archers keep " + LearnLog.num(config.rushBandGain()) + " blocks more distance at full"));
		lines.add(entry(player, Habit.BLOCK, WaitOutShieldGoal.shieldPressure(player), "turtle", config.shieldSaturation(), "mobs wait out a raised shield from " + LearnLog.num(config.shieldThreshold())));
		lines.add(entry(player, Habit.RANGED, Cover.shooterPressure(player), "shooter", config.rangedSaturation(), "melee mobs advance through cover from " + LearnLog.num(config.coverThreshold())));
		lines.add(entry(player, Habit.RETREAT, Choke.retreatPressure(player), "retreater", config.retreatSaturation(), "mobs gather outside a gap from " + LearnLog.num(config.chokeThreshold())));
		return lines;
	}

	private static String entry(final ServerPlayer player, final Habit habit, final double pressure, final String name, final double saturation, final String effect) {
		return habit.name() + " " + LearnLog.num(Profiles.value(player, habit)) + " of " + LearnLog.num(saturation) + " -> " + name + " " + LearnLog.num(pressure) + " (" + effect + ")";
	}
}
