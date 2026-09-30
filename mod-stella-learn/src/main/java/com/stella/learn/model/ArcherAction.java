package com.stella.learn.model;

/**
 * One decision. `forward` and `strafe` (right is positive) move the mob; `yaw` (right is positive) and `pitch` (down is
 * positive) are offsets from the vanilla skeleton's aim point (with its ballistic lift), 30 degrees at full scale. All four are in [-1, 1].
 */
public record ArcherAction(float forward, float strafe, float yaw, float pitch, boolean jump, Bow bow) {
	public enum Bow {
		IDLE,
		DRAW,
		FIRE,
		FIRE_VOLLEY
	}

	public static final ArcherAction STILL = new ArcherAction(0.0F, 0.0F, 0.0F, 0.0F, false, Bow.IDLE);
	/** What a source returns when it cannot produce an action; the goal treats it as a fault. */
	public static final ArcherAction NOT_A_NUMBER = new ArcherAction(Float.NaN, Float.NaN, Float.NaN, Float.NaN, false, Bow.IDLE);

	public boolean isFinite() {
		return Float.isFinite(this.forward) && Float.isFinite(this.strafe) && Float.isFinite(this.yaw) && Float.isFinite(this.pitch);
	}

	public int jumpIndex() {
		return this.jump ? 1 : 0;
	}
}
