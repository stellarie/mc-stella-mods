package com.stella.rpg;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class StatBonuses {
	public static final int EFFECTIVE_CEILING = 2 * StatSheet.CEILING;

	@FunctionalInterface
	public interface Provider {
		int bonus(LivingEntity entity, Stat stat);
	}

	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

	private StatBonuses() {
	}

	public static void register(final Provider provider) {
		PROVIDERS.add(provider);
	}

	public static void unregister(final Provider provider) {
		PROVIDERS.remove(provider);
	}

	public static int bonus(final LivingEntity entity, final Stat stat) {
		long sum = 0;
		for (Provider provider : PROVIDERS) {
			sum += provider.bonus(entity, stat);
		}
		return (int) Math.max(0, Math.min(StatSheet.CEILING, sum));
	}

	public static int base(final LivingEntity entity, final Stat stat) {
		if (!(entity instanceof Player player)) {
			return 0;
		}
		return player.getAttachedOrElse(StatSheet.ATTACHMENT, StatSheet.EMPTY).get(stat);
	}

	public static int effective(final LivingEntity entity, final Stat stat) {
		int base;
		if (entity instanceof ServerPlayer) {
			base = base(entity, stat);
		} else if (entity instanceof Mob mob) {
			base = MobStats.base(mob, stat);
		} else {
			return 0;
		}
		return Math.max(0, Math.min(EFFECTIVE_CEILING, base + bonus(entity, stat)));
	}
}
