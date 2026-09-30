package com.stella.learn.model;

import net.minecraft.world.entity.Mob;

/**
 * The network as an action source. The continuous outputs go through tanh; the jump and bow heads take the arg max, and
 * a tie picks the first. Any output that is not finite makes the whole action not finite, so the goal falls back.
 */
public final class MlpSource implements ActionSource {
	private static final int JUMP_AT = Contract.CONTINUOUS.size();
	private static final int BOW_AT = JUMP_AT + Contract.JUMP_CHOICES;
	private static final ArcherAction.Bow[] BOWS = ArcherAction.Bow.values();
	private final Mlp mlp;
	private final float[] raw;

	public MlpSource(final Mlp mlp) {
		this.mlp = mlp;
		this.raw = new float[mlp.outputSize()];
	}

	@Override
	public ArcherAction decide(final Mob mob, final float[] observation) {
		this.mlp.forward(observation, this.raw);
		for (float value : this.raw) {
			if (!Float.isFinite(value)) {
				return ArcherAction.NOT_A_NUMBER;
			}
		}

		int bow = BOW_AT;
		for (int i = BOW_AT + 1; i < BOW_AT + Contract.BOW_CHOICES; i++) {
			if (this.raw[i] > this.raw[bow]) {
				bow = i;
			}
		}

		return new ArcherAction(
			(float) Math.tanh(this.raw[0]), (float) Math.tanh(this.raw[1]), (float) Math.tanh(this.raw[2]), (float) Math.tanh(this.raw[3]),
			this.raw[JUMP_AT + 1] > this.raw[JUMP_AT], BOWS[bow - BOW_AT]
		);
	}
}
