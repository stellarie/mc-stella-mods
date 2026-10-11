package com.stella.mobs.flash;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The strength and colour of the two screen edge flashes. The client draws them. This class holds the arithmetic, so a
 * server side game test can check what the player would see.
 */
public final class StatusFlash {
	public static final int ORANGE = 0xFF7A1A;
	public static final int FROST = 0xCFE8FF;
	public static final float BURN_MIN_ALPHA = 0.25F;
	public static final float BURN_MAX_ALPHA = 0.55F;
	public static final float CHILL_MAX_ALPHA = 0.6F;
	public static final int PULSE_TICKS = 16;
	private static final int ALPHA_SHIFT = 24;
	private static final int ALPHA_MAX = 255;

	private StatusFlash() {
	}

	/** The peak alpha of the orange flash at a time in ticks, or 0 when the body is not burning. */
	public static float burnAlpha(final Entity body, final float time) {
		if (!body.isOnFire()) {
			return 0.0F;
		}

		float wave = 0.5F + 0.5F * Mth.sin(Mth.TWO_PI * time / PULSE_TICKS);
		return Mth.lerp(wave, BURN_MIN_ALPHA, BURN_MAX_ALPHA);
	}

	/** The peak alpha of the frost flash, scaled by how frozen the body is, or 0 when it is not frozen. */
	public static float chillAlpha(final Entity body) {
		if (body.getTicksFrozen() <= 0) {
			return 0.0F;
		}

		return Mth.clamp(body.getPercentFrozen(), 0.0F, 1.0F) * CHILL_MAX_ALPHA;
	}

	/** The alpha of one ring step: full at the screen edge, fading to nothing toward the centre. */
	public static float ringAlpha(final float peak, final int step, final int steps) {
		float fade = 1.0F - (float) step / steps;
		return peak * fade * fade;
	}

	/** An ARGB colour for one ring step. */
	public static int ringColor(final int rgb, final float peak, final int step, final int steps) {
		int alpha = Math.round(Mth.clamp(ringAlpha(peak, step, steps), 0.0F, 1.0F) * ALPHA_MAX);
		return (alpha << ALPHA_SHIFT) | rgb;
	}
}
