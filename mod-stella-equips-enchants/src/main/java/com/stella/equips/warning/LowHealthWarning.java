package com.stella.equips.warning;

/** When the low-health warning shows. The client draws it; this class holds every rule. */
public final class LowHealthWarning {
	/** The warning shows below this fraction of max health. */
	public static final float THRESHOLD = 0.30F;
	public static final int HEARTBEAT_TICKS = 20;
	private static final float VIGNETTE_FLOOR = 0.35F;
	private static final float PULSE_FLOOR = 0.6F;

	private LowHealthWarning() {
	}

	/** False for NaN, so a broken value never warns. */
	public static boolean active(final float health, final float maxHealth) {
		return health > 0.0F && maxHealth > 0.0F && health < maxHealth * THRESHOLD;
	}

	/** The tick counts from the moment the warning came on. */
	public static boolean beatDue(final int ticksActive) {
		return ticksActive % HEARTBEAT_TICKS == 0;
	}

	/** Zero when the warning is off. Otherwise 0 to 1, deeper at lower health, and peaking on the beat. */
	public static float vignette(final float health, final float maxHealth, final int ticksActive) {
		if (!active(health, maxHealth)) {
			return 0.0F;
		}

		float depth = 1.0F - health / (maxHealth * THRESHOLD);
		float phase = (float) (ticksActive % HEARTBEAT_TICKS) / HEARTBEAT_TICKS;
		float pulse = PULSE_FLOOR + (1.0F - PULSE_FLOOR) * (0.5F + 0.5F * (float) Math.cos(2.0 * Math.PI * phase));
		return Math.min(1.0F, (VIGNETTE_FLOOR + (1.0F - VIGNETTE_FLOOR) * depth) * pulse);
	}
}
