package com.stella.mobs.behavior;

import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * An active encounter Wither flies at its target and holds at the stop distance when it can see it. It aims low: the
 * wanted height never passes the ceiling, so the vanilla lift toward a high target has nothing to follow.
 */
public final class WitherPursueGoal extends Goal {
	private static final double BRAKE = 0.5;
	private final WitherBoss wither;

	public WitherPursueGoal(final WitherBoss wither) {
		this.wither = wither;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.wither.getTarget();
		return target != null && target.isAlive() && WitherEncounter.isActive(this.wither) && this.wither.getInvulnerableTicks() <= 0;
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
	public void tick() {
		LivingEntity target = this.wither.getTarget();
		if (target == null) {
			return;
		}
		this.wither.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double stop = WitherConfigStore.get().get(WitherConfig.MOVE_STOP_DISTANCE);
		double dx = target.getX() - this.wither.getX();
		double dz = target.getZ() - this.wither.getZ();
		if (dx * dx + dz * dz <= stop * stop && this.wither.hasLineOfSight(target)) {
			this.wither.getMoveControl().setWantedPosition(this.wither.getX(), this.wither.getY(), this.wither.getZ(), 1.0);
			// A flier coasts; braking keeps it near the stop distance.
			this.wither.setDeltaMovement(this.wither.getDeltaMovement().multiply(BRAKE, 1.0, BRAKE));
			return;
		}
		double wantedY = Math.min(target.getEyeY(), WitherEncounter.ceilingFor(this.wither));
		this.wither.getMoveControl().setWantedPosition(target.getX(), wantedY, target.getZ(), 1.0);
	}
}
