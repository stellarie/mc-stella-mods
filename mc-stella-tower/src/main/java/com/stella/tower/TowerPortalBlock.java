package com.stella.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;

/**
 * The blue portal. It behaves as a nether portal does for entering and timing, but it leads to the tower,
 * spawns nothing, and holds only while its crying obsidian frame does.
 */
public class TowerPortalBlock extends NetherPortalBlock {
	/** A deep blue, for the drifting motes. */
	public static final int PARTICLE_COLOR = 0x3A6BFF;

	public TowerPortalBlock(final Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable TeleportTransition getPortalDestination(final ServerLevel level, final Entity entity, final BlockPos pos) {
		return TowerTravel.destination(level, entity, pos);
	}

	@Override
	protected void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		// No zombified piglins walk out of the tower's portal.
	}

	@Override
	protected BlockState updateShape(
		final BlockState state, final LevelReader level, final ScheduledTickAccess ticks, final BlockPos pos, final Direction direction,
		final BlockPos neighborPos, final BlockState neighbor, final RandomSource random
	) {
		// The portal's own plane: up, down, and along its axis. A neighbor there that is neither portal nor
		// frame means the frame was broken, and the break runs through the whole portal from block to block.
		boolean inPlane = direction.getAxis().isVertical() || direction.getAxis() == state.getValue(AXIS);
		if (inPlane && !neighbor.is(this) && !TowerPortalShape.isFrame(neighbor)) {
			return Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(new DustParticleOptions(PARTICLE_COLOR, 1.0F),
				pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected ItemStack getCloneItemStack(final LevelReader level, final BlockPos pos, final BlockState state, final boolean includeData) {
		return ItemStack.EMPTY;
	}
}
