package com.stella.mobs.scaling;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/** Kill experience: a base per mob type plus a part from the mob level and its gap to the killer's level. */
public final class ThreatExperience {
	/** Set by the villages mod on a Nemesis. Read by name, so this mod needs no sibling. */
	public static final String NEMESIS_TAG = "stella_nemesis";

	private ThreatExperience() {
	}

	public static int reward(final int base, final int threatLevel, final int playerLevel) {
		ScalingConfig config = ScalingConfigStore.get();
		long level = Math.max(0, threatLevel);
		double gap = Mth.clamp(1.0 + config.xpGapStep() * (level - playerLevel), config.xpGapMin(), config.xpGapMax());
		return clampToInt(Math.round(base + config.xpLevelFactor() * level * gap), 1L);
	}

	public static int base(final Mob mob) {
		ScalingConfig config = ScalingConfigStore.get();
		Integer listed = config.xpBase().get(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
		if (listed != null) {
			return listed;
		}
		return mob instanceof Enemy ? config.xpBaseHostile() : config.xpBasePassive();
	}

	/** An unrolled mob has no level, so it counts as on level with its killer. */
	public static int rewardFor(final Mob mob, final int killerLevel) {
		int level = mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG) ? killerLevel : ThreatState.level(mob);
		int formula = reward(base(mob), level, killerLevel);
		return clampToInt(Math.round(formula * multiplier(mob)), 1L);
	}

	/** The larger of the Nemesis and elite multipliers applies. They do not stack. */
	public static double multiplier(final Mob mob) {
		ScalingConfig config = ScalingConfigStore.get();
		double multiplier = 1.0;
		if (mob.entityTags().contains(NEMESIS_TAG)) {
			multiplier = Math.max(multiplier, config.xpNemesisMultiplier());
		}
		if (Elite.isElite(mob)) {
			multiplier = Math.max(multiplier, config.xpEliteMultiplier());
		}
		return multiplier;
	}

	private static int clampToInt(final long value, final long minimum) {
		return (int) Math.min(Integer.MAX_VALUE, Math.max(minimum, value));
	}
}
