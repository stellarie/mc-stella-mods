package com.stella.ruins.site.deepforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A lever of the Deep Forge. It does not move until the three Forge Cores are set, and once pulled it stays
 * pulled. The forge's objective reads the state of every lever marker, so no other link is needed.
 */
public final class ForgeLeverBlock extends LeverBlock {
	public static final BooleanProperty UNLOCKED = BooleanProperty.create("unlocked");

	public ForgeLeverBlock(final Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(UNLOCKED, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(UNLOCKED);
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (!state.getValue(UNLOCKED)) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.forge.lever_stiff", "The lever will not move."));
			}
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(POWERED)) {
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}
}
