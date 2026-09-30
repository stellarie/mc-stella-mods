package com.stella.learn.gym.brains;

import com.stella.learn.gym.Arena;
import com.stella.learn.gym.Bot;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Movement pieces the brains share. Each fills the walk keys of the input and leaves the look to the caller. */
final class Moves {
	private Moves() {
	}

	/** Moves along the world direction (dx, dz) while the input keeps its own look. */
	static void along(final BotInput.Builder input, final double dx, final double dz, final boolean sprint) {
		double length = Math.hypot(dx, dz);
		if (length < 1.0E-6) {
			return;
		}

		float wanted = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		double relative = Math.toRadians(Mth.wrapDegrees(wanted - input.yaw));
		input.forward = (float) Math.cos(relative);
		input.strafe = (float) -Math.sin(relative);
		input.sprint = sprint && input.forward > 0.5F;
	}

	/** Steps away from the mob, bending toward the arena middle as a wall comes near. */
	static void retreat(final BotView view, final BotInput.Builder input, final boolean sprint) {
		Vec3 me = view.pos();
		Vec3 mob = view.seen();
		double ax = me.x - mob.x;
		double az = me.z - mob.z;
		double length = Math.max(Math.hypot(ax, az), 1.0E-6);
		ax /= length;
		az /= length;
		var origin = view.arena().origin();
		double cx = origin.getX() + Arena.OPEN / 2.0 - me.x;
		double cz = origin.getZ() + Arena.OPEN / 2.0 - me.z;
		double edge = Math.max(Math.abs(cx), Math.abs(cz)) / (Arena.OPEN / 2.0);
		double pull = Mth.clamp((edge - 0.55) * 3.0, 0.0, 1.5);
		double centerLength = Math.max(Math.hypot(cx, cz), 1.0E-6);
		along(input, ax + pull * cx / centerLength, az + pull * cz / centerLength, sprint);
	}

	/** Runs at the mob along the path, facing it. */
	static void rush(final BotView view, final BotInput.Builder input, final boolean sprint) {
		view.faceMob(input, view.horizontalDistance() < 4.0);
		view.walkTo(input, view.seen(), sprint, false);
	}

	static Vec3 flat(final Vec3 v) {
		return new Vec3(v.x, 0.0, v.z);
	}

	static float yaw(final Vec3 from, final Vec3 to) {
		return Bot.yawTo(from, to);
	}
}
