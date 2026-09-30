package com.stella.learn.gym;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * What a bot's virtual keyboard and mouse hold for one tick. Yaw and pitch are absolute degrees. A true `attack` swings
 * on every tick it is held, so a brain sets it only when it wants a swing. A true `use` is one press, held until it
 * turns false, which releases the item. `dig` names a block to keep breaking; `place` names a block face to click once.
 */
public record BotInput(
	float forward, float strafe, boolean jump, boolean sprint, boolean sneak, float yaw, float pitch,
	boolean attack, boolean use, int slot, @Nullable BlockPos dig, @Nullable BlockPos placeAgainst, @Nullable Direction placeFace
) {
	public static final BotInput IDLE = new BotInput(0.0F, 0.0F, false, false, false, 0.0F, 0.0F, false, false, 0);

	public BotInput(
		final float forward, final float strafe, final boolean jump, final boolean sprint, final boolean sneak, final float yaw,
		final float pitch, final boolean attack, final boolean use, final int slot
	) {
		this(forward, strafe, jump, sprint, sneak, yaw, pitch, attack, use, slot, null, null, null);
	}

	public BotInput facing(final float newYaw, final float newPitch) {
		return new BotInput(forward, strafe, jump, sprint, sneak, newYaw, newPitch, attack, use, slot, dig, placeAgainst, placeFace);
	}

	public BotInput moving(final float newForward, final float newStrafe, final boolean newJump, final boolean newSprint) {
		return new BotInput(newForward, newStrafe, newJump, newSprint, sneak, yaw, pitch, attack, use, slot, dig, placeAgainst, placeFace);
	}

	public BotInput holding(final boolean newAttack, final boolean newUse, final int newSlot) {
		return new BotInput(forward, strafe, jump, sprint, sneak, yaw, pitch, newAttack, newUse, newSlot, dig, placeAgainst, placeFace);
	}

	/** A mutable copy of the input for a brain to fill in one tick. */
	public static final class Builder {
		public float forward;
		public float strafe;
		public boolean jump;
		public boolean sprint;
		public boolean sneak;
		public float yaw;
		public float pitch;
		public boolean attack;
		public boolean use;
		public int slot;
		public @Nullable BlockPos dig;
		public @Nullable BlockPos placeAgainst;
		public @Nullable Direction placeFace;

		/** Clears every key and sets the look, so a brain that does nothing keeps facing where it was. */
		public Builder clear(final float keepYaw, final float keepPitch, final int keepSlot) {
			forward = 0.0F;
			strafe = 0.0F;
			jump = false;
			sprint = false;
			sneak = false;
			yaw = keepYaw;
			pitch = keepPitch;
			attack = false;
			use = false;
			slot = keepSlot;
			dig = null;
			placeAgainst = null;
			placeFace = null;
			return this;
		}

		public Builder look(final float newYaw, final float newPitch) {
			yaw = newYaw;
			pitch = newPitch;
			return this;
		}

		public Builder walk(final float newForward, final float newStrafe) {
			forward = newForward;
			strafe = newStrafe;
			return this;
		}

		public Builder place(final BlockPos against, final Direction face) {
			placeAgainst = against;
			placeFace = face;
			return this;
		}

		public BotInput build() {
			return new BotInput(forward, strafe, jump, sprint, sneak, yaw, pitch, attack, use, slot, dig, placeAgainst, placeFace);
		}
	}
}
