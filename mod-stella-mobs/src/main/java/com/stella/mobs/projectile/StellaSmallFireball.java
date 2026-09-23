package com.stella.mobs.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A small fireball whose impact is a non-destructive explosion instead of an ignition. The class exists
 * for one reason: {@code SmallFireball.onHitBlock} writes fire to the ground, and this override never
 * reaches it. The elite blaze's field spread reuses this class as its hook.
 */
public class StellaSmallFireball extends SmallFireball {
	public static final float DEFAULT_EXPLOSION_RADIUS = 1.5F;
	private float explosionRadius = DEFAULT_EXPLOSION_RADIUS;

	public StellaSmallFireball(final Level level, final double x, final double y, final double z, final Vec3 direction) {
		super(level, x, y, z, direction);
	}

	public void setExplosionRadius(final float explosionRadius) {
		this.explosionRadius = explosionRadius;
	}

	/**
	 * The payload. {@code NONE} damages and knocks entities back with zero block damage, and the owner is
	 * the source so vanilla leaves the caster out of its own blast.
	 */
	protected void onImpact() {
		if (this.level() instanceof ServerLevel serverLevel) {
			Entity source = this.getOwner() != null ? this.getOwner() : this;
			serverLevel.explode(
				source, this.getX(), this.getY(), this.getZ(), this.explosionRadius, Level.ExplosionInteraction.NONE
			);
		}
	}

	/** No five second ignition on the victim: the blast is the payload. */
	@Override
	protected void onHitEntity(final EntityHitResult hitResult) {
		this.onImpact();
	}

	/** No fire on the ground: this override deliberately does not call {@code SmallFireball.onHitBlock}. */
	@Override
	protected void onHitBlock(final BlockHitResult hitResult) {
		this.onImpact();
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("ExplosionRadius", this.explosionRadius);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.explosionRadius = input.getFloatOr("ExplosionRadius", DEFAULT_EXPLOSION_RADIUS);
	}
}
