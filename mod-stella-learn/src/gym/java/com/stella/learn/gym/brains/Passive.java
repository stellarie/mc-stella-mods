package com.stella.learn.gym.brains;

import com.stella.learn.gym.Arena;
import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.world.phys.Vec3;

/** Target practice: walks between random points and never attacks or shoots, so the archer has to lead a moving mark. */
public final class Passive implements BotBrain {
	private static final int LEG_TICKS = 40;
	private static final int MARGIN = 3;
	private static final double ARRIVED = 1.0;
	private Vec3 goal;
	private long legEnds;

	@Override
	public String name() {
		return "Passive";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		Vec3 origin = Vec3.atLowerCornerOf(view.arena().origin());
		if (goal == null || view.tick() >= legEnds || Math.hypot(goal.x - view.pos().x, goal.z - view.pos().z) < ARRIVED) {
			double span = Arena.OPEN - 2.0 * MARGIN;
			goal = new Vec3(origin.x + MARGIN + view.random().nextDouble() * span, view.pos().y, origin.z + MARGIN + view.random().nextDouble() * span);
			legEnds = view.tick() + LEG_TICKS;
		}

		view.walkTo(input, goal, false, true);
	}
}
