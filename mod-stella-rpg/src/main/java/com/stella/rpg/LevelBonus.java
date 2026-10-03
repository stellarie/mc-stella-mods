package com.stella.rpg;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** A player gets bonus points in every stat for each full block of levels. */
public final class LevelBonus implements StatBonuses.Provider {
	public static void register() {
		StatBonuses.register(new LevelBonus());
	}

	@Override
	public int bonus(final LivingEntity entity, final Stat stat) {
		if (!(entity instanceof ServerPlayer player)) {
			return 0;
		}
		RpgConfig.ScalingRules rules = RpgConfigStore.current().scaling();
		return Math.max(0, player.experienceLevel) / rules.bonusLevelStep() * rules.bonusPoints();
	}
}
