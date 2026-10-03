package com.stella.mobs.scaling;

import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;

/** The share of armor and Protection a mob's hit ignores: 25% at least, 1.5% per level, at most half. */
public final class MobPiercing {
	/** Every attacker ignores this much armor. */
	public static final double BASE_SHARE = 0.25;

	private MobPiercing() {
	}

	public static float share(final DamageSource source) {
		if (!(source.getEntity() instanceof Mob mob) || !SelfScaled.levelHits(mob)) {
			return 0.0F;
		}
		return share(ThreatState.level(mob), ScalingConfigStore.get());
	}

	public static float share(final int threatLevel, final ScalingConfig config) {
		double share = Math.max(0, threatLevel) * config.piercePerLevel();
		return (float) Math.min(config.pierceCap(), Math.max(BASE_SHARE, share));
	}
}
