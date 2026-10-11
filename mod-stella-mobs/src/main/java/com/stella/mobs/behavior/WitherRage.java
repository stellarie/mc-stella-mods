package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * An encounter Wither widens its attack twice. While it stands under half health it looses a sphere of skulls on a
 * fixed beat, on top of its volleys. Under a quarter health it summons its small withers, once. A small wither does
 * neither. Both phases read the health fraction each tick, so a hit and a heal act the same way.
 */
public final class WitherRage {
	private static final int HEADS = 3;
	private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

	private WitherRage() {
	}

	/** One tick of an active parent Wither: the sphere beat, then the summon check. */
	public static void tick(final ServerLevel level, final WitherBoss wither) {
		WitherState state = WitherState.find(wither);
		if (state == null || state.small()) {
			return;
		}
		WitherConfig config = WitherConfigStore.get();
		double fraction = wither.getHealth() / wither.getMaxHealth();
		if (fraction <= config.get(WitherConfig.SPHERE_THRESHOLD)) {
			sphere(wither, state, config, fraction);
		}
		if (fraction <= config.get(WitherConfig.SMALL_THRESHOLD) && !WitherState.find(wither).rageBUsed()) {
			WitherSpawnlings.summon(level, wither);
		}
	}

	/** Ticks every active Wither of the level: a parent runs its phases, a small wither checks that its parent stands. */
	public static void tickAll(final ServerLevel level) {
		for (WitherBoss wither : WitherMinions.watched(level)) {
			if (!wither.isAlive() || !WitherEncounter.isActive(wither)) {
				continue;
			}
			if (WitherSpawnlings.isSmall(wither)) {
				WitherSpawnlings.tick(level, wither);
			} else {
				tick(level, wither);
			}
		}
	}

	/** The skulls of one burst leave the Wither's head in every direction, as far as the sphere range. */
	public static WitherSkullShot.Spec sphereSpec(final WitherConfig config) {
		return new WitherSkullShot.Spec(
			config.get(WitherConfig.SPHERE_SHARE), config.getInt(WitherConfig.SPHERE_WITHER_TICKS), config.getInt(WitherConfig.SPHERE_WITHER_AMP),
			config.get(WitherConfig.SPHERE_RANGE), config.getInt(WitherConfig.SKULL_LIFE_TICKS), false
		);
	}

	/** The `index`th of `count` directions spread evenly over the sphere, turned by `phase` around the vertical. */
	public static Vec3 sphereDirection(final int index, final int count, final double phase) {
		double height = 1.0 - 2.0 * (index + 0.5) / count;
		double ring = Math.sqrt(1.0 - height * height);
		double angle = phase + index * GOLDEN_ANGLE;
		return new Vec3(Math.cos(angle) * ring, height, Math.sin(angle) * ring);
	}

	private static void sphere(final WitherBoss wither, final WitherState state, final WitherConfig config, final double fraction) {
		if (state.sphereTicks() > 0) {
			WitherState.set(wither, state.withSphere(state.rageAUsed(), state.sphereTicks() - 1));
			return;
		}
		RandomSource random = wither.getRandom();
		WitherSkullShot.Spec spec = sphereSpec(config);
		int count = Math.max(1, config.getInt(WitherConfig.SPHERE_COUNT));
		double phase = random.nextDouble() * Math.PI * 2.0;
		int fired = 0;
		for (int i = 0; i < count; i++) {
			if (WitherShots.fireAlong(wither, i % HEADS, sphereDirection(i, count, phase), spec) != null) {
				fired++;
			}
		}
		// The beat counts this tick, so the next burst comes `interval` ticks after this one.
		WitherState.set(wither, state.withSphere(true, Math.max(0, config.getInt(WitherConfig.SPHERE_INTERVAL) - 1)));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSphere BURST uuid={} fraction={} fired={} of={} live={}",
			wither.getUUID(), fraction, fired, count, WitherShots.live(wither));
	}
}
