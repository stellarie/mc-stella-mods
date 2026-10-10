package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * An encounter Wither rages twice, each time for thirty seconds. At half health a barrage of skulls leaves it in every
 * direction, up to sixteen blocks. At a quarter health skulls rain within twenty-four blocks, and that rage replaces the
 * first if it still runs. Each rage starts once. A hit that crosses both thresholds starts the second only.
 */
public final class WitherRage {
	private static final double FAR = 50.0;
	private static final double SINGLE_PITCH_DEGREES = 10.0;
	private static final int HEADS = 3;

	private WitherRage() {
	}

	/** Starts a rage when a hit takes the Wither under a threshold it has not crossed. */
	public static void afterDamage(final LivingEntity entity, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked) {
		if (blocked || damageTaken <= 0.0F || !(entity instanceof WitherBoss wither) || !WitherEncounter.isActive(wither) || !wither.isAlive()) {
			return;
		}
		WitherState state = WitherState.find(wither);
		WitherConfig config = WitherConfigStore.get();
		double fraction = wither.getHealth() / wither.getMaxHealth();
		boolean second = !state.rageBUsed() && fraction <= config.get(WitherConfig.RAGE_B_THRESHOLD);
		boolean first = !state.rageAUsed() && fraction <= config.get(WitherConfig.RAGE_A_THRESHOLD);
		if (!second && !first) {
			return;
		}
		int kind = second ? WitherState.RAGE_RAIN : WitherState.RAGE_BARRAGE;
		// A hit past both thresholds spends both, and starts the second.
		boolean aUsed = state.rageAUsed() || first || second;
		boolean bUsed = state.rageBUsed() || second;
		int ticks = config.getInt(WitherConfig.RAGE_TICKS);
		WitherState.set(wither, state.withRage(aUsed, bUsed, kind, ticks));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherRage START uuid={} kind={} fraction={} ticks={} replaced={}",
			wither.getUUID(), kind, fraction, ticks, state.rageKind());
	}

	/** One tick of an active rage. The elapsed ticks come from the saved countdown, so a reload resumes the pattern. */
	public static void tick(final ServerLevel level, final WitherBoss wither) {
		WitherState state = WitherState.find(wither);
		if (state == null || state.rageKind() == WitherState.RAGE_NONE) {
			return;
		}
		WitherConfig config = WitherConfigStore.get();
		int elapsed = Math.max(0, config.getInt(WitherConfig.RAGE_TICKS) - state.rageTicksLeft());
		RandomSource random = wither.getRandom();
		if (WitherShots.live(wither) < config.getInt(WitherConfig.SKULL_LIVE_CAP)) {
			if (state.rageKind() == WitherState.RAGE_BARRAGE) {
				barrage(wither, elapsed, random, config);
			} else {
				rain(level, wither, elapsed, random, config);
			}
		}
		int left = state.rageTicksLeft() - 1;
		if (left <= 0) {
			WitherState.set(wither, state.withRage(state.rageAUsed(), state.rageBUsed(), WitherState.RAGE_NONE, 0));
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherRage END uuid={} kind={}", wither.getUUID(), state.rageKind());
		} else {
			WitherState.set(wither, state.withRage(state.rageAUsed(), state.rageBUsed(), state.rageKind(), left));
		}
	}

	/** Ticks every active Wither of the level that is in a rage. */
	public static void tickAll(final ServerLevel level) {
		for (WitherBoss wither : WitherMinions.watched(level)) {
			if (wither.isAlive() && WitherEncounter.isActive(wither)) {
				tick(level, wither);
			}
		}
	}

	private static WitherSkullShot.Spec barrageSpec(final WitherConfig config) {
		return new WitherSkullShot.Spec(
			config.get(WitherConfig.RAGE_A_SHARE), config.getInt(WitherConfig.RAGE_A_WITHER_TICKS), config.getInt(WitherConfig.RAGE_A_WITHER_AMP),
			config.get(WitherConfig.RAGE_A_RANGE), config.getInt(WitherConfig.SKULL_LIFE_TICKS), true
		);
	}

	private static void barrage(final WitherBoss wither, final int elapsed, final RandomSource random, final WitherConfig config) {
		WitherSkullShot.Spec spec = barrageSpec(config);
		if (elapsed % Math.max(1, config.getInt(WitherConfig.RAGE_A_RING_INTERVAL)) == 0) {
			int size = Math.max(1, config.getInt(WitherConfig.RAGE_A_RING_SIZE));
			double phase = random.nextDouble() * Math.PI * 2.0;
			for (int i = 0; i < size; i++) {
				double yaw = phase + i * Math.PI * 2.0 / size;
				shoot(wither, i % HEADS, new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw)), spec);
			}
		}
		if (elapsed % Math.max(1, config.getInt(WitherConfig.RAGE_A_SINGLE_INTERVAL)) == 0) {
			double yaw = random.nextDouble() * Math.PI * 2.0;
			double pitch = Math.toRadians((random.nextDouble() * 2.0 - 1.0) * SINGLE_PITCH_DEGREES);
			Vec3 direction = new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
			shoot(wither, random.nextInt(HEADS), direction, spec);
		}
	}

	private static void shoot(final WitherBoss wither, final int head, final Vec3 direction, final WitherSkullShot.Spec spec) {
		if (WitherShots.live(wither) >= WitherConfigStore.get().getInt(WitherConfig.SKULL_LIVE_CAP)) {
			return;
		}
		WitherShots.fire(wither, head, wither.getEyePosition().add(direction.scale(FAR)), spec);
	}

	private static void rain(final ServerLevel level, final WitherBoss wither, final int elapsed, final RandomSource random, final WitherConfig config) {
		if (elapsed % Math.max(1, config.getInt(WitherConfig.RAGE_B_INTERVAL)) != 0) {
			return;
		}
		double radius = config.get(WitherConfig.RAGE_B_RADIUS);
		Vec3 point = null;
		if (random.nextDouble() < config.get(WitherConfig.RAGE_B_PLAYER_BIAS)) {
			point = nearPlayer(level, wither, radius, config.get(WitherConfig.RAGE_B_NEAR_PLAYER), random);
		}
		if (point == null) {
			double distance = radius * Math.sqrt(random.nextDouble());
			double angle = random.nextDouble() * Math.PI * 2.0;
			point = new Vec3(wither.getX() + Math.cos(angle) * distance, 0.0, wither.getZ() + Math.sin(angle) * distance);
		}
		// A spot near a far player is pulled back inside the rain radius.
		double dx = point.x - wither.getX();
		double dz = point.z - wither.getZ();
		double away = Math.hypot(dx, dz);
		if (away > radius) {
			point = new Vec3(wither.getX() + dx * radius / away, 0.0, wither.getZ() + dz * radius / away);
		}
		double height = wither.getY() + config.get(WitherConfig.RAGE_B_HEIGHT);
		WitherSkullShot skull = new WitherSkullShot(level, wither, new Vec3(0.0, -1.0, 0.0));
		skull.configure(new WitherSkullShot.Spec(
			config.get(WitherConfig.RAGE_B_SHARE), config.getInt(WitherConfig.RAGE_B_WITHER_TICKS), config.getInt(WitherConfig.RAGE_B_WITHER_AMP),
			0.0, config.getInt(WitherConfig.RAGE_B_LIFE_TICKS), false
		), new Vec3(point.x, height, point.z));
		skull.setPos(point.x, height, point.z);
		if (level.addFreshEntity(skull)) {
			WitherShots.track(wither, skull);
		}
	}

	/** A random spot around a random player within reach, or null when none is. */
	private static Vec3 nearPlayer(final ServerLevel level, final WitherBoss wither, final double reach, final double spread, final RandomSource random) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.distanceToSqr(wither) <= reach * reach) {
				players.add(player);
			}
		}
		if (players.isEmpty()) {
			return null;
		}
		ServerPlayer chosen = players.get(random.nextInt(players.size()));
		double distance = spread * Math.sqrt(random.nextDouble());
		double angle = random.nextDouble() * Math.PI * 2.0;
		return new Vec3(chosen.getX() + Math.cos(angle) * distance, 0.0, chosen.getZ() + Math.sin(angle) * distance);
	}
}
