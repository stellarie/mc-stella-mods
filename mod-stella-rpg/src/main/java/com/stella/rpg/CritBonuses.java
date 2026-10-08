package com.stella.rpg;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.LivingEntity;

/**
 * Critical chance and critical damage bonuses that other mods supply per caster. {@code mod-stella-spells} finds this
 * class by name, so the registration shape and the method names are a contract: it registers once at init and
 * receives a caster on every critical roll. With no provider registered every method returns zero, so a mod that is
 * absent grants nothing. A provider that throws is logged and skipped for that roll.
 */
public final class CritBonuses {
	/** One provider's answer for one caster. The chance is a fraction, the damage a multiple of the hit. */
	public record Bonus(double chance, double damage) {
	}

	@FunctionalInterface
	public interface Provider {
		Bonus bonus(LivingEntity caster);
	}

	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

	private CritBonuses() {
	}

	public static void register(final Provider provider) {
		PROVIDERS.add(provider);
	}

	public static void unregister(final Provider provider) {
		PROVIDERS.remove(provider);
	}

	/** The chance a caster's critical roll gains. A provider that throws contributes nothing. */
	public static double chanceBonus(final LivingEntity caster) {
		double sum = 0.0;
		for (Provider provider : PROVIDERS) {
			Bonus bonus = of(provider, caster);
			if (bonus != null) {
				sum += bonus.chance();
			}
		}
		return sum;
	}

	/** The multiplier a caster's critical hit gains. A provider that throws contributes nothing. */
	public static double damageBonus(final LivingEntity caster) {
		double sum = 0.0;
		for (Provider provider : PROVIDERS) {
			Bonus bonus = of(provider, caster);
			if (bonus != null) {
				sum += bonus.damage();
			}
		}
		return sum;
	}

	/** The critical multiplier of one caster: the config value plus every registered damage bonus. */
	public static double critMultiplier(final LivingEntity caster) {
		return RpgCurve.critMultiplier() + damageBonus(caster);
	}

	private static Bonus of(final Provider provider, final LivingEntity caster) {
		try {
			return provider.bonus(caster);
		} catch (RuntimeException exception) {
			StellaRpg.LOGGER.warn("a critical bonus provider threw for {}; skipping it", caster.getName().getString(), exception);
			return null;
		}
	}
}
