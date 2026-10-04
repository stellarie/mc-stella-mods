package com.stella.ruins.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The objective core of a site. It wakes for a player, runs the objective and opens the vault. It cannot be broken. */
public final class RuinCoreBlock extends BaseEntityBlock {
	public RuinCoreBlock(final Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new RuinCoreBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		if (level.isClientSide() || type != RuinBlocks.CORE_ENTITY) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<RuinCoreBlockEntity>) (world, pos, current, core) -> core.serverTick((ServerLevel) world);
	}

	@Override
	protected InteractionResult useItemOn(
		final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult hit
	) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (RuinRuns.use(server, pos, serverPlayer, stack)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			RuinData.Entry entry = RuinData.get(server).entry(pos);
			RuinPhase phase = entry == null ? RuinPhase.DORMANT : entry.phase();
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.core." + phase.id(), "The core is " + phase.id() + "."));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel level, final BlockPos pos, final boolean movedByPiston) {
		RuinData.get(level).forget(pos);
	}
}
