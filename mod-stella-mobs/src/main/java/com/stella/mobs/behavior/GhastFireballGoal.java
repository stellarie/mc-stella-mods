package com.stella.mobs.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * R24: the ghast's rank 7 shoot goal, replaced so one goal serves both variants. An elite fires a real
 * LargeFireball at power 0, which is a look and a sound with no damage, no knockback and no block loop,
 * and the payload lands on the tick after the ball is removed. An ordinary ghast keeps power 1 and no
 * payload. The charge cycle starts at 1, so the interval is the shoot tick minus the recharge.
 */
public class GhastFireballGoal extends Goal {
	public static final int VANILLA_SHOOT_TICK = 20;
	public static final int ELITE_SHOOT_TICK = 30;
	public static final int VANILLA_RECHARGE = -40;
	public static final int ELITE_RECHARGE = -70;
	public static final int VANILLA_INTERVAL_TICKS = VANILLA_SHOOT_TICK - VANILLA_RECHARGE;
	public static final int ELITE_INTERVAL_TICKS = ELITE_SHOOT_TICK - ELITE_RECHARGE;
	public static final int VANILLA_WARN_TICK = 10;
	public static final int ELITE_WARN_TICK = ELITE_SHOOT_TICK / 2;
	public static final int ELITE_POWER = 0;
	public static final int VANILLA_POWER = 1;
	public static final double PAYLOAD_RADIUS = 3.0;
	public static final float BURN_SECONDS = 8.0F;
	public static final int SLOWNESS_TICKS = 400;
	public static final int SLOWNESS_AMPLIFIER = 3;
	public static final int POISON_TICKS = 432;
	public static final int POISON_AMPLIFIER = 1;

	private final Ghast ghast;
	private final boolean elite;
	private int charge = 1;
	private LargeFireball pending;

	public GhastFireballGoal(final Ghast ghast, final boolean elite) {
		this.ghast = ghast;
		this.elite = elite;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.ghast.getTarget();
		return target != null && target.isAlive();
	}

	@Override
	public void start() {
		this.ghast.setCharging(false);
	}

	@Override
	public void stop() {
		this.ghast.setCharging(false);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.settle();

		LivingEntity target = this.ghast.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		this.ghast.getLookControl().setLookAt(target, 10.0F, 10.0F);
		int shootTick = this.elite ? ELITE_SHOOT_TICK : VANILLA_SHOOT_TICK;
		int warnTick = this.elite ? ELITE_WARN_TICK : VANILLA_WARN_TICK;

		if (this.charge == warnTick) {
			this.ghast.setCharging(true);
			this.playSound(SoundEvents.GHAST_WARN);
		}

		if (this.charge >= shootTick) {
			this.shoot(target);
			this.playSound(SoundEvents.GHAST_SHOOT);
			this.charge = this.elite ? ELITE_RECHARGE : VANILLA_RECHARGE;
			return;
		}

		this.charge++;
	}

	/** One tick after the ball is removed, its payload lands where it stopped. */
	private void settle() {
		if (!this.elite || this.pending == null) {
			return;
		}

		if (!this.pending.isRemoved()) {
			return;
		}

		Vec3 hit = this.pending.position();
		this.pending = null;
		this.applyPayload(hit);
	}

	private void shoot(final LivingEntity target) {
		if (!(this.ghast.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 view = this.ghast.getViewVector(1.0F);
		double lead = 4.0;
		LargeFireball ball = new LargeFireball(
			level,
			this.ghast,
			new Vec3(
				target.getX() - (this.ghast.getX() + view.x * lead),
				target.getY(0.5) - (this.ghast.getY(0.5) + view.y * lead),
				target.getZ() - (this.ghast.getZ() + view.z * lead)
			),
			this.elite ? ELITE_POWER : VANILLA_POWER
		);
		ball.setPos(this.ghast.getX() + view.x * lead, this.ghast.getY(0.5), this.ghast.getZ() + view.z * lead);
		level.addFreshEntity(ball);

		if (this.elite) {
			this.pending = ball;
		}
	}

	private void applyPayload(final Vec3 hit) {
		if (this.ghast.level() instanceof ServerLevel level) {
			applyPayload(level, this.ghast, hit, PAYLOAD_RADIUS);
		}
	}

	/**
	 * The payload as a call that takes a level, a source, a position and a radius, so a test can assert
	 * what a blast does without a fireball ever landing.
	 */
	public static void applyPayload(
		final ServerLevel level, final LivingEntity source, final Vec3 hit, final double radius
	) {
		AABB box = new AABB(hit, hit).inflate(radius);

		// A direct predicate, not TargetingConditions: a target the blast just hurt carries immunity
		// frames, and the combat conditions refuse an invulnerable target, which is exactly the victim.
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, candidate -> isPayloadTarget(source, candidate))) {
			victim.igniteForSeconds(BURN_SECONDS);
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, SLOWNESS_AMPLIFIER), source);
			victim.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_AMPLIFIER), source);
		}
	}

	private static boolean isPayloadTarget(final LivingEntity source, final LivingEntity candidate) {
		return candidate != source
			&& candidate.isAlive()
			&& !candidate.isSpectator()
			&& !source.isAlliedTo(candidate);
	}

	private void playSound(final net.minecraft.sounds.SoundEvent event) {
		this.ghast.level().playSound(
			null, this.ghast.getX(), this.ghast.getY(), this.ghast.getZ(), event, this.ghast.getSoundSource(), 10.0F, 1.0F
		);
	}
}
