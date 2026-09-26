package com.stella.mobs.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.illager.Illusioner;

/**
 * M9: an Illusioner in a fight splits off its decoys, and splits again once they are all gone and the
 * rearm has passed. The rearm counts the goal's own ticks. No flags.
 */
public class IllusionerDecoyGoal extends Goal {
	private final Illusioner illusioner;
	private int rearm;

	public IllusionerDecoyGoal(final Illusioner illusioner) {
		this.illusioner = illusioner;
	}

	@Override
	public boolean canUse() {
		if (this.rearm > 0) {
			this.rearm -= 2;
			return false;
		}

		LivingEntity target = this.illusioner.getTarget();
		return target != null && target.isAlive() && !IllusionerKit.isDecoy(this.illusioner)
			&& IllusionerKit.liveDecoys(this.illusioner) == 0 && this.illusioner.level() instanceof ServerLevel;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		if (this.illusioner.level() instanceof ServerLevel level) {
			IllusionerKit.split(this.illusioner, level);
			this.rearm = IllusionerKit.DECOY_REARM_TICKS;
		}
	}
}
