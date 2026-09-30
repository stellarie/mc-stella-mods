package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Fights with a sword and throws an ender pearl away from the mob when hurt, or once by tick 90. */
public final class PearlEscaper implements BotBrain {
	private static final int FIRST_THROW_TICK = 90;
	private static final float THROW_PITCH = -35.0F;
	private int thrown;
	private boolean holding;

	@Override
	public String name() {
		return "PearlEscaper";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		if (holding) {
			holding = false;
			input.slot = Hotbar.PEARLS;
			return;
		}

		boolean hurt = view.healthFraction() < 0.5F;
		if (thrown < 3 && (hurt && view.tick() % 60 == 0 || thrown == 0 && view.tick() >= FIRST_THROW_TICK)) {
			thrown++;
			holding = true;
			input.slot = Hotbar.PEARLS;
			input.use = true;
			var me = view.pos();
			var mob = view.mobFeet();
			input.look(Moves.yaw(mob, me), THROW_PITCH);
			return;
		}

		Moves.rush(view, input, true);
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
	}
}
