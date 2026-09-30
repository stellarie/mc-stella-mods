package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Keeps its distance and shoots: backs off when the mob is close, closes in when it is far, and draws in between. */
public final class BowKiter implements BotBrain {
	private static final double TOO_CLOSE = 8.0;
	private static final double TOO_FAR = 15.0;
	private final Fire fire = new Fire();

	@Override
	public String name() {
		return "BowKiter";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.BOW;
		if (!view.mobAlive()) {
			return;
		}

		fire.bow(view, input);
		double distance = view.horizontalDistance();
		if (distance < TOO_CLOSE) {
			Moves.retreat(view, input, false);
		} else if (distance > TOO_FAR) {
			view.walkTo(input, view.seen(), false, false);
		}
	}
}
