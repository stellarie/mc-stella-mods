package com.stella.mobs.behavior;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;

/**
 * W04 and the first clause of W06: below 30 percent of its own health the witch splashes a healing
 * potion on itself. One pass, no flags, so it never contends for MOVE or LOOK.
 */
public class WitchSelfHealGoal<T extends Witch> extends Goal {
	public static final double HEALTH_FRACTION = 0.30;
	public static final int REARM_TICKS = 200;
	private static final int ACTION_TICKS = 10;

	private final T witch;
	private long readyTick;

	public WitchSelfHealGoal(final T witch) {
		this.witch = witch;
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.witch);
		if (!state.playerInRange(this.witch)) {
			return false;
		}

		long now = this.witch.level().getGameTime();
		if (now < state.getWitchActionUntilTick() || now < this.readyTick) {
			return false;
		}

		if (this.witch.getHealth() > this.witch.getMaxHealth() * HEALTH_FRACTION) {
			return false;
		}

		return true;
	}

	@Override
	public void start() {
		ReactiveState state = ReactiveState.get(this.witch);
		long now = this.witch.level().getGameTime();
		state.setWitchActionUntilTick(now + ACTION_TICKS);
		this.readyTick = now + REARM_TICKS;
		WitchThrow.atSelf(this.witch, WitchPotions.stack(WitchPotions.healing()));
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}
}
