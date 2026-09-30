package com.stella.learn;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Flagless: it never runs. The selector asks `canUse` every few ticks, and each ask re-reads the target's profile and sets the band. */
public final class LearnedBandGoal extends Goal {
	private final Mob mob;

	public LearnedBandGoal(final Mob mob) {
		this.mob = mob;
	}

	@Override
	public boolean canUse() {
		// No tick modulus here: the selector's own beat and the mob's tick count can share a factor and never meet.
		if (this.mob.getTarget() instanceof ServerPlayer player) {
			LearnedBand.apply(this.mob, player);
		} else {
			LearnedBand.restore(this.mob);
		}

		return false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
