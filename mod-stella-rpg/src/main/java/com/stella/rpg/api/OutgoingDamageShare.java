package com.stella.rpg.api;

import com.stella.rpg.RpgCombat;
import com.stella.rpg.RpgScale;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Publishes the RPG outgoing damage scaling under {@code mod-stella-rpg:outgoing-damage}.
 * The value is a {@code Function<Object[], Float>} taking {@code {LivingEntity victim, DamageSource source, Float amount}}
 * and returning {@code RpgCombat.scaleOutgoing} times the health scale K, so the value is in health units. It uses JDK types only, so a reader needs no class from this mod.
 */
public final class OutgoingDamageShare {
	public static final String OBJECT_SHARE_KEY = "mod-stella-rpg:outgoing-damage";

	private OutgoingDamageShare() {
	}

	public static void register() {
		Function<Object[], Float> share = OutgoingDamageShare::scale;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, share);
	}

	private static Float scale(final Object[] args) {
		if (args.length != 3 || !(args[0] instanceof LivingEntity victim) || !(args[1] instanceof DamageSource source)
			|| !(args[2] instanceof Float amount)) {
			throw new IllegalArgumentException("expected {LivingEntity, DamageSource, Float}");
		}
		return RpgCombat.scaleOutgoing(victim, source, amount) * RpgScale.kf();
	}
}
