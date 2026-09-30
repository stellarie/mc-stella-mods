package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.projectile.StellaSmallFireball;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The elite evoker's rain of fire. M8: it opens the fight the moment the evoker has a target, with no
 * wind-up, on a 48 by 48 area centred where it stood, and sixty balls fall over the next minute. A fall to
 * half health casts it instead if the evoker was hurt before it ever had a target. Each ball is a
 * {@link StellaSmallFireball}, so it explodes without breaking a block and never sets the ground alight.
 *
 * Either trigger spends the same durable flag, so the storm comes once per life and a chunk reload
 * cannot re-arm it.
 */
public final class EvokerStorm {
	public static final int BALL_INTERVAL_TICKS = 20;
	public static final int DURATION_TICKS = 1200;
	public static final double BASE_RADIUS = 8.0;
	// M8: "increase by 200%" read literally, so the radius is three times the old one.
	public static final double RADIUS_INCREASE = 2.0;
	public static final double AREA_RADIUS = BASE_RADIUS * (1.0 + RADIUS_INCREASE);
	public static final String REASON_OPENING = "opening";
	public static final String REASON_HALF_HEALTH = "half_health";
	public static final double SPAWN_HEIGHT = 8.0;
	public static final float BALL_POWER = 1.2F;
	private static final float BALL_UNCERTAINTY = 0.0F;
	private static final AttachmentType<List<Active>> ACTIVE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "evoker_storms"), ArrayList::new
	);

	/** Test seam: what each storm did, keyed by the caster that owns it. */
	public interface Recorder {
		default void begun(final Mob caster, final String reason, final Vec3 centre) {
		}

		default void ball(final Mob caster, final UUID ball, final Vec3 at, final int age) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private EvokerStorm() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/**
	 * One storm: where it was called, its own age in ticks, and the age the next ball is due at. The age
	 * advances once per level tick, so the cadence holds wherever the game clock stands.
	 */
	private static final class Active {
		private final Mob caster;
		private final double x;
		private final double y;
		private final double z;
		private int age;
		private int nextBallAge;
		private int balls;

		private Active(final Mob caster, final double x, final double y, final double z) {
			this.caster = caster;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the evoker storm attachment {}", ACTIVE.identifier());
	}

	/**
	 * The trigger, on the damage event rather than on a tick, so it costs one callback per hit. Elite
	 * only, unspent only, and at or below half health only.
	 */
	public static void afterDamage(
		final LivingEntity entity, final DamageSource source, final float amount, final float taken, final boolean blocked
	) {
		if (!(entity instanceof Evoker evoker) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		if (!Elite.isElite(evoker) || EliteState.isStormCast(evoker)) {
			return;
		}

		if (evoker.getHealth() > evoker.getMaxHealth() / 2.0F) {
			return;
		}

		begin(evoker, level, REASON_HALF_HEALTH);
	}

	public static void begin(final Mob evoker, final ServerLevel level, final String reason) {
		long now = level.getGameTime();
		EliteState.setStormCast(evoker, true);
		((AttachmentTarget) level).getAttachedOrCreate(ACTIVE)
			.add(new Active(evoker, evoker.getX(), evoker.getY(), evoker.getZ()));

		recorder.begun(evoker, reason, evoker.position());
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=evokerStorm t={} STORM BEGIN reason={} centre={},{},{} window={} interval={} radius={}",
			now,
			reason,
			String.format(Locale.ROOT, "%.2f", evoker.getX()),
			String.format(Locale.ROOT, "%.2f", evoker.getY()),
			String.format(Locale.ROOT, "%.2f", evoker.getZ()),
			DURATION_TICKS,
			BALL_INTERVAL_TICKS,
			AREA_RADIUS
		);
	}

	/** Advances every live storm. A storm ends when its window closes or its caster dies. */
	public static void tick(final ServerLevel level) {
		List<Active> active = ((AttachmentTarget) level).getAttached(ACTIVE);
		if (active == null || active.isEmpty()) {
			return;
		}

		long now = level.getGameTime();
		Iterator<Active> storms = active.iterator();

		while (storms.hasNext()) {
			Active storm = storms.next();
			// The window is half-open: the last ball is due one interval before the window closes, so the
			// count is the window over the interval, never one more.
			if (!storm.caster.isAlive() || storm.caster.isRemoved() || storm.age >= DURATION_TICKS) {
				storms.remove();
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=evokerStorm t={} STORM END balls={} casterAlive={}",
					now,
					storm.balls,
					storm.caster.isAlive()
				);
				continue;
			}

			if (storm.age >= storm.nextBallAge) {
				storm.nextBallAge += BALL_INTERVAL_TICKS;
				spawnBall(storm, level, now);
			}
			storm.age++;
		}
	}

	private static void spawnBall(final Active storm, final ServerLevel level, final long now) {
		RandomSource random = storm.caster.getRandom();
		double x = storm.x + (random.nextDouble() * 2.0 - 1.0) * AREA_RADIUS;
		double z = storm.z + (random.nextDouble() * 2.0 - 1.0) * AREA_RADIUS;
		double y = storm.y + SPAWN_HEIGHT;
		StellaSmallFireball ball = new StellaSmallFireball(level, x, y, z, new Vec3(0.0, -1.0, 0.0));
		ball.setOwner(storm.caster);
		Projectile.spawnProjectileUsingShoot(
			ball, level, new ItemStack(Items.FIRE_CHARGE), 0.0, -1.0, 0.0, BALL_POWER, BALL_UNCERTAINTY
		);
		storm.balls++;
		recorder.ball(storm.caster, ball.getUUID(), new Vec3(x, y, z), storm.age);

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=evokerStorm t={} BALL index={} at={},{},{}",
			now,
			storm.balls,
			String.format(Locale.ROOT, "%.2f", x),
			String.format(Locale.ROOT, "%.2f", y),
			String.format(Locale.ROOT, "%.2f", z)
		);
	}

	public static boolean isActive(final Mob caster) {
		return stormsFor(caster) > 0;
	}

	/** Caster-scoped, because a game test level holds every arena at once and the level-wide count mixes them. */
	public static int stormsFor(final Mob caster) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return 0;
		}

		List<Active> active = ((AttachmentTarget) level).getAttached(ACTIVE);
		return active == null ? 0 : (int) active.stream().filter(storm -> storm.caster == caster).count();
	}

	public static int activeCount(final ServerLevel level) {
		List<Active> active = ((AttachmentTarget) level).getAttached(ACTIVE);
		return active == null ? 0 : active.size();
	}
}
