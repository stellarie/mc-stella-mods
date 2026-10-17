package com.stella.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoulFireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The soul flame: soul fire that holds on any solid floor, not only soul sand and soil, and burns out alone after
 * {@link #LIFE} ticks. The Sovereign no longer sets it; the block stays registered for the worlds that hold one.
 */
public class SoulFlameBlock extends SoulFireBlock {
	public static final int LIFE = 120;

	public SoulFlameBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(final BlockState state, final LevelReader level, final ScheduledTickAccess ticks, final BlockPos pos,
		final Direction direction, final BlockPos neighborPos, final BlockState neighbor, final RandomSource random) {
		return this.canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected void onPlace(final BlockState state, final Level level, final BlockPos pos, final BlockState old, final boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		if (level.getBlockState(pos).is(this)) {
			level.scheduleTick(pos, this, LIFE);
		}
	}

	@Override
	protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		level.removeBlock(pos, false);
	}
}
