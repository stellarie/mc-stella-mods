package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Walks up to the mob and puts blocks on the floor cells around it to box it in, then swings at it over the wall. */
public final class BlockTrapper implements BotBrain {
	private static final double PLACE_RANGE = 3.8;
	private static final Direction[] RING = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
	private int next;

	@Override
	public String name() {
		return "BlockTrapper";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		input.slot = Hotbar.MELEE;
		if (!view.mobAlive()) {
			return;
		}

		view.faceMob(input, view.horizontalDistance() < 4.0);
		if (next < RING.length && view.horizontalDistance() > PLACE_RANGE) {
			view.walkTo(input, view.seen(), false, false);
			return;
		}

		if (next < RING.length) {
			input.slot = Hotbar.BLOCKS;
			if (view.tick() % 3 == 0) {
				BlockPos mobCell = BlockPos.containing(view.mobFeet().x, view.mobFeet().y + 1.0E-3, view.mobFeet().z);
				BlockPos cell = mobCell.relative(RING[next]);
				var world = view.player().level();
				if (world.getBlockState(cell).isAir() && !world.getBlockState(cell.below()).isAir()) {
					input.place(cell.below(), Direction.UP);
				}

				next++;
			}

			return;
		}

		Moves.rush(view, input, false);
		input.attack = view.inReach() && view.attackStrength() >= 1.0F;
	}
}
