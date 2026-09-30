package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Keeps a shield up while the mob draws or an arrow flies, and creeps in behind it. */
public final class ShieldTurtle implements BotBrain {
	private static final double SHIELD_RANGE = 5.0;
	private boolean armed;

	@Override
	public String name() {
		return "ShieldTurtle";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		if (!armed) {
			com.stella.learn.gym.BotLoadout.giveShield(view.bot());
			armed = true;
		}

		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		view.faceMob(input, view.horizontalDistance() < 4.0);
		boolean threat = view.mobDrawing() || view.arrowThreat() >= 0.0 || view.horizontalDistance() > SHIELD_RANGE;
		input.use = threat || view.tick() < 30;
		view.walkTo(input, view.seen(), false, false);
		input.forward *= view.horizontalDistance() > 2.5 ? 0.6F : 0.0F;
		input.strafe *= 0.6F;
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
	}
}
