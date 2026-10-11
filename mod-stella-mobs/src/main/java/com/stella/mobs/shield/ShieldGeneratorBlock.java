package com.stella.mobs.shield;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A block that keeps hostile mobs out of the box around it and keeps them from aggro inside it. */
public final class ShieldGeneratorBlock extends BaseEntityBlock {
	public ShieldGeneratorBlock(final Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new ShieldGeneratorBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		if (level.isClientSide() || type != ShieldBlocks.ENTITY) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<ShieldGeneratorBlockEntity>) (world, pos, current, generator) -> generator.serverTick((ServerLevel) world);
	}
}
