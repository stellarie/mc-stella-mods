package com.stella.mobs.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Delivers a mod-chosen potion through vanilla's own spawn call, so the game still owns the
 * projectile, the splash box, and the effect application.
 */
public final class WitchThrow {
	private static final float THROW_SPEED = 0.75F;
	// Zero spread, so a throw lands on the mark. Vanilla uses 8.0.
	public static final float UNCERTAINTY = 0.0F;
	private static final double GRAVITY_PER_TICK = 0.03;
	// The drop over the flight at THROW_SPEED, so the aim can cancel it.
	private static final double DROP_PER_BLOCK_SQUARED = GRAVITY_PER_TICK / 2.0 / (THROW_SPEED * THROW_SPEED);

	private WitchThrow() {
	}

	/**
	 * Aims at the target's mid body and adds back the flight drop. Vanilla aims from the feet with a
	 * linear term, which overshoots a level target by more than a block.
	 */
	public static void atTarget(final Witch witch, final ItemStack stack, final LivingEntity target) {
		shoot(witch, stack, aim(witch, target), false);
	}

	/** W12: the same aim and speed, but the projectile leaves a lingering cloud where it lands. */
	public static void lingerAtTarget(final Witch witch, final ItemStack stack, final LivingEntity target) {
		shoot(witch, stack, aim(witch, target), true);
	}

	/** Straight down, so the splash box is centred on the witch itself. */
	public static void atSelf(final Witch witch, final ItemStack stack) {
		shoot(witch, stack, new Aim(0.0, -1.0, 0.0), false);
	}

	private static Aim aim(final Witch witch, final LivingEntity target) {
		Vec3 movement = target.getDeltaMovement();
		double xd = target.getX() + movement.x - witch.getX();
		double zd = target.getZ() + movement.z - witch.getZ();
		double dist = Math.sqrt(xd * xd + zd * zd);
		double midBody = target.getY() + target.getBbHeight() * 0.5;
		double yd = midBody + DROP_PER_BLOCK_SQUARED * dist * dist - (witch.getEyeY() - 0.1);
		return new Aim(xd, yd, zd);
	}

	private static void shoot(final Witch witch, final ItemStack stack, final Aim aim, final boolean lingering) {
		if (!(witch.level() instanceof ServerLevel level)) {
			return;
		}

		if (lingering) {
			Projectile.spawnProjectileUsingShoot(
				ThrownLingeringPotion::new, level, stack, witch, aim.x(), aim.y(), aim.z(), THROW_SPEED, UNCERTAINTY
			);
		} else {
			Projectile.spawnProjectileUsingShoot(
				ThrownSplashPotion::new, level, stack, witch, aim.x(), aim.y(), aim.z(), THROW_SPEED, UNCERTAINTY
			);
		}

		if (!witch.isSilent()) {
			witch.level()
				.playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
		}
	}

	/** One throw's direction in the witch's own frame. */
	private record Aim(double x, double y, double z) {
	}
}
