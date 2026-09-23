package com.stella.mobs.behavior;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The elite witch: one flag in the attachment, 1.5 times health derived from the mob's own base,
 * and a 16 block blindness aura. Phase 7 owns the general elite framework.
 */
public final class WitchElite {
	public static final double HEALTH_MULTIPLIER = 1.5;
	public static final double DEFAULT_CHANCE = 0.10;
	public static final double AURA_RADIUS = 16.0;
	public static final int AURA_DURATION_TICKS = 100;
	public static final int AURA_INTERVAL_TICKS = 20;

	private WitchElite() {
	}

	public static boolean roll(final RandomSource random, final double chance) {
		return random.nextFloat() < (float) chance;
	}

	public static double eliteHealth(final double baseHealth) {
		return baseHealth * HEALTH_MULTIPLIER;
	}

	/** Marks the mob elite and raises its health once, from the base it already carries. */
	public static void mark(final Mob witch) {
		ReactiveState state = ReactiveState.get(witch);
		if (state.isElite()) {
			return;
		}

		state.setElite(true);
		double boosted = eliteHealth(witch.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
		witch.getAttribute(Attributes.MAX_HEALTH).setBaseValue(boosted);
		witch.setHealth((float) boosted);
	}

	public static boolean isElite(final Mob witch) {
		return ReactiveState.get(witch).isElite();
	}
}
