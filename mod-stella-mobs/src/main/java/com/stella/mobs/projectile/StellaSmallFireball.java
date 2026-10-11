package com.stella.mobs.projectile;

import java.util.ArrayList;
import java.util.List;
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
 *
 * <p>A blaze's ball may carry a split distance. When the ball has flown that far it is replaced by
 * {@link #SPLIT_COUNT} children that fan out around its heading. A child has the owner and the explosion of
 * its parent, never splits, and never strikes its owner.
 */
public class StellaSmallFireball extends SmallFireball {
	public static final float DEFAULT_EXPLOSION_RADIUS = 1.5F;
	/** A ball that has hit nothing by now burns out, so a miss never lands far away. */
	public static final int MAX_LIFE_TICKS = 100;
	public static final int SPLIT_COUNT = 3;
	/** The angle between neighbouring children, so the three fan out by this much either side of the heading. */
	public static final double SPLIT_SPREAD_DEGREES = 12.0;

	public interface Recorder {
		default void split(final StellaSmallFireball parent, final Vec3 heading, final List<StellaSmallFireball> children) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private float explosionRadius = DEFAULT_EXPLOSION_RADIUS;
	/** Blocks of flight before the ball splits. Zero means it never does. */
	private double splitDistance;
	private Vec3 splitOrigin = Vec3.ZERO;
	private boolean splitChild;

	public StellaSmallFireball(final Level level, final double x, final double y, final double z, final Vec3 direction) {
		super(level, x, y, z, direction);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}

		if (this.tickCount > MAX_LIFE_TICKS) {
			this.discard();
		} else if (this.splitDistance > 0.0 && !this.isRemoved() && this.position().distanceTo(this.splitOrigin) >= this.splitDistance) {
			this.splitNow();
		}
	}

	/**
	 * A child never strikes its owner, even after it has left the owner's box. Siblings need no rule: vanilla
	 * does not let one small fireball strike another.
	 */
	@Override
	protected boolean canHitEntity(final Entity entity) {
		return !(this.splitChild && entity == this.getOwner()) && super.canHitEntity(entity);
	}

	public void setSplitDistance(final double splitDistance) {
		this.splitDistance = splitDistance;
		this.splitOrigin = this.position();
	}

	public double getSplitDistance() {
		return this.splitDistance;
	}

	public boolean isSplitChild() {
		return this.splitChild;
	}

	public float getExplosionRadius() {
		return this.explosionRadius;
	}

	/**
	 * Replaces this ball with its children at the place it has reached. A child or a ball with no heading
	 * does not split. Returns the children, or an empty list when none was made.
	 */
	public List<StellaSmallFireball> splitNow() {
		Vec3 motion = this.getDeltaMovement();
		if (!(this.level() instanceof ServerLevel level) || this.splitChild || this.isRemoved() || motion.lengthSqr() < 1.0E-8) {
			return List.of();
		}

		double speed = motion.length();
		Vec3 forward = motion.scale(1.0 / speed);
		Vec3 axis = fanAxis(forward);
		List<StellaSmallFireball> children = new ArrayList<>(SPLIT_COUNT);
		for (int index = 0; index < SPLIT_COUNT; index++) {
			double degrees = (index - (SPLIT_COUNT - 1) / 2.0) * SPLIT_SPREAD_DEGREES;
			Vec3 heading = rotate(forward, axis, Math.toRadians(degrees));
			StellaSmallFireball child = new StellaSmallFireball(level, this.getX(), this.getY(), this.getZ(), heading);
			child.setOwner(this.getOwner());
			child.explosionRadius = this.explosionRadius;
			child.splitChild = true;
			child.accelerationPower = this.accelerationPower;
			child.setDeltaMovement(heading.scale(speed));
			children.add(child);
		}

		this.discard();
		children.forEach(level::addFreshEntity);
		recorder.split(this, forward, List.copyOf(children));
		return children;
	}

	/** The upright axis the fan turns about, made square to the heading, so a level shot fans out sideways. */
	private static Vec3 fanAxis(final Vec3 forward) {
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 axis = up.subtract(forward.scale(up.dot(forward)));
		if (axis.lengthSqr() < 1.0E-6) {
			Vec3 east = new Vec3(1.0, 0.0, 0.0);
			axis = east.subtract(forward.scale(east.dot(forward)));
		}

		return axis.normalize();
	}

	/** Turns {@code vector} about {@code axis}, which stands square to it, by {@code radians}. */
	private static Vec3 rotate(final Vec3 vector, final Vec3 axis, final double radians) {
		return vector.scale(Math.cos(radians)).add(axis.cross(vector).scale(Math.sin(radians))).normalize();
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
