package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Circles the mob at sword range, strafing one way for a stretch, then the other, and swings when the cooldown fills. */
public final class StrafeDuelist implements BotBrain {
	private static final double RANGE = 3.4;
	private int side = 1;
	private int untilFlip;

	@Override
	public String name() {
		return "StrafeDuelist";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		if (untilFlip-- <= 0) {
			side = view.tick() == 1 ? (view.random().nextBoolean() ? 1 : -1) : -side;
			untilFlip = 40 + view.random().nextInt(50);
		}

		view.faceMob(input, view.horizontalDistance() < 4.0);
		double distance = view.horizontalDistance();
		input.strafe = side * 0.9F;
		input.forward = distance > RANGE + 0.6 ? 0.9F : distance < RANGE - 0.6 ? -0.6F : 0.0F;
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
	}
}
