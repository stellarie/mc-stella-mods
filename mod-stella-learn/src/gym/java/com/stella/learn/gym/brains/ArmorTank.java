package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;

/** Puts on a full set of diamond armor whatever its tier, then walks straight in and swings. */
public final class ArmorTank implements BotBrain {
	private boolean dressed;

	@Override
	public String name() {
		return "ArmorTank";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		if (!dressed) {
			com.stella.learn.gym.BotLoadout.wearArmor(view.bot(), com.stella.learn.gym.Gear.DIAMOND);
			dressed = true;
		}

		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		Moves.rush(view, input, true);
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
	}
}
