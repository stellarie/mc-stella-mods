package com.stella.mobs.projectile;

import com.stella.mobs.behavior.Chill;
import com.stella.mobs.behavior.WitherEncounter;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Wither skull of the encounter fight. It breaks no block and hurts only bodies within the blast radius of its
 * impact. A hit is a share of the owner's level hit, adds Wither, and may add chill or burning.
 * It keeps the vanilla entity type, so a vanilla client draws it.
 */
public class WitherSkullShot extends WitherSkull {
	/** What a skull carries. A range of 0 means no limit. */
	public record Spec(double share, int witherTicks, int witherAmplifier, double maxRange, int lifeTicks, boolean dangerous) {
	}

	private Spec spec = new Spec(0.0, 0, 0, 0.0, 1, false);
	private Vec3 origin = Vec3.ZERO;

	public WitherSkullShot(final Level level, final LivingEntity owner, final Vec3 direction) {
		super(level, owner, direction);
	}

	public void configure(final Spec newSpec, final Vec3 start) {
		this.spec = newSpec;
		this.origin = start;
	}

	public Vec3 origin() {
		return this.origin;
	}

	public Spec spec() {
		return this.spec;
	}

	/** A skull saved in flight would reload as a vanilla one that breaks blocks, so none is saved. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() || this.isRemoved()) {
			return;
		}
		boolean burntOut = this.tickCount >= this.spec.lifeTicks();
		boolean outOfRange = this.spec.maxRange() > 0.0 && this.position().distanceToSqr(this.origin) > this.spec.maxRange() * this.spec.maxRange();
		if (burntOut || outOfRange) {
			this.discard();
		}
	}

	/** A skull passes through the wither's own side, so a minion in its way does not set it off. */
	@Override
	protected boolean canHitEntity(final Entity entity) {
		return super.canHitEntity(entity) && !(entity instanceof Enemy);
	}

	/** The vanilla direct hit and the owner's heal are gone. The blast does the work. */
	@Override
	protected void onHitEntity(final net.minecraft.world.phys.EntityHitResult hitResult) {
	}

	@Override
	protected void onHit(final HitResult hitResult) {
		if (this.level() instanceof ServerLevel) {
			this.detonate(hitResult.getLocation(), this.level().getRandom());
		}
		this.discard();
	}

	/** The whole blast. No vanilla explosion runs, so no block is touched. */
	public void detonate(final Vec3 point, final RandomSource random) {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		WitherConfig config = WitherConfigStore.get();
		double radius = config.get(WitherConfig.BLAST_RADIUS);
		Entity owner = this.getOwner();
		float hit = this.hitSize(owner);
		AABB reach = new AABB(point, point).inflate(radius);
		DamageSource source = level.damageSources().witherSkull(this, owner);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, reach, entity -> this.isVictim(entity, owner))) {
			if (victim.getBoundingBox().distanceToSqr(point) > radius * radius) {
				continue;
			}
			victim.hurtServer(level, source, hit);
			victim.addEffect(new MobEffectInstance(MobEffects.WITHER, this.spec.witherTicks(), this.spec.witherAmplifier()), owner);
			if (random.nextDouble() < config.get(WitherConfig.BLAST_RIDER_CHANCE)) {
				if (random.nextBoolean()) {
					int chill = config.getInt(WitherConfig.BLAST_CHILL_TICKS);
					Chill.apply(victim, owner != null ? owner : this, chill, chill, 0);
				} else {
					victim.igniteForSeconds((float) config.get(WitherConfig.BLAST_BURN_SECONDS));
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6F, 1.0F);
	}

	/** The share of the owner's standard level hit. The mobs damage mixin leaves a skull alone, so it scales once. */
	private float hitSize(final Entity owner) {
		int level = owner instanceof Mob mob ? ThreatState.level(mob) : 0;
		return (float) (this.spec.share() * (8.0 * SpawnScaling.damageMultiplier(level) + SpawnScaling.tailFlat(level)));
	}

	private boolean isVictim(final LivingEntity entity, final Entity owner) {
		return entity != owner && !(entity instanceof Enemy) && !entity.isSpectator() && !(entity instanceof ArmorStand)
			&& !WitherEncounter.isMinion(entity);
	}
}
