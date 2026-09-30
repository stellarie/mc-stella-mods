package com.stella.mobs.scaling;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;

/** The share of armor and Protection a mob's hit ignores: 1.5% per level, at most half. */
public final class MobPiercing {
	private MobPiercing() {
	}

	public static float share(final DamageSource source) {
		if (!(source.getEntity() instanceof Mob mob) || mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG)) {
			return 0.0F;
		}
		return share(ThreatState.level(mob), ScalingConfigStore.get());
	}

	public static float share(final int threatLevel, final ScalingConfig config) {
		double share = Math.max(0, threatLevel) * config.piercePerLevel();
		return (float) Math.max(0.0, Math.min(config.pierceCap(), share));
	}
}
