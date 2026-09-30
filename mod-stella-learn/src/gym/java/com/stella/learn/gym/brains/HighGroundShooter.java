package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Shoots from height: it climbs the nearest ledge, or builds a pillar when there is none, then draws from there. */
public final class HighGroundShooter implements BotBrain {
	private static final int PILLAR_HEIGHT = 4;
	private static final double LEDGE_REACHED = 1.5;
	private final Pillar pillar = new Pillar();
	private final Fire fire = new Fire();

	@Override
	public String name() {
		return "HighGroundShooter";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.BOW;
		if (!view.mobAlive()) {
			return;
		}

		var ledges = view.arena().terrain().ledgeTops();
		if (!ledges.isEmpty()) {
			BlockPos origin = view.arena().origin();
			Vec3 goal = null;
			for (BlockPos top : ledges) {
				Vec3 at = center(origin, top);
				if (goal == null || view.pos().distanceToSqr(at) < view.pos().distanceToSqr(goal)) {
					goal = at;
				}
			}

			if (Math.hypot(goal.x - view.pos().x, goal.z - view.pos().z) > LEDGE_REACHED || view.pos().y < goal.y - 0.5) {
				input.look(view.yawTo(view.seen()), view.pitchTo(view.seen()));
				view.walkTo(input, goal, false, false);
				return;
			}
		} else if (pillar.tick(view, input, PILLAR_HEIGHT)) {
			input.look(view.yawTo(view.seen()), 90.0F);
			return;
		}

		fire.bow(view, input);
	}

	private static Vec3 center(final BlockPos origin, final BlockPos cell) {
		return new Vec3(origin.getX() + cell.getX() + 0.5, origin.getY() + cell.getY(), origin.getZ() + cell.getZ() + 0.5);
	}
}
