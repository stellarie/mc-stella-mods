package com.stella.rpg;

import java.util.Map;
import net.minecraft.world.entity.Mob;

public final class MobStats {
	public static final String MELEE = "melee";
	public static final int MINIMUM = 5;

	private MobStats() {
	}

	public static int base(final Mob mob, final Stat stat) {
		int threat = Math.max(0, MobsBridge.threat(mob));
		Map<String, Map<Stat, Double>> weights = RpgConfigStore.current().mobWeights();
		Map<Stat, Double> row = weights.get(MobsBridge.role(mob));
		if (row == null) {
			row = weights.get(MELEE);
		}
		long scaled = Math.round(threat * row.get(stat));
		return (int) Math.max(MINIMUM, Math.min(StatSheet.CEILING, scaled));
	}
}
