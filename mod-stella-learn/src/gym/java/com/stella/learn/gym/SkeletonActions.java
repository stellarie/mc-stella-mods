package com.stella.learn.gym;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * The speed run action set: forward, strafe, jump, and a yaw delta, applied to the mob. Move controls forget their
 * input each tick, so the last action is set again every tick until the next decision replaces it.
 */
public final class SkeletonActions {
	public static final int SIZE = 4;
	private static final float MAX_YAW_STEP = 30.0F;

	private final float[] held = new float[SIZE];
	private boolean yawPending;

	/** Replaces the held action. The yaw delta turns the mob once, on the next tick. */
	public void set(final float[] action) {
		for (int i = 0; i < SIZE; i++) {
			held[i] = Float.isFinite(action[i]) ? Mth.clamp(action[i], -1.0F, 1.0F) : 0.0F;
		}

		yawPending = true;
	}

	public float[] held() {
		return held;
	}

	public void tick(final Mob mob) {
		if (mob == null || !mob.isAlive()) {
			return;
		}

		if (yawPending) {
			float yaw = mob.getYRot() + held[3] * MAX_YAW_STEP;
			mob.setYRot(yaw);
			mob.setYHeadRot(yaw);
			yawPending = false;
		}

		mob.getMoveControl().strafe(held[0], held[1]);
		if (held[2] > 0.5F) {
			mob.getJumpControl().jump();
		}
	}
}
