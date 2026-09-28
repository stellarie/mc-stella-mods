package com.stella.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A trap set into a floor: a player who steps on it springs it. */
public class TowerTrapBlock extends Block {
	private final TrapTheme theme;

	public TowerTrapBlock(final TrapTheme theme, final Properties properties) {
		super(properties);
		this.theme = theme;
	}

	public TrapTheme theme() {
		return this.theme;
	}

	/** A player who walks onto it, in survival or adventure, springs it; mobs pass over. */
	@Override
	public void stepOn(final Level level, final BlockPos pos, final BlockState state, final Entity entity) {
		super.stepOn(level, pos, state, entity);
		if (level instanceof ServerLevel tower && entity instanceof ServerPlayer player && !player.isSpectator() && !player.isCreative()) {
			TowerTraps.spring(tower, pos, player, this.theme, Math.max(1, TowerLayout.floorAt(pos)));
		}
	}
}
