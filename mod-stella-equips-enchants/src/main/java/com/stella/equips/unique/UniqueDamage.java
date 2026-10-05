package com.stella.equips.unique;

import com.stella.equips.StellaEquips;
import com.stella.equips.mixin.LivingEntityHurtAccessor;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Extra damage of a unique, dealt as the holder. The damage types are {@code unique}, which armor reduces, and
 * {@code unique_pierce}, which ignores armor. Both skip the hurt cooldown and keep it as it was, like
 * {@code SlimeHeart.land}, so an extra hit never costs the holder a real hit. They run no hit hooks.
 */
public final class UniqueDamage {
	public static final ResourceKey<DamageType> UNIQUE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "unique"));
	public static final ResourceKey<DamageType> UNIQUE_PIERCE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "unique_pierce"));

	private UniqueDamage() {
	}

	/** Hurts a target as the holder. Returns whether the target took damage. */
	public static boolean deal(final ServerLevel level, final LivingEntity target, final ServerPlayer from, final float amount, final boolean pierce) {
		if (amount <= 0.0F || target == from || !target.isAlive()) {
			return false;
		}
		Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(pierce ? UNIQUE_PIERCE : UNIQUE);
		LivingEntityHurtAccessor hurt = (LivingEntityHurtAccessor) target;
		int cooldown = target.damageCooldownTime;
		float lastHurt = hurt.stellaEquips$getLastHurt();
		try {
			return target.hurtServer(level, new DamageSource(type, from), amount);
		} finally {
			target.damageCooldownTime = cooldown;
			hurt.stellaEquips$setLastHurt(lastHurt);
		}
	}

	/** Hurts every foe whose body is within the radius of a point. The skipped entity is left out. Returns the count hurt. */
	public static int area(final ServerLevel level, final Vec3 center, final double radius, final ServerPlayer from, final float amount, final boolean pierce,
		final @Nullable LivingEntity skip) {
		AABB box = new AABB(center, center).inflate(radius);
		double reach = radius * radius;
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, box,
			entity -> entity != skip && UniqueTargets.isFoe(from, entity) && entity.getBoundingBox().distanceToSqr(center) <= reach);
		int hurt = 0;
		for (LivingEntity foe : foes) {
			if (deal(level, foe, from, amount, pierce)) {
				hurt++;
			}
		}
		return hurt;
	}
}
