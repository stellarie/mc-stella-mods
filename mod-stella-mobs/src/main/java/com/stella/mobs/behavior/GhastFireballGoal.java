package com.stella.mobs.behavior;

import com.stella.mobs.projectile.GhastWitherShot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The ghast's rank 7 shoot goal, replaced: a volley of three slow shots every seven ticks while the target is
 * in sight and in range. The three aim points lie on a horizontal circle around the target at 120 degree
 * steps, so the target stands at the centre of the triangle. A shot bursts in a blast that hurts and throws
 * bodies but breaks no block. Where it lands it hurts a player in reach, lights a few fires when mobs may
 * grief, and leaves a cloud of Wither and Slowness that lingers five seconds, its Wither stronger from an elite.
 */
public class GhastFireballGoal extends Goal {
	/** From the first shot of one volley to the first shot of the next. */
	public static final int ATTACK_INTERVAL_TICKS = 7;
	public static final int SHOTS_PER_ATTACK = 3;
	/** The radius of the horizontal circle the three aim points lie on. Under {@link #CLOUD_RADIUS}, so a target at the centre is inside all three clouds. */
	public static final double TRIANGLE_RADIUS = 2.5;
	/** Blocks per tick. A shot flies at this speed from the start and never gains on it. */
	public static final double SHOT_SPEED = 0.4;
	/** Long enough to cross {@link #MAX_RANGE} at {@link #SHOT_SPEED}, with a margin. */
	public static final int SHOT_LIFE_TICKS = 200;
	/** Vanilla's own ghast fireball power. */
	public static final float EXPLOSION_POWER = 1.0F;
	/** Vanilla's own firing range: a ghast fires only at a target within 64 blocks. */
	public static final double MAX_RANGE = 64.0;
	public static final float CLOUD_RADIUS = 3.0F;
	public static final int CLOUD_DURATION_TICKS = 100;
	/** A fire a landing lights burns out when the cloud does. */
	public static final int FIRE_LIFETIME_TICKS = CLOUD_DURATION_TICKS;
	public static final int WITHER_TICKS = 100;
	public static final int SLOWNESS_TICKS = 100;
	public static final int SLOWNESS_AMPLIFIER = 0;
	public static final int ORDINARY_WITHER_AMPLIFIER = 0;
	public static final int ELITE_WITHER_AMPLIFIER = 1;
	/** What a player in reach of a landing takes, before the ghast's level scaling. */
	public static final float LANDING_DAMAGE = 4.0F;
	public static final int FIRE_MIN = 1;
	public static final int FIRE_MAX = 3;
	/** The shoot sound plays on one volley in this many, so a volley every seven ticks is not a wall of noise. */
	private static final int SOUND_EVERY = 4;
	private static final double LEAD = 4.0;

	public interface Recorder {
		/** One call for each shot. */
		default void fired(final Ghast ghast, final int serverTick) {
		}

		default void volley(final Ghast ghast, final int serverTick, final Vec3 center, final List<Vec3> points) {
		}

		default void shot(final Ghast ghast, final GhastWitherShot shot) {
		}

		default void cloud(final Ghast ghast, final AreaEffectCloud cloud, final MobEffectInstance wither) {
		}

		default void landed(final Ghast ghast, final Vec3 at, final List<BlockPos> fires, final List<Player> hurt) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private final Ghast ghast;
	private int cooldown;
	private int volleys;

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

	/** Called by a landing shot with the fires it lit and the players it hurt. */
	public static void shotLanded(final Ghast ghast, final Vec3 at, final List<BlockPos> fires, final List<Player> hurt) {
		recorder.landed(ghast, at, fires, hurt);
	}

	/** The aim points: {@link #SHOTS_PER_ATTACK} of them on a horizontal circle round {@code center}, evenly spaced. */
	public static List<Vec3> trianglePoints(final Vec3 center, final double baseAngle) {
		List<Vec3> points = new ArrayList<>(SHOTS_PER_ATTACK);
		for (int index = 0; index < SHOTS_PER_ATTACK; index++) {
			double angle = baseAngle + index * (2.0 * Math.PI / SHOTS_PER_ATTACK);
			points.add(new Vec3(center.x + Math.cos(angle) * TRIANGLE_RADIUS, center.y, center.z + Math.sin(angle) * TRIANGLE_RADIUS));
		}

		return points;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.ghast.getTarget();
		return target != null && target.isAlive();
	}

	@Override
	public void start() {
		this.cooldown = ATTACK_INTERVAL_TICKS;
		this.volleys = 0;
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

		this.volley(target);
		this.cooldown = ATTACK_INTERVAL_TICKS;
	}

	private void volley(final LivingEntity target) {
		if (!(this.ghast.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 view = this.ghast.getViewVector(1.0F);
		Vec3 from = new Vec3(this.ghast.getX() + view.x * LEAD, this.ghast.getY(0.5), this.ghast.getZ() + view.z * LEAD);
		// The shots fly at a steady speed, so the lead is the target's own velocity over the flight.
		Vec3 center = target.position().add(ShotLead.offset(this.ghast, target, SHOT_SPEED));
		List<Vec3> points = trianglePoints(center, this.ghast.getRandom().nextDouble() * 2.0 * Math.PI);
		int amplifier = Elite.isElite(this.ghast) ? ELITE_WITHER_AMPLIFIER : ORDINARY_WITHER_AMPLIFIER;
		int tick = level.getServer().getTickCount();
		recorder.volley(this.ghast, tick, center, points);
		for (Vec3 point : points) {
			Vec3 aim = point.subtract(from);
			if (aim.lengthSqr() < 1.0E-4) {
				continue;
			}

			GhastWitherShot shot = new GhastWitherShot(level, this.ghast, aim, amplifier);
			shot.setPos(from);
			level.addFreshEntity(shot);
			recorder.shot(this.ghast, shot);
			recorder.fired(this.ghast, tick);
		}

		if (this.volleys++ % SOUND_EVERY == 0) {
			this.playSound(SoundEvents.GHAST_SHOOT);
		}
	}

	private void playSound(final SoundEvent event) {
		this.ghast.level().playSound(null, this.ghast.getX(), this.ghast.getY(), this.ghast.getZ(), event, this.ghast.getSoundSource(), 10.0F, 1.0F);
	}
}
