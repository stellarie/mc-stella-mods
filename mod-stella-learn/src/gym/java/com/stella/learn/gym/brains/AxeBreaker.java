package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Swaps the sword for an axe and rushes. An axe hit takes a raised shield down, so it is a bot's answer to a turtle. */
public final class AxeBreaker implements BotBrain {
	private boolean armed;

	@Override
	public String name() {
		return "AxeBreaker";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		if (!armed) {
			view.swapMelee(true);
			armed = true;
		}

		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		Moves.rush(view, input, true);
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
	}
}
