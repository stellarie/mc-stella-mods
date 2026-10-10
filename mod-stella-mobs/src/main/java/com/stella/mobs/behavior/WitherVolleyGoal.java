package com.stella.mobs.behavior;

import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/** An active encounter Wither looses one aimed skull at its target, faster as its health falls. */
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
		return new WitherSkullShot.Spec(
			config.get(WitherConfig.AIM_SHARE), config.getInt(WitherConfig.AIM_WITHER_TICKS), config.getInt(WitherConfig.AIM_WITHER_AMP),
			0.0, config.getInt(WitherConfig.SKULL_LIFE_TICKS), false
		);
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.wither.getTarget();
		return target != null && target.isAlive() && WitherEncounter.isActive(this.wither) && !WitherEncounter.isRaging(this.wither);
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
		WitherShots.fire(this.wither, this.head, aim, aimedSpec(config));
		this.head = (this.head + 1) % HEADS;
		double fraction = this.wither.getHealth() / this.wither.getMaxHealth();
		this.nextFireTick = this.wither.tickCount + interval(fraction, config);
	}
}
