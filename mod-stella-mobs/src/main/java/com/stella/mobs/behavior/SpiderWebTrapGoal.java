package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public final class SpiderWebTrapGoal extends Goal {
	public static final int PRIORITY = 5;
	public static final int COOLDOWN_TICKS = 60;
	public static final int WEB_CAP = 4;
	private static final double REACH = 3.5;

	private final Spider spider;
	private @Nullable Player target;
	private int nextWebTick;
	private int websPlaced;

	public SpiderWebTrapGoal(final Spider spider) {
		this.spider = spider;
	}

	@Override
	public boolean canUse() {
		if (this.websPlaced >= WEB_CAP || this.spider.tickCount < this.nextWebTick) {
			return false;
		}

		LivingEntity candidate = this.spider.getTarget();
		if (!(candidate instanceof Player player)
			|| !player.isAlive()
			|| player.isCreative()
			|| player.isSpectator()
			|| this.spider.distanceToSqr(player) > REACH * REACH) {
			return false;
		}

		this.target = player;
		return true;
	}

	@Override
	public void start() {
		Player player = this.target;
		this.target = null;
		if (player == null
			|| !player.isAlive()
			|| player.isCreative()
			|| player.isSpectator()
			|| this.spider.distanceToSqr(player) > REACH * REACH
			|| !(this.spider.level() instanceof ServerLevel level)
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}

		BlockPos position = player.blockPosition();
		if (!level.getBlockState(position).canBeReplaced()
			|| !level.setBlockAndUpdate(position, Blocks.COBWEB.defaultBlockState())) {
			return;
		}

		this.websPlaced++;
		this.nextWebTick = this.spider.tickCount + COOLDOWN_TICKS;
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] R08 webPlaced spider={} block={} count={}/{} tick={}",
			this.spider.getType(), position, this.websPlaced, WEB_CAP, this.spider.tickCount
		);
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void stop() {
		this.target = null;
	}
}
