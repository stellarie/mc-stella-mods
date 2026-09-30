package com.stella.rpg;

import java.util.Map;
import net.minecraft.world.entity.Mob;

/** A mob's stat: its role's base, amplified by its level. */
public final class MobStats {
	public static final String MELEE = "melee";
	public static final int MINIMUM = 1;

	private MobStats() {
	}

	public static int base(final Mob mob, final Stat stat) {
		RpgConfig config = RpgConfigStore.current();
		return base(config, MobsBridge.role(mob), stat, MobsBridge.threat(mob));
	}

	public static int base(final RpgConfig config, final String role, final Stat stat, final int threat) {
		Map<String, Map<Stat, Double>> table = config.mobStats();
		Map<Stat, Double> row = table.get(role);
		if (row == null) {
			row = table.get(MELEE);
		}
		long scaled = Math.round(row.get(stat) * (1.0 + config.mobAmplifier() * Math.max(0, threat)));
		return (int) Math.max(MINIMUM, Math.min(StatSheet.CEILING, scaled));
	}
}
