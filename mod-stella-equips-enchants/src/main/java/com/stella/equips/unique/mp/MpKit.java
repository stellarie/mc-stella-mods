package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueTargets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small helpers that the mage and priest uniques share. */
public final class MpKit {
	public static final int SECOND = 20;

	private MpKit() {
	}

	public static String percent(final double share) {
		return Math.round(share * 100.0) + "%";
	}

	public static String seconds(final int ticks) {
		return (ticks % SECOND == 0 ? String.valueOf(ticks / SECOND) : String.valueOf(ticks / (double) SECOND)) + " s";
	}

	public static int ticks(final double seconds) {
		return (int) Math.round(seconds * SECOND);
	}

	/** The foes whose body is within the radius of a point, the skipped entity left out. */
	public static List<LivingEntity> foesAround(final ServerLevel level, final ServerPlayer holder, final Vec3 center, final double radius, final LivingEntity skip) {
		double reach = radius * radius;
		return new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
			entity -> entity != skip && UniqueTargets.isFoe(holder, entity) && entity.getBoundingBox().distanceToSqr(center) <= reach));
	}

	/** Harmful effects on an entity. */
	public static int harmfulCount(final LivingEntity entity) {
		int count = 0;
		for (MobEffectInstance effect : entity.getActiveEffects()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				count++;
			}
		}
		return count;
	}

	/** A buff that the holder keeps by refreshing it often: hidden, with no particles. */
	public static void buff(final LivingEntity target, final Holder<MobEffect> effect, final int ticks, final int amplifier) {
		MobEffectInstance current = target.getEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getAmplifier() == amplifier && current.getDuration() < ticks) {
			target.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
		}
	}

	/** Sets a target alight for at least the ticks. A shorter fire never replaces a longer one. */
	public static void ignite(final LivingEntity target, final int ticks) {
		if (!target.fireImmune() && target.getRemainingFireTicks() < ticks) {
			target.setRemainingFireTicks(ticks);
		}
	}

	/** Fire immunity of a worn unique: no flames, and a hidden fire resistance that covers lava. */
	public static void fireProof(final LivingEntity wearer) {
		buff(wearer, MobEffects.FIRE_RESISTANCE, 3 * SECOND, 0);
		wearer.clearFire();
	}

	public static void heal(final LivingEntity target, final double amount) {
		if (amount > 0.0 && target.isAlive()) {
			target.heal((float) amount);
		}
	}
}
