package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import com.stella.learn.gym.Hotbar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;

/** Digs one block down, then tunnels toward the mob at foot level, and swings when it comes close. */
public final class Tunneler implements BotBrain {
	private static final double SWING_RANGE = 3.0;
	private int startY = Integer.MIN_VALUE;

	@Override
	public String name() {
		return "Tunneler";
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

		view.faceMob(input, view.horizontalDistance() < 4.0);
		if (feet.getY() == startY) {
			input.slot = Hotbar.SHOVEL;
			input.pitch = 90.0F;
			input.dig = feet.below();
			return;
		}

		if (view.horizontalDistance() < SWING_RANGE) {
			input.attack = view.inReach() && view.attackStrength() >= 1.0F;
			Moves.rush(view, input, false);
			return;
		}

		var toMob = view.seen().subtract(view.pos());
		Direction heading = Math.abs(toMob.x) > Math.abs(toMob.z)
			? (toMob.x > 0 ? Direction.EAST : Direction.WEST)
			: (toMob.z > 0 ? Direction.SOUTH : Direction.NORTH);
		BlockPos ahead = feet.relative(heading);
		if (!world.getBlockState(ahead).isAir()) {
			input.slot = world.getBlockState(ahead).is(BlockTags.MINEABLE_WITH_SHOVEL) ? Hotbar.SHOVEL : Hotbar.PICKAXE;
			input.pitch = 25.0F;
			input.dig = ahead;
			return;
		}

		input.yaw = heading.toYRot();
		input.forward = 1.0F;
	}
}
