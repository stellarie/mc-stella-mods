package com.stella.ruins.site.deepforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The great forge. It stays cold until the Forge Guardian falls. Once lit it turns one Deepsteel ore chunk and one
 * coal or charcoal into one Deepsteel ingot, and nothing else in the game does.
 */
public final class DeepForgeBlock extends HorizontalDirectionalBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public DeepForgeBlock(final Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useItemOn(
		final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult hit
	) {
		if (!stack.is(DeepForgeItems.ORE_CHUNK)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (!state.getValue(LIT)) {
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.forge.unlit", "The forge is cold."));
			return InteractionResult.SUCCESS_SERVER;
		}
		ItemStack fuel = findFuel(player, hand);
		if (fuel.isEmpty()) {
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.forge.no_fuel", "The forge needs coal or charcoal."));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
			fuel.shrink(1);
		}
		ItemStack ingot = new ItemStack(DeepForgeItems.INGOT);
		if (!player.getInventory().add(ingot)) {
			player.drop(ingot, false, Prediction.SERVER_ONLY);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Coal or charcoal: the other hand first, then the pack. */
	private static ItemStack findFuel(final Player player, final InteractionHand used) {
		ItemStack other = player.getItemInHand(used == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (isFuel(other)) {
			return other;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack candidate = player.getInventory().getItem(slot);
			if (isFuel(candidate)) {
				return candidate;
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean isFuel(final ItemStack stack) {
		return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			boolean lit = state.getValue(LIT);
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback(lit ? "message.mod-stella-ruins.forge.lit" : "message.mod-stella-ruins.forge.unlit", lit ? "The forge burns." : "The forge is cold."));
		}
		return InteractionResult.SUCCESS;
	}
}
