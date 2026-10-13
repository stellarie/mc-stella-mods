package com.stella.rpg.api;

import com.stella.rpg.RpgCombat;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Publishes the RPG critical hit verdict under {@code mod-stella-rpg:is-crit}.
 * The value is a {@code Function<Object[], Boolean>} taking {@code {LivingEntity victim, DamageSource source}}.
 * It answers true while a weapon critical hit on the victim is in progress, or for a spell critical hit of the attacker in the same tick.
 * It uses JDK types only, so a reader needs no class from this mod.
 */
public final class CritShare {
	public static final String OBJECT_SHARE_KEY = "mod-stella-rpg:is-crit";

	private CritShare() {
	}

	public static void register() {
		Function<Object[], Boolean> share = CritShare::isCrit;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, share);
	}

	private static Boolean isCrit(final Object[] args) {
		if (args.length != 2 || !(args[0] instanceof LivingEntity victim) || !(args[1] instanceof DamageSource source)) {
			throw new IllegalArgumentException("expected {LivingEntity, DamageSource}");
		}
		return RpgCombat.isCrit(victim, source);
	}
}
