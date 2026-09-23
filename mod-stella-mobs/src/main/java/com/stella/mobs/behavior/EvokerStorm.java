package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.projectile.StellaSmallFireball;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
 * The elite evoker's rain of fire: the first time an elite evoker falls to half health it calls a storm
 * on a 16 by 16 area centred where it stood, and forty balls fall over the next minute. Each ball is a
 * {@link StellaSmallFireball}, so it explodes without breaking a block and never sets the ground alight.
 *
 * The cast is one-shot and the flag is durable, so a chunk reload cannot re-arm it.
 */
public final class EvokerStorm {
	public static final int BALL_INTERVAL_TICKS = 30;
	public static final int DURATION_TICKS = 1200;
	public static final double AREA_RADIUS = 8.0;
	public static final double SPAWN_HEIGHT = 8.0;
	public static final float BALL_POWER = 1.2F;
	private static final float BALL_UNCERTAINTY = 0.0F;
	private static final AttachmentType<List<Active>> ACTIVE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "evoker_storms"), ArrayList::new
	);

	private EvokerStorm() {
	}

	/** One storm: where it was called, when it ends, and when the next ball is due. */
	private static final class Active {
		private final Mob caster;
		private final double x;
		private final double y;
		private final double z;
		private final long endTick;
		private long nextBallTick;
		private int balls;

		private Active(final Mob caster, final double x, final double y, final double z, final long endTick) {
			this.caster = caster;
			this.x = x;
			this.y = y;
			this.z = z;
			this.endTick = endTick;
			this.nextBallTick = endTick - DURATION_TICKS;
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

		begin(evoker, level);
	}

	public static void begin(final Mob evoker, final ServerLevel level) {
		long now = level.getGameTime();
		EliteState.setStormCast(evoker, true);
		((AttachmentTarget) level).getAttachedOrCreate(ACTIVE)
			.add(new Active(evoker, evoker.getX(), evoker.getY(), evoker.getZ(), now + DURATION_TICKS));

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=evokerStorm t={} STORM BEGIN centre={},{},{} window={} interval={} radius={}",
			now,
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
			// The window is half-open: the last ball is due at endTick minus one interval, so 1200 ticks
			// at one ball per 30 is exactly 40 balls, not 41.
			if (!storm.caster.isAlive() || storm.caster.isRemoved() || now >= storm.endTick) {
				storms.remove();
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=evokerStorm t={} STORM END balls={} casterAlive={}",
					now,
					storm.balls,
					storm.caster.isAlive()
				);
				continue;
			}

			if (now >= storm.nextBallTick) {
				storm.nextBallTick += BALL_INTERVAL_TICKS;
				spawnBall(storm, level, now);
			}
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
