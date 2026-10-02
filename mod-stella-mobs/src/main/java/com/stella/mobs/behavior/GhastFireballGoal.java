package com.stella.mobs.behavior;

import com.stella.mobs.projectile.GhastWitherShot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.phys.Vec3;

/**
 * The ghast's rank 7 shoot goal, replaced: a burst of two shots every three seconds while the target is in
 * sight. A shot bursts in a blast that hurts and throws bodies but breaks no block, and leaves a small cloud
 * of Wither and Poison that lingers two seconds, its Wither stronger from an elite.
 */
public class GhastFireballGoal extends Goal {
	public static final int BURST_SHOTS = 2;
	/** The gap between the two shots of a burst. */
	public static final int BURST_GAP_TICKS = 6;
	/** From the first shot of one burst to the first shot of the next. */
	public static final int BURST_INTERVAL_TICKS = 60;
	/** Vanilla's own ghast fireball power. */
	public static final float EXPLOSION_POWER = 1.0F;
	/** Vanilla's own firing range: a ghast fires only at a target within 64 blocks. */
	public static final double MAX_RANGE = 64.0;
	public static final float CLOUD_RADIUS = 1.5F;
	public static final int CLOUD_DURATION_TICKS = 40;
	public static final int WITHER_TICKS = 100;
	public static final int POISON_TICKS = 100;
	public static final int POISON_AMPLIFIER = 0;
	public static final int ORDINARY_WITHER_AMPLIFIER = 0;
	public static final int ELITE_WITHER_AMPLIFIER = 1;
	private static final double LEAD = 4.0;

	public interface Recorder {
		default void fired(final Ghast ghast, final int serverTick) {
		}

		default void cloud(final Ghast ghast, final AreaEffectCloud cloud, final MobEffectInstance wither) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private final Ghast ghast;
	private int cooldown;
	private int shotInBurst;

	public GhastFireballGoal(final Ghast ghast) {
		this.ghast = ghast;
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** Called by a landing shot, so the record holds what the ghast actually left. */
	public static void cloudLanded(final Ghast ghast, final AreaEffectCloud cloud, final MobEffectInstance wither) {
		recorder.cloud(ghast, cloud, wither);
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.ghast.getTarget();
		return target != null && target.isAlive();
	}

	@Override
	public void start() {
		this.cooldown = BURST_GAP_TICKS;
		this.shotInBurst = 0;
		this.ghast.setCharging(true);
		this.playSound(SoundEvents.GHAST_WARN);
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
		LivingEntity target = this.ghast.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		this.ghast.getLookControl().setLookAt(target, 10.0F, 10.0F);
		if (--this.cooldown > 0 || this.ghast.distanceToSqr(target) > MAX_RANGE * MAX_RANGE || !this.ghast.hasLineOfSight(target)) {
			return;
		}

		this.shoot(target);
		this.shotInBurst++;
		if (this.shotInBurst < BURST_SHOTS) {
			this.cooldown = BURST_GAP_TICKS;
		} else {
			this.shotInBurst = 0;
			this.cooldown = BURST_INTERVAL_TICKS - BURST_GAP_TICKS * (BURST_SHOTS - 1);
		}
	}

	private void shoot(final LivingEntity target) {
		if (!(this.ghast.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 view = this.ghast.getViewVector(1.0F);
		Vec3 from = new Vec3(this.ghast.getX() + view.x * LEAD, this.ghast.getY(0.5), this.ghast.getZ() + view.z * LEAD);
		Vec3 lead = ShotLead.offset(this.ghast, target, ShotLead.fireballSpeed(this.ghast.distanceTo(target)));
		Vec3 aim = new Vec3(target.getX() + lead.x - from.x, target.getY(0.5) + lead.y - from.y, target.getZ() + lead.z - from.z);
		int amplifier = Elite.isElite(this.ghast) ? ELITE_WITHER_AMPLIFIER : ORDINARY_WITHER_AMPLIFIER;
		GhastWitherShot shot = new GhastWitherShot(level, this.ghast, aim, amplifier);
		shot.setPos(from);
		level.addFreshEntity(shot);
		recorder.fired(this.ghast, level.getServer().getTickCount());
		if (this.shotInBurst == 0) {
			this.playSound(SoundEvents.GHAST_SHOOT);
		}
	}

	private void playSound(final SoundEvent event) {
		this.ghast.level().playSound(null, this.ghast.getX(), this.ghast.getY(), this.ghast.getZ(), event, this.ghast.getSoundSource(), 10.0F, 1.0F);
	}
}
