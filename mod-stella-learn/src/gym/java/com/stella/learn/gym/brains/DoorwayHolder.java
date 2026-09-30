package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Holds a doorway with a sword, and stands its ground where it starts when the arena has none. */
public final class DoorwayHolder implements BotBrain {
	private static final double ARRIVED = 0.6;

	@Override
	public String name() {
		return "DoorwayHolder";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		view.faceMob(input, view.horizontalDistance() < 4.0);
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
		var doors = view.arena().terrain().doorways();
		if (doors.isEmpty()) {
			return;
		}

		BlockPos origin = view.arena().origin();
		Vec3 best = null;
		for (BlockPos door : doors) {
			Vec3 at = new Vec3(origin.getX() + door.getX() + 0.5, origin.getY() + door.getY(), origin.getZ() + door.getZ() + 0.5);
			if (best == null || at.distanceToSqr(view.pos()) < best.distanceToSqr(view.pos())) {
				best = at;
			}
		}

		if (Math.hypot(best.x - view.pos().x, best.z - view.pos().z) > ARRIVED) {
			view.walkTo(input, best, false, false);
		}
	}
}
