package com.stella.learn.gym;

/**
 * What a bot's virtual keyboard and mouse hold for one tick. Yaw and pitch are absolute degrees. A true `attack` swings
 * on every tick it is held, so a brain sets it only when it wants a swing. A true `use` is one press, held until it
 * turns false, which releases the item.
 */
public record BotInput(
	float forward, float strafe, boolean jump, boolean sprint, boolean sneak, float yaw, float pitch,
	boolean attack, boolean use, int slot
) {
	public static final BotInput IDLE = new BotInput(0.0F, 0.0F, false, false, false, 0.0F, 0.0F, false, false, 0);

	public BotInput facing(final float newYaw, final float newPitch) {
		return new BotInput(forward, strafe, jump, sprint, sneak, newYaw, newPitch, attack, use, slot);
	}

	public BotInput moving(final float newForward, final float newStrafe, final boolean newJump, final boolean newSprint) {
		return new BotInput(newForward, newStrafe, newJump, newSprint, sneak, yaw, pitch, attack, use, slot);
	}

	public BotInput holding(final boolean newAttack, final boolean newUse, final int newSlot) {
		return new BotInput(forward, strafe, jump, sprint, sneak, yaw, pitch, newAttack, newUse, newSlot);
	}
}
