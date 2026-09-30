package com.stella.rpg;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.LivingEntity;

public final class DefenseBonuses {
	public static final int CEILING = 1024;

	@FunctionalInterface
	public interface Provider {
		float flat(LivingEntity entity);
	}

	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

	private DefenseBonuses() {
	}

	public static void register(final Provider provider) {
		PROVIDERS.add(provider);
	}

	public static void unregister(final Provider provider) {
		PROVIDERS.remove(provider);
	}

	public static float flat(final LivingEntity entity) {
		double sum = 0.0;
		for (Provider provider : PROVIDERS) {
			sum += provider.flat(entity);
		}
		return (float) Math.max(0.0, Math.min(CEILING, sum));
	}
}
