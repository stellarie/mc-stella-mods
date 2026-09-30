package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** Jumps and places a block under itself at the top of the jump, again and again. */
final class Pillar {
	private int cell = Integer.MIN_VALUE;
	private int startY = Integer.MIN_VALUE;
	private int placed;
	private boolean finished;

	/** The height gained so far, in blocks. */
	int gained(final BotView view) {
		return startY == Integer.MIN_VALUE ? 0 : Mth.floor(view.pos().y + 1.0E-3) - startY;
	}

	int placed() {
		return placed;
	}

	/** Returns true while more blocks are needed to reach `height` blocks above the start. */
	boolean tick(final BotView view, final BotInput.Builder input, final int height) {
		double y = view.pos().y;
		if (startY == Integer.MIN_VALUE) {
			startY = Mth.floor(y + 1.0E-3);
		}

		if (finished || gained(view) >= height) {
			finished = true;
			return false;
		}

		input.slot = Hotbar.BLOCKS;
		input.pitch = 90.0F;
		if (view.onGround()) {
			cell = Mth.floor(y + 1.0E-3);
			input.jump = true;
			return true;
		}

		if (cell != Integer.MIN_VALUE && y >= cell + 1.0 + 0.05) {
			BlockPos here = BlockPos.containing(view.pos().x, cell, view.pos().z);
			if (view.player().level().getBlockState(here).isAir()) {
				input.place(here.below(), Direction.UP);
				placed++;
			}
		}

		return true;
	}
}
