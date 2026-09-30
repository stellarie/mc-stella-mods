package com.stella.learn.gym;

import net.minecraft.util.RandomSource;

/**
 * The skill values a brain draws at episode start: the most it misses its aim by, how many ticks it lags behind what the
 * mob does, and its gear. The draw depends on the stage.
 */
public record Skill(float aimErrorDegrees, int reactionTicks, Gear gear) {
	public static final float MAX_AIM_ERROR = 6.0F;
	public static final int MIN_REACTION = 2;
	public static final int MAX_REACTION = 8;

	public static Skill draw(final RandomSource random, final int stage) {
		if (stage == Terrain.A) {
			return new Skill(MAX_AIM_ERROR, MAX_REACTION, Gear.NONE);
		}

		float aim = random.nextFloat() * MAX_AIM_ERROR;
		int reaction = MIN_REACTION + random.nextInt(MAX_REACTION - MIN_REACTION + 1);
		Gear gear = stage == Terrain.B ? (random.nextBoolean() ? Gear.NONE : Gear.IRON) : Gear.values()[random.nextInt(Gear.values().length)];
		return new Skill(aim, reaction, gear);
	}

	public String detail() {
		return "aimError=" + com.stella.learn.LearnLog.num(aimErrorDegrees) + " reaction=" + reactionTicks + " gear=" + gear;
	}
}
