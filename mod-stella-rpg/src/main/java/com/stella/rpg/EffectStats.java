package com.stella.rpg;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Stats from active effects. The config key {@code effect_stats} maps an effect name to a stat map.
 * Only vanilla effects and those of {@code mod-stella-food} count. An effect that is absent grants nothing.
 * A level is the amplifier plus one. A flat effect ignores the level.
 */
public final class EffectStats implements StatBonuses.Provider {
	public static final String FOOD_NAMESPACE = "mod-stella-food";

	public static void register() {
		StatBonuses.register(new EffectStats());
	}

	@Override
	public int bonus(final LivingEntity entity, final Stat stat) {
		if (entity.getActiveEffects().isEmpty()) {
			return 0;
		}
		Map<String, StatTables.EffectStat> table = RpgConfigStore.current().tables().effectStats();
		long sum = 0;
		for (MobEffectInstance effect : entity.getActiveEffects()) {
			Identifier id = effect.getEffect().unwrapKey().map(key -> key.identifier()).orElse(null);
			if (id != null) {
				sum += amount(id, effect.getAmplifier() + 1, stat, table);
			}
		}
		return (int) Math.max(-StatSheet.CEILING, Math.min(StatSheet.CEILING, sum));
	}

	/** What an effect of the id and level grants to a stat. */
	public static int amount(final Identifier id, final int level, final Stat stat, final Map<String, StatTables.EffectStat> table) {
		if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) && !id.getNamespace().equals(FOOD_NAMESPACE)) {
			return 0;
		}
		StatTables.EffectStat effect = table.get(id.getPath());
		if (effect == null) {
			return 0;
		}
		return effect.stats().getOrDefault(stat, 0) * (effect.perLevel() ? level : 1);
	}
}
