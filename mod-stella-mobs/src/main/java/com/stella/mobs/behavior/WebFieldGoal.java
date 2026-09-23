package com.stella.mobs.behavior;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * The goal records the tiles it actually wrote, so a clause reads the writes the ability produced rather
 * than a box over a shared level, and the record is bounded because a roaming spider writes for as long
 * as it lives.
 */
public class WebFieldGoal extends Goal {
	public static final int INTERVAL_TICKS = 40;
	/** The most tiles the record keeps. The lair is a three by three field, so this is far above one pass. */
	public static final int RECORD_LIMIT = 64;
	private static final int FIELD_RADIUS = 1;

	private final Spider spider;
	/** The tiles this goal has written, in write order, bounded by RECORD_LIMIT. */
	private final Set<BlockPos> written = new LinkedHashSet<>();
	private long nextWebTick;

	public WebFieldGoal(final Spider spider) {
		this.spider = spider;
	}

	/** The tiles the goal actually wrote, so a test reads the ability's own product and not the world. */
	public List<BlockPos> written() {
		return List.copyOf(this.written);
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

				if (level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState())) {
					remember(pos);
				}
			}
		}
	}

	/** Record only a tile the write actually changed, and stop growing at the limit. */
	private void remember(final BlockPos pos) {
		if (this.written.size() < RECORD_LIMIT) {
			this.written.add(pos);
		}
	}
}
