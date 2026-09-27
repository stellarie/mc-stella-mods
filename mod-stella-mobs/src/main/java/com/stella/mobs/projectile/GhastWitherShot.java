package com.stella.mobs.projectile;

import com.stella.mobs.behavior.GhastFireballGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A ghast's shot that never explodes: where it lands it leaves a small lingering cloud of Wither. */
public class GhastWitherShot extends LargeFireball {
	/** How far below a hit the cloud looks for ground. */
	private static final double SETTLE_DEPTH = 4.0;
	private final int witherAmplifier;

	@Override
	public void tick() {
		super.tick();
		// A shot that has hit nothing by now burns out, so a miss never leaves its cloud far away.
		if (!this.level().isClientSide() && this.tickCount > StellaSmallFireball.MAX_LIFE_TICKS) {
			this.discard();
		}
	}

	public GhastWitherShot(final Level level, final LivingEntity owner, final Vec3 direction, final int witherAmplifier) {
		super(level, owner, direction, 0);
		this.witherAmplifier = witherAmplifier;
	}

	// The whole hit, replaced: no blast, no direct damage, only the cloud.
	@Override
	protected void onHit(final HitResult hitResult) {
		if (this.level() instanceof ServerLevel level) {
			Vec3 at = settle(level, hitResult);
			AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
			MobEffectInstance wither = new MobEffectInstance(MobEffects.WITHER, GhastFireballGoal.WITHER_TICKS, this.witherAmplifier);
			if (this.getOwner() instanceof LivingEntity owner) {
				cloud.setOwner(owner);
			}
			cloud.setRadius(GhastFireballGoal.CLOUD_RADIUS);
			cloud.setDuration(GhastFireballGoal.CLOUD_DURATION_TICKS);
			cloud.setWaitTime(0);
			cloud.setRadiusPerTick(0.0F);
			cloud.addEffect(wither);
			level.addFreshEntity(cloud);
			if (this.getOwner() instanceof Ghast ghast) {
				GhastFireballGoal.cloudLanded(ghast, cloud, wither);
			}
		}
		this.discard();
	}

	// A cloud only reaches the bodies it lies across, so it settles at a struck body's feet or on the ground below the hit.
	private Vec3 settle(final ServerLevel level, final HitResult hitResult) {
		if (hitResult instanceof EntityHitResult entityHit) {
			return entityHit.getEntity().position();
		}

		Vec3 at = hitResult.getLocation();
		BlockHitResult ground = level.clip(new ClipContext(at, at.subtract(0.0, SETTLE_DEPTH, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return ground.getType() == HitResult.Type.BLOCK ? ground.getLocation() : at;
	}
}
