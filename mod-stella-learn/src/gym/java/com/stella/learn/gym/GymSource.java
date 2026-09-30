package com.stella.learn.gym;

import com.stella.learn.model.ActionSource;
import com.stella.learn.model.ArcherAction;
import com.stella.learn.model.Contract;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * The model of one gym archer. The goal calls it each decision with the observation and gets the latest action the
 * trainer sent; the trainer's answer to this observation lands in the next call. The action size on the wire is the four
 * continuous values, then the jump index and the bow index as floats.
 */
final class GymSource implements ActionSource {
	static final int ACTION_SIZE = 6;
	private final float[] observation = new float[Contract.archer().observationSize()];
	private volatile ArcherAction pending = ArcherAction.STILL;
	private boolean captured;
	private long decisions;

	@Override
	public ArcherAction decide(final Mob mob, final float[] scaled) {
		System.arraycopy(scaled, 0, observation, 0, observation.length);
		captured = true;
		decisions++;
		return pending;
	}

	boolean captured() {
		return captured;
	}

	long decisions() {
		return decisions;
	}

	/** The observation of the latest decision. The caller copies it into a frame at once. */
	float[] observation() {
		return observation;
	}

	void set(final float[] action) {
		int bow = Mth.clamp(Math.round(action[5]), 0, ArcherAction.Bow.values().length - 1);
		pending = new ArcherAction(unit(action[0]), unit(action[1]), unit(action[2]), unit(action[3]), action[4] > 0.5F, ArcherAction.Bow.values()[bow]);
	}

	private static float unit(final float value) {
		return Float.isFinite(value) ? Mth.clamp(value, -1.0F, 1.0F) : 0.0F;
	}
}
