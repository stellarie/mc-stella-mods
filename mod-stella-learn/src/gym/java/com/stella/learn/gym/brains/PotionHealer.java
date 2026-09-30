package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Fights like a sword bot and drinks a healing potion when hurt, or once by tick 100 to be ready. */
public final class PotionHealer implements BotBrain {
	private static final int DRINK_TICKS = 36;
	private static final int FIRST_DRINK_TICK = 100;
	private int drinking;
	private int drunk;

	@Override
	public String name() {
		return "PotionHealer";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		boolean hurt = view.healthFraction() < 0.6F;
		if (drinking == 0 && drunk < 3 && (hurt && view.tick() % 40 == 0 || drunk == 0 && view.tick() >= FIRST_DRINK_TICK)) {
			drinking = DRINK_TICKS;
			drunk++;
		}

		if (drinking > 0) {
			drinking--;
			input.slot = Hotbar.POTIONS;
			input.use = true;
			view.faceMob(input, false);
			Moves.retreat(view, input, false);
			return;
		}

		Moves.rush(view, input, true);
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
	}
}
