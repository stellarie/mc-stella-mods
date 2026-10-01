package com.stella.mobs.behavior;

import java.util.UUID;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * M2: the slow a skeleton's single shot leaves on what it hits. A burst arrow carries no mark and no side
 * effect: it deals its damage and nothing else.
 */
public final class SkeletonVolley {
	/** A skeleton's single shot, which slows what it hits. */
	public static final String SLOW_TAG = "mod-stella-mobs.slow_shot";
	public static final int SLOW_TICKS = 40;
	public static final int SLOW_AMPLIFIER = 0;
	public static final int ELITE_SLOW_AMPLIFIER = 1;
	public static final int BOSS_SLOW_AMPLIFIER = 2;

	/** Test seam: each arrow as it leaves the bow. */
	public interface Recorder {
		default void burst(final Mob shooter, final UUID arrow, final Vec3 velocity) {
		}

		default void shot(final Mob shooter, final UUID arrow, final Vec3 velocity) {
		}

		default void slowed(final Mob shooter, final LivingEntity victim, final MobEffectInstance slow) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private SkeletonVolley() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	static Recorder recorder() {
		return recorder;
	}

	/** A single shot's slow, on whatever living thing it strikes, harder from an elite and harder again from a boss. */
	public static void onEntityHit(final AbstractArrow arrow, final Entity struck) {
		if (!(struck instanceof LivingEntity victim) || !arrow.entityTags().contains(SLOW_TAG) || !(arrow.getOwner() instanceof Mob shooter)) {
			return;
		}
		arrow.removeTag(SLOW_TAG);
		int amplifier = Bosses.isBoss(shooter) ? BOSS_SLOW_AMPLIFIER : Elite.isElite(shooter) ? ELITE_SLOW_AMPLIFIER : SLOW_AMPLIFIER;
		MobEffectInstance slow = new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, amplifier);
		victim.addEffect(slow, shooter);
		recorder.slowed(shooter, victim, slow);
	}
}
