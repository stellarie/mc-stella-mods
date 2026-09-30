package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Sprints at the mob and swings each time the attack cooldown is full. */
public final class SwordSweeper implements BotBrain {
	@Override
	public String name() {
		return "SwordSweeper";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		Moves.rush(view, input, true);
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
	}
}
