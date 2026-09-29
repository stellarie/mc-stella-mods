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
			.append(" charger=").append(LearnLog.num(Movement.chargePressure(player)))
			.append(" kiter=").append(LearnLog.num(Movement.kitePressure(player)))
			.append(" strafer=").append(LearnLog.num(Movement.strafePressure(player)))
			.append(" strafe_bias=").append(LearnLog.num(Movement.strafeBias(player)))
			.append(" crit_jumper=").append(LearnLog.num(Movement.critPressure(player)))
			.append(" policy_entries=").append(Policies.get(player).entries().size())
			.append(" policy_tries=").append(Policies.get(player).entries().values().stream().mapToInt(Policy.Entry::tries).sum())
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
		lines.add(entry(player, Habit.CHARGE, Movement.chargePressure(player), "charger", config.moveSaturation(), "flank slots tighten by up to " + LearnLog.num(config.chargeTighten() * 100.0) + " percent"));
		lines.add(entry(player, Habit.KITE, Movement.kitePressure(player), "kiter", config.moveSaturation(), "melee mobs aim " + config.interceptLeadTicks() + " ticks ahead of you"));
		lines.add(entry(player, Habit.STRAFE_LEFT, Movement.strafePressure(player), "strafer", config.moveSaturation(), "left of " + LearnLog.num(Profiles.value(player, Habit.STRAFE_LEFT) + Profiles.value(player, Habit.STRAFE_RIGHT))));
		lines.add(entry(player, Habit.STRAFE_RIGHT, Movement.strafePressure(player), "strafer", config.moveSaturation(), "bias " + LearnLog.num(Movement.strafeBias(player)) + ", the first flanker takes that side"));
		lines.add(entry(player, Habit.CRIT, Movement.critPressure(player), "crit_jumper", config.critSaturation(), "mobs step back when you jump"));
		lines.addAll(policy(player));
		return lines;
	}

	/** What the mobs have learned to do in each situation: every tactic tried, its average outcome, and the best marked. */
	static List<String> policy(final ServerPlayer player) {
		Policy policy = Policies.get(player);
		List<String> lines = new ArrayList<>();
		java.util.Set<String> groups = new java.util.TreeSet<>();
		policy.entries().keySet().forEach(key -> groups.add(key.substring(0, key.lastIndexOf('|'))));
		if (groups.isEmpty()) {
			return lines;
		}

		lines.add("What mobs have learned to do (average hit points won per try, best first):");
		for (String group : groups) {
			List<java.util.Map.Entry<String, Policy.Entry>> tried = policy.entries().entrySet().stream()
				.filter(entry -> entry.getKey().startsWith(group + "|")).sorted((a, b) -> Double.compare(b.getValue().value(), a.getValue().value())).toList();
			String tactics = tried.stream().map(entry -> entry.getKey().substring(group.length() + 1) + " " + LearnLog.num(entry.getValue().value()) + " (" + entry.getValue().tries() + ")")
				.collect(java.util.stream.Collectors.joining(", "));
			lines.add(group.replace('|', ' ') + ": " + tactics);
		}

		return lines;
	}

	private static String entry(final ServerPlayer player, final Habit habit, final double pressure, final String name, final double saturation, final String effect) {
		return habit.name() + " " + LearnLog.num(Profiles.value(player, habit)) + " of " + LearnLog.num(saturation) + " -> " + name + " " + LearnLog.num(pressure) + " (" + effect + ")";
	}
}
