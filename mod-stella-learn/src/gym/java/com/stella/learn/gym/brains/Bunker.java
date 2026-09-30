package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;

/** Digs down three blocks, seals the hole with a block above its head, waits, then breaks out and swings. */
public final class Bunker implements BotBrain {
	private static final int DEPTH = 3;
	private static final int WAIT_TICKS = 50;
	private int startY = Integer.MIN_VALUE;
	private long sealedAt = -1;
	private BlockPos roof;

	@Override
	public String name() {
		return "Bunker";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		var world = view.player().level();
		BlockPos feet = BlockPos.containing(view.pos().x, view.pos().y + 1.0E-3, view.pos().z);
		if (startY == Integer.MIN_VALUE) {
			startY = feet.getY();
		}

		if (sealedAt < 0 && startY - feet.getY() < DEPTH) {
			BlockPos below = feet.below();
			input.slot = world.getBlockState(below).is(BlockTags.MINEABLE_WITH_SHOVEL) ? Hotbar.SHOVEL : Hotbar.PICKAXE;
			input.pitch = 90.0F;
			input.dig = below;
			return;
		}

		if (sealedAt < 0) {
			// A bot still falling into the last block has its head in the roof cell, and the block cannot go there.
			if (!view.onGround()) {
				return;
			}

			roof = feet.above(2);
			if (world.getBlockState(roof).isAir()) {
				input.slot = Hotbar.BLOCKS;
				input.pitch = -90.0F;
				input.place(roof.east(), Direction.WEST);
				sealedAt = view.tick();
			}

			return;
		}

		if (view.tick() - sealedAt < WAIT_TICKS) {
			return;
		}

		if (world.getBlockState(roof).isAir()) {
			view.faceMob(input, false);
			Moves.rush(view, input, false);
			input.jump |= view.onGround();
			input.attack = view.inReach() && view.attackStrength() >= 1.0F;
			return;
		}

		input.slot = Hotbar.PICKAXE;
		input.pitch = -90.0F;
		input.dig = roof;
	}
}
