package com.stella.spells;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** What a spell calls to put a status effect on a target. Later stages call only this class. */
public final class SpellStatusApi {
	/** How long Frostbind and Frost Nova freeze a mob that took a fourth chill. */
	public static final int CHILL_FREEZE_TICKS = 60;
	/** The highest Chilled amplifier; one more chill turns into Frozen. */
	private static final int MAX_CHILL_AMPLIFIER = 2;

	private SpellStatusApi() {
	}

	/** Adds one chill stack; the fourth turns into Frozen for frozenTicks. Returns true when it froze. */
	public static boolean chill(final LivingEntity target, final int ticks, final int frozenTicks, final @Nullable Entity source) {
		MobEffectInstance chilled = target.getEffect(SpellStatus.CHILLED);
		int next = chilled == null ? 0 : chilled.getAmplifier() + 1;
		// Removing first drops any hidden lower stack, so no old level returns when the new one ends.
		target.removeEffect(SpellStatus.CHILLED);
		if (next > MAX_CHILL_AMPLIFIER) {
			freeze(target, frozenTicks, source);
			return true;
		}
		target.addEffect(new MobEffectInstance(SpellStatus.CHILLED, ticks, next), source);
		return false;
	}

	/** Halved on an elite or a boss. */
	public static void freeze(final LivingEntity target, final int ticks, final @Nullable Entity source) {
		target.addEffect(new MobEffectInstance(SpellStatus.FROZEN, SpellNumbers.controlTicksFor(target, ticks), 0), source);
	}

	/** Halved on an elite or a boss. */
	public static void stagger(final LivingEntity target, final int ticks, final @Nullable Entity source) {
		target.addEffect(new MobEffectInstance(SpellStatus.STAGGERED, SpellNumbers.controlTicksFor(target, ticks), 0), source);
	}

	public static void soak(final LivingEntity target, final int ticks, final @Nullable Entity source) {
		target.addEffect(new MobEffectInstance(SpellStatus.SOAKED, ticks, 0), source);
	}

	public static void scorch(final LivingEntity target, final int ticks, final @Nullable Entity source) {
		target.addEffect(new MobEffectInstance(SpellStatus.SCORCHED, ticks, 0), source);
	}

	public static void brittle(final LivingEntity target, final int ticks, final @Nullable Entity source) {
		target.addEffect(new MobEffectInstance(SpellStatus.BRITTLE, ticks, 0), source);
	}

	/** Marked, and the glow that shows it, for the same length. */
	public static void mark(final LivingEntity target, final int ticks, final @Nullable Entity source) {
		target.addEffect(new MobEffectInstance(SpellStatus.MARKED, ticks, 0), source);
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0), source);
	}

	/** A new ward replaces the pool when it is larger, and keeps the old pool otherwise. */
	public static void ward(final LivingEntity target, final float amount, final int ticks) {
		AttachmentTarget holder = (AttachmentTarget) target;
		Float old = target.hasEffect(SpellStatus.ARCANE_WARD) ? holder.getAttached(SpellStatus.WARD_POOL) : null;
		if (old == null || amount > old) {
			holder.setAttached(SpellStatus.WARD_POOL, amount);
		}
		target.addEffect(new MobEffectInstance(SpellStatus.ARCANE_WARD, ticks, 0));
	}

	/** Adds Bleeding; a repeat raises the amplifier by one, up to maxAmplifier. */
	public static void bleed(final LivingEntity target, final int ticks, final int maxAmplifier, final @Nullable Entity source) {
		MobEffectInstance bleeding = target.getEffect(SpellStatus.BLEEDING);
		int next = bleeding == null ? 0 : Math.min(maxAmplifier, bleeding.getAmplifier() + 1);
		target.removeEffect(SpellStatus.BLEEDING);
		target.addEffect(new MobEffectInstance(SpellStatus.BLEEDING, ticks, next), source);
		SpellStatus.rememberBleeder(target, source);
	}

	/** 1.5 when the target is Soaked, else 1.0. Callers multiply lightning spell damage by it. */
	public static float lightningFactor(final LivingEntity target) {
		return target.hasEffect(SpellStatus.SOAKED) ? SpellStatus.SOAKED_LIGHTNING_FACTOR : 1.0F;
	}
}
