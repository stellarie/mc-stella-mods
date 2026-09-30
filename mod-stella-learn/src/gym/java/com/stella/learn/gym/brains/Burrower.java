package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.core.BlockPos;

/** Digs a one-block hole two deep, then shoots the mob from the bottom of it. */
public final class Burrower implements BotBrain {
	private static final int DEPTH = 2;
	private final Fire fire = new Fire();
	private int startY = Integer.MIN_VALUE;

	@Override
	public String name() {
		return "Burrower";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		if (!view.mobAlive()) {
			return;
		}

		BlockPos feet = BlockPos.containing(view.pos().x, view.pos().y + 1.0E-3, view.pos().z);
		if (startY == Integer.MIN_VALUE) {
			startY = feet.getY();
		}

		if (startY - feet.getY() < DEPTH) {
			input.slot = Hotbar.SHOVEL;
			input.pitch = 90.0F;
			input.dig = feet.below();
			return;
		}

		fire.bow(view, input);
	}
}
