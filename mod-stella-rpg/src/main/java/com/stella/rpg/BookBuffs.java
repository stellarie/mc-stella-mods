package com.stella.rpg;

import java.util.function.ToDoubleFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.LivingEntity;

/**
 * The MATK a refined spell book leaves on its caster, read from {@code mod-stella-spells} through the
 * ObjectShare key it publishes. The value is a {@code ToDoubleFunction<Object>} of the entity that returns a
 * fraction, so this mod needs no class of that one. Without the key, or on any failure, the buff is 0.
 */
public final class BookBuffs {
	/** The key the spells mod publishes. */
	public static final String MATK_SHARE = "mod-stella-spells:matk-bonus";
	/** No buff counts for more than this fraction, whatever the sibling answers. */
	private static final double CAP = 1.0;

	private BookBuffs() {
	}

	/** The buff as a fraction of MATK; 0 when the spells mod is absent. Read on each call. */
	@SuppressWarnings("unchecked")
	public static double matkBonus(final LivingEntity entity) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(MATK_SHARE);
		if (!(shared instanceof ToDoubleFunction<?> function)) {
			return 0.0;
		}
		try {
			double bonus = ((ToDoubleFunction<Object>) function).applyAsDouble(entity);
			return Double.isFinite(bonus) && bonus > 0.0 ? Math.min(bonus, CAP) : 0.0;
		} catch (RuntimeException failure) {
			return 0.0;
		}
	}
}
