package com.stella.mobs.behavior;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.illager.Illusioner;

/**
 * M9: the Illusioner's aura, one pulse every interval on the goal's own tick counter, frost and fire in turn. No flags, so the
 * vanilla spells and the bow still run beside it. A decoy carries no aura.
 */
public class IllusionerAuraGoal extends Goal {
	private final Illusioner illusioner;
	private int ticksToPulse;
	private boolean frost = true;

	public IllusionerAuraGoal(final Illusioner illusioner) {
		this.illusioner = illusioner;
	}

	@Override
	public boolean canUse() {
		return !IllusionerKit.isDecoy(this.illusioner) && ReactiveState.get(this.illusioner).playerInRange(this.illusioner);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (--this.ticksToPulse > 0) {
			return;
		}

		this.ticksToPulse = IllusionerKit.AURA_INTERVAL_TICKS;
		IllusionerKit.pulse(this.illusioner, this.frost);
		this.frost = !this.frost;
	}
}
