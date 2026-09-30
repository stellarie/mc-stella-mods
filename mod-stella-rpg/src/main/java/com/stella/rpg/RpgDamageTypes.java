package com.stella.rpg;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** The poison tick's own damage type, plus the damage-over-time and environmental type tags. */
public final class RpgDamageTypes {
	public static final ResourceKey<DamageType> POISON = ResourceKey.create(Registries.DAMAGE_TYPE, StellaRpg.id("poison"));
	public static final TagKey<DamageType> NO_HURT_SHAKE = TagKey.create(Registries.DAMAGE_TYPE, StellaRpg.id("no_hurt_shake"));

	/** Damage types that INT defends against, besides magic and indirect magic. Empty by default. */
	public static final TagKey<DamageType> MAGIC = TagKey.create(Registries.DAMAGE_TYPE, StellaRpg.id("magic"));
	public static final TagKey<DamageType> ENVIRONMENTAL = TagKey.create(Registries.DAMAGE_TYPE, StellaRpg.id("environmental"));

	private RpgDamageTypes() {
	}

	public static DamageSource poison(final LivingEntity entity) {
		return new DamageSource(entity.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(POISON));
	}
}
