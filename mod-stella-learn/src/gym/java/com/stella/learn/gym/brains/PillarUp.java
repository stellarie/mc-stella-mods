package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Builds a pillar under itself, waits on top, and drops onto the mob with a sword when it comes near. */
public final class PillarUp implements BotBrain {
	private static final int HEIGHT = 4;
	private static final double DROP_RANGE = 7.0;
	private static final int PATIENCE_TICKS = 220;
	private final Pillar pillar = new Pillar();

	@Override
	public String name() {
		return "PillarUp";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		if (view.tick() < PATIENCE_TICKS && pillar.tick(view, input, HEIGHT)) {
			input.look(view.yawTo(view.seen()), 90.0F);
			return;
		}

		view.faceMob(input, view.horizontalDistance() < 4.0);
		boolean above = pillar.gained(view) >= 2;
		if (above && view.horizontalDistance() > DROP_RANGE && view.tick() < PATIENCE_TICKS) {
			return;
		}

		Moves.rush(view, input, false);
		input.attack = view.inReach() && view.attackStrength() >= 0.9F;
	}
}
