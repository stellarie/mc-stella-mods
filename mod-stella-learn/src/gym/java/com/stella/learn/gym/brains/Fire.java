package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.world.phys.Vec3;

/** The bow and crossbow cycles the ranged brains share. */
final class Fire {
	static final int BOW_DRAW_TICKS = 22;
	static final int CROSSBOW_CHARGE_TICKS = 27;
	private static final double ARROW_SPEED = 2.6;
	private int step;

	/** Draws and looses a bow at the mob. Aim and use are set; the caller sets the walk keys. */
	void bow(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.BOW;
		aim(view, input);
		input.use = step < BOW_DRAW_TICKS;
		step = step + 1 > BOW_DRAW_TICKS ? 0 : step + 1;
	}

	/** Charges a crossbow, then presses again to fire it. */
	void crossbow(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.CROSSBOW;
		aim(view, input);
		input.use = step < CROSSBOW_CHARGE_TICKS || step == CROSSBOW_CHARGE_TICKS + 1;
		step = step + 1 > CROSSBOW_CHARGE_TICKS + 3 ? 0 : step + 1;
	}

	private static void aim(final BotView view, final BotInput.Builder input) {
		Vec3 at = view.leadPoint(ARROW_SPEED);
		input.look(view.yawTo(at), view.pitchTo(at));
	}
}
