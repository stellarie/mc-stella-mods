package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Holds a long range and looses a crossbow bolt every charge, backing off when the mob closes in. */
public final class CrossbowSniper implements BotBrain {
	private static final double TOO_CLOSE = 10.0;
	private final Fire fire = new Fire();

	@Override
	public String name() {
		return "CrossbowSniper";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.CROSSBOW;
		if (!view.mobAlive()) {
			return;
		}

		fire.crossbow(view, input);
		if (view.horizontalDistance() < TOO_CLOSE) {
			Moves.retreat(view, input, false);
		}
	}
}
