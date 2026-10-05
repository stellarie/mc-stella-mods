package com.stella.equips.unique.mp;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** The random rolls of the mage and priest uniques. A test replaces the roller and decides by holder and unique id. */
public final class MpRolls {
	/** Decides a chance roll and picks a value in [0, 1). */
	public interface Roller {
		boolean succeeds(String unique, ServerPlayer holder, double chance);

		double unit(String unique, ServerPlayer holder);
	}

	private static final Roller FAIR = new Roller() {
		@Override
		public boolean succeeds(final String unique, final ServerPlayer holder, final double chance) {
			return holder.getRandom().nextDouble() < chance;
		}

		@Override
		public double unit(final String unique, final ServerPlayer holder) {
			return holder.getRandom().nextDouble();
		}
	};

	private static volatile Roller roller = FAIR;

	private MpRolls() {
	}

	public static void setRoller(final @Nullable Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static boolean chance(final String unique, final ServerPlayer holder, final double chance) {
		return roller.succeeds(unique, holder, chance);
	}

	public static double unit(final String unique, final ServerPlayer holder) {
		return roller.unit(unique, holder);
	}

	public static int pick(final String unique, final ServerPlayer holder, final int bound) {
		return Math.min(bound - 1, (int) (roller.unit(unique, holder) * bound));
	}
}
