package com.stella.mobs.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
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
 * reaches it. Every blaze's attack fires one.
 */
public class StellaSmallFireball extends SmallFireball {
	public static final float DEFAULT_EXPLOSION_RADIUS = 1.5F;
	/** A ball that has hit nothing by now burns out, so a miss never lands far away. */
	public static final int MAX_LIFE_TICKS = 100;
	private float explosionRadius = DEFAULT_EXPLOSION_RADIUS;

	public StellaSmallFireball(final Level level, final double x, final double y, final double z, final Vec3 direction) {
		super(level, x, y, z, direction);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.tickCount > MAX_LIFE_TICKS) {
			this.discard();
		}
	}

	// A ball saved in flight would reload as a vanilla fireball that lights the ground, so none is saved.
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	public void setExplosionRadius(final float explosionRadius) {
		this.explosionRadius = explosionRadius;
	}

	/**
	 * The payload. {@code NONE} leaves every block alone, and {@link #BODIES_ONLY} leaves every entity that is
	 * not a body alone, so dropped items, frames and armor stands survive. The owner is the source so vanilla
	 * leaves the caster out of its own blast.
	 */
	protected void onImpact() {
		if (this.level() instanceof ServerLevel serverLevel) {
			Entity source = this.getOwner() != null ? this.getOwner() : this;
			serverLevel.explode(
				source, null, BODIES_ONLY, this.getX(), this.getY(), this.getZ(), this.explosionRadius, false, Level.ExplosionInteraction.NONE
			);
		}
	}

	/** Hurts and pushes living bodies only, never an item, a hanging decoration or an armor stand. */
	public static final ExplosionDamageCalculator BODIES_ONLY = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(final Explosion explosion, final Entity entity) {
			return isBody(entity) && super.shouldDamageEntity(explosion, entity);
		}

		@Override
		public float getKnockbackMultiplier(final Entity entity) {
			return isBody(entity) ? super.getKnockbackMultiplier(entity) : 0.0F;
		}
	};

	private static boolean isBody(final Entity entity) {
		return entity instanceof LivingEntity && !(entity instanceof ArmorStand);
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
