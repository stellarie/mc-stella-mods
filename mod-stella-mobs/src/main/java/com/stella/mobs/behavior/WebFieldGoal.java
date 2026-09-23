package com.stella.mobs.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The brood mother's lair: a three by three field of cobweb around the boss spider's own feet, written
 * every 40 ticks. It claims no flag. Placement honours mob griefing, which vanilla gates cobweb writes
 * on and a mod call is not gated by itself, and it only replaces a replaceable block over a sturdy floor.
 */
public class WebFieldGoal extends Goal {
	public static final int INTERVAL_TICKS = 40;
	private static final int FIELD_RADIUS = 1;

	private final Spider spider;
	private long nextWebTick;

	public WebFieldGoal(final Spider spider) {
		this.spider = spider;
	}

	@Override
	public boolean canUse() {
		return this.spider.isAlive() && ReactiveState.get(this.spider).playerInRange(this.spider);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	@Override
	public void tick() {
		long now = this.spider.level().getGameTime();
		if (now < this.nextWebTick) {
			return;
		}

		this.nextWebTick = now + INTERVAL_TICKS;
		this.writeField();
	}

	private void writeField() {
		if (!(this.spider.level() instanceof ServerLevel level)) {
			return;
		}

		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}

		BlockPos centre = this.spider.blockPosition();

		for (int dx = -FIELD_RADIUS; dx <= FIELD_RADIUS; dx++) {
			for (int dz = -FIELD_RADIUS; dz <= FIELD_RADIUS; dz++) {
				BlockPos pos = centre.offset(dx, 0, dz);
				// The spider's own tile stays clear, so the field is a lair and never a cage.
				if (pos.equals(centre)) {
					continue;
				}

				BlockPos below = pos.below();
				if (!level.getBlockState(pos).canBeReplaced()) {
					continue;
				}

				if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					continue;
				}

				level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
			}
		}
	}
}
