package com.stella.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;

/**
 * The portal back down. It looks and behaves as a floor's portal, but it leads to the floor below, and
 * drifts soul-blue motes where the way up drifts blue ones.
 */
public class TowerBackPortalBlock extends TowerPortalBlock {
	/** A cold cyan, for the drifting motes. */
	public static final int BACK_PARTICLE_COLOR = 0x4FE6E0;

	public TowerBackPortalBlock(final Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable TeleportTransition getPortalDestination(final ServerLevel level, final Entity entity, final BlockPos pos) {
		return TowerTravel.backDestination(level, entity, pos);
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(new DustParticleOptions(BACK_PARTICLE_COLOR, 1.0F),
				pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
		}
		if (random.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
				pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.03, 0.0);
		}
	}
}
