package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Runs at the mob, jumps as it comes close, and swings while falling for the critical hit. */
public final class CritRusher implements BotBrain {
	private static final double JUMP_RANGE = 4.2;

	@Override
	public String name() {
		return "CritRusher";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		boolean near = view.horizontalDistance() < JUMP_RANGE;
		Moves.rush(view, input, !near);
		if (near && view.onGround() && view.attackStrength() >= 0.9F) {
			input.jump = true;
		}

		if (!view.onGround() && view.player().fallDistance > 0.0 && view.inReach() && view.attackStrength() >= 0.9F) {
			input.attack = true;
		}
	}
}
