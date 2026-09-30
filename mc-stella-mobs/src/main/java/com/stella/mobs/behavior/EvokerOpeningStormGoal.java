package com.stella.mobs.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.illager.Evoker;

/**
 * M8: an elite evoker opens every fight with its storm, the first time it has a live target, with no cast
 * animation or charge. It spends the storm's durable flag, so it fires once per life. No flags, so it
 * never contends for MOVE or LOOK and the vanilla spells still run beside it.
 */
public class EvokerOpeningStormGoal extends Goal {
	private final Evoker evoker;

	public EvokerOpeningStormGoal(final Evoker evoker) {
		this.evoker = evoker;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.evoker.getTarget();
		return target != null
			&& target.isAlive()
			&& Elite.isElite(this.evoker)
			&& !EliteState.isStormCast(this.evoker)
			&& this.evoker.level() instanceof ServerLevel;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		if (this.evoker.level() instanceof ServerLevel level) {
			EvokerStorm.begin(this.evoker, level, EvokerStorm.REASON_OPENING);
		}
	}
}
