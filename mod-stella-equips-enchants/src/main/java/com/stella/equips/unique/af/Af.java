package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueEffects;
import com.stella.equips.unique.UniqueTargets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared helpers of the archer and fighter uniques: tier pick, text, foes around a point, effects and knockback. */
public final class Af {
	/** Marks an arrow that left the bow while the holder was off the ground. */
	public static final String AIRBORNE_TAG = "stella_unique_airborne_shot";
	/** Marks an arrow that left the crossbow while the holder was sprinting. */
	public static final String SPRINT_TAG = "stella_unique_sprint_shot";
	/** The arrow speed of a full draw bow shot. */
	public static final float FULL_DRAW = 3.0F;
	private static final float EPSILON = 1.0E-3F;

	private Af() {
	}

	public static int tier(final int refine, final int base, final int plus7, final int plus12) {
		return refine >= 12 ? plus12 : refine >= 7 ? plus7 : base;
	}

	public static double tier(final int refine, final double base, final double plus7, final double plus12) {
		return refine >= 12 ? plus12 : refine >= 7 ? plus7 : base;
	}

	public static boolean fullDraw(final float power) {
		return power >= FULL_DRAW - EPSILON;
	}

	public static String percent(final double share) {
		return Math.round(share * 100.0) + "%";
	}

	public static String seconds(final int ticks) {
		double seconds = ticks / 20.0;
		return (seconds == Math.floor(seconds) ? String.valueOf((int) seconds) : String.valueOf(seconds)) + " s";
	}

	public static String ordinal(final int number) {
		return number + switch (number % 10) {
			case 1 -> number % 100 == 11 ? "th" : "st";
			case 2 -> number % 100 == 12 ? "th" : "nd";
			case 3 -> number % 100 == 13 ? "th" : "rd";
			default -> "th";
		};
	}

	/** The roman numeral of an effect level, as the tooltip text writes it: 1 is I, 2 is II. */
	public static String roman(final int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> String.valueOf(level);
		};
	}

	/** Foes whose body is within the radius of a point, nearest first. The skipped entity is left out. */
	public static List<LivingEntity> foesAround(final ServerLevel level, final ServerPlayer holder, final Vec3 center, final double radius,
		final @Nullable LivingEntity skip) {
		double reach = radius * radius;
		AABB box = new AABB(center, center).inflate(radius);
		List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
			entity -> entity != skip && UniqueTargets.isFoe(holder, entity) && entity.getBoundingBox().distanceToSqr(center) <= reach));
		foes.sort(Comparator.comparingDouble(foe -> foe.distanceToSqr(center)));
		return foes;
	}

	/** Puts a harmful effect on a target without lowering a stronger one. */
	public static void curse(final LivingEntity target, final Holder<MobEffect> effect, final int ticks, final int amplifier) {
		UniqueEffects.addHarmful(target, effect, ticks, amplifier);
	}

	/** Puts a helpful effect on the holder, without particles and without a corner icon spam. */
	public static void bless(final ServerPlayer holder, final Holder<MobEffect> effect, final int ticks, final int amplifier) {
		MobEffectInstance current = holder.getEffect(effect);
		if (current != null && (current.getAmplifier() > amplifier || current.getAmplifier() == amplifier && current.getDuration() >= ticks)) {
			return;
		}
		holder.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
	}

	/** Multiplies the push a hit just gave a target. The default knockback of the hit has already run. */
	public static void scaleKnockback(final LivingEntity target, final double factor) {
		Vec3 motion = target.getDeltaMovement();
		target.setDeltaMovement(motion.x * factor, motion.y, motion.z * factor);
	}

	/** The living attacker of a blow in melee reach, or null for a projectile, a spell, fire or any other damage. */
	public static @Nullable LivingEntity meleeAttacker(final DamageSource source) {
		if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
			&& (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypeTags.IS_PLAYER_ATTACK))) {
			return attacker;
		}
		return null;
	}

	/** The stack of the holder that is the given item, main hand first. Used to change the real stack, not a copy. */
	public static @Nullable ItemStack held(final ServerPlayer holder, final Item item) {
		if (holder.getMainHandItem().is(item)) {
			return holder.getMainHandItem();
		}
		return holder.getOffhandItem().is(item) ? holder.getOffhandItem() : null;
	}
}
