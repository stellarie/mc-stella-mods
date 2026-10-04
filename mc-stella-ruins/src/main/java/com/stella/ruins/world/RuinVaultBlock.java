package com.stella.ruins.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The vault of a site: a 27-slot chest that stays shut until its core completes. It cannot be broken, locked or
 * not, so the loot leaves only through the menu.
 */
public final class RuinVaultBlock extends BaseEntityBlock {
	public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

	public RuinVaultBlock(final Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(LOCKED, true));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(LOCKED);
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new RuinVaultBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(LOCKED)) {
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.vault_locked", "The vault is sealed."));
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof RuinVaultBlockEntity vault) {
			serverPlayer.openMenu(vault);
		}
		return InteractionResult.SUCCESS;
	}
}
