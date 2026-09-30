package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** The first target: stands where it spawned, turns to face the archer, and never attacks or shoots. */
public final class Static implements BotBrain {
	@Override
	public String name() {
		return "Static";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (view.mobAlive()) {
			view.faceMob(input, false);
		}
	}
}
