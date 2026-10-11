package com.stella.mobs.behavior;

import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * An active encounter Wither looses one aimed skull at its target, and with it a few skulls in random directions,
 * faster as its health falls. A small wither runs the same goal at a share of the damage.
 */
public final class WitherVolleyGoal extends Goal {
	private static final int HEADS = 3;
	private final WitherBoss wither;
	private int nextFireTick = -1;
	private int head;

	public WitherVolleyGoal(final WitherBoss wither) {
		this.wither = wither;
	}

	/** Ticks between volleys: the slow end at full health, the fast end at none. */
	public static int interval(final double healthFraction, final WitherConfig config) {
		double slow = config.get(WitherConfig.AIM_INTERVAL_MAX);
		double fast = config.get(WitherConfig.AIM_INTERVAL_MIN);
		return (int) Math.round(fast + (slow - fast) * Mth.clamp(healthFraction, 0.0, 1.0));
	}

	public static WitherSkullShot.Spec aimedSpec(final WitherConfig config) {
		return aimedSpec(config, 1.0);
	}

	/** The aimed skull with its damage share scaled once by `factor`. */
	public static WitherSkullShot.Spec aimedSpec(final WitherConfig config, final double factor) {
		return new WitherSkullShot.Spec(
			config.get(WitherConfig.AIM_SHARE) * factor, config.getInt(WitherConfig.AIM_WITHER_TICKS), config.getInt(WitherConfig.AIM_WITHER_AMP),
			0.0, config.getInt(WitherConfig.SKULL_LIFE_TICKS), false
		);
	}

	/** A random skull hits like the aimed one and burns out sooner, so a miss does not hold a place under the cap. */
	public static WitherSkullShot.Spec randomSpec(final WitherConfig config, final double factor) {
		WitherSkullShot.Spec aimed = aimedSpec(config, factor);
		return new WitherSkullShot.Spec(
			aimed.share(), aimed.witherTicks(), aimed.witherAmplifier(), config.get(WitherConfig.AIM_RANDOM_RANGE), aimed.lifeTicks(), false
		);
	}

	private static double damageFactor(final WitherBoss wither, final WitherConfig config) {
		WitherState state = WitherState.find(wither);
		return state != null && state.small() ? config.get(WitherConfig.SMALL_DAMAGE_FACTOR) : 1.0;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.wither.getTarget();
		return target != null && target.isAlive() && WitherEncounter.isActive(this.wither);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		if (this.nextFireTick < 0) {
			this.nextFireTick = this.wither.tickCount;
		}
	}

	@Override
	public void tick() {
		LivingEntity target = this.wither.getTarget();
		WitherConfig config = WitherConfigStore.get();
		if (target == null || this.wither.tickCount < this.nextFireTick) {
			return;
		}
		if (WitherShots.live(this.wither) >= config.getInt(WitherConfig.SKULL_LIVE_CAP)) {
			this.nextFireTick = this.wither.tickCount + 1;
			return;
		}
		Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
		double factor = damageFactor(this.wither, config);
		WitherShots.fire(this.wither, this.head, aim, aimedSpec(config, factor));
		this.head = (this.head + 1) % HEADS;
		RandomSource random = this.wither.getRandom();
		WitherSkullShot.Spec spec = randomSpec(config, factor);
		for (int i = 0; i < config.getInt(WitherConfig.AIM_RANDOM_SKULLS); i++) {
			WitherShots.fireAlong(this.wither, random.nextInt(HEADS), WitherShots.randomDirection(random), spec);
		}
		double fraction = this.wither.getHealth() / this.wither.getMaxHealth();
		this.nextFireTick = this.wither.tickCount + interval(fraction, config);
	}
}
