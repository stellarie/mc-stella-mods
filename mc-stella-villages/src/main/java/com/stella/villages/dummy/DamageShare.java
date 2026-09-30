package com.stella.villages.dummy;

import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Reads the RPG outgoing damage scaling from the object share {@code mod-stella-rpg:outgoing-damage}. The value is a
 * {@code Function<Object[], Float>} taking {@code {victim, source, amount}}. Without the RPG mod, the amount is raw.
 */
public final class DamageShare {
	public static final String OBJECT_SHARE_KEY = "mod-stella-rpg:outgoing-damage";

	private DamageShare() {
	}

	@SuppressWarnings("unchecked")
	public static float scale(final LivingEntity victim, final DamageSource source, final float amount) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(OBJECT_SHARE_KEY);
		if (!(shared instanceof Function<?, ?> share)) {
			return amount;
		}
		try {
			Object scaled = ((Function<Object[], ?>) share).apply(new Object[] {victim, source, amount});
			return scaled instanceof Float value ? value : amount;
		} catch (RuntimeException e) {
			return amount;
		}
	}
}
