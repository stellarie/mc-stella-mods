package com.stella.learn;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/** The pressures the movement counters put on mobs, each from 0 to 1, in the way the other habits do. */
public final class Movement {
	private Movement() {
	}

	/** How much of a full charger this player is. */
	public static double chargePressure(final ServerPlayer player) {
		return clamp(Profiles.value(player, Habit.CHARGE) / LearnConfigStore.get().moveSaturation());
	}

	/** How much of a full kiter this player is: one who backs away while shooting. */
	public static double kitePressure(final ServerPlayer player) {
		return clamp(Profiles.value(player, Habit.KITE) / LearnConfigStore.get().moveSaturation());
	}

	/** How much of a full strafer this player is, whichever way they go. */
	public static double strafePressure(final ServerPlayer player) {
		return clamp((Profiles.value(player, Habit.STRAFE_LEFT) + Profiles.value(player, Habit.STRAFE_RIGHT)) / LearnConfigStore.get().moveSaturation());
	}

	/** From -1 for a player who only strafes left to +1 for one who only strafes right; 0 with no strafing seen. */
	public static double strafeBias(final ServerPlayer player) {
		double left = Profiles.value(player, Habit.STRAFE_LEFT);
		double right = Profiles.value(player, Habit.STRAFE_RIGHT);
		return left + right < 1.0E-6 ? 0.0 : (right - left) / (left + right);
	}

	/** How much of a full crit-jumper this player is. */
	public static double critPressure(final ServerPlayer player) {
		return clamp(Profiles.value(player, Habit.CRIT) / LearnConfigStore.get().critSaturation());
	}

	private static double clamp(final double value) {
		return Mth.clamp(value, 0.0, 1.0);
	}
}
