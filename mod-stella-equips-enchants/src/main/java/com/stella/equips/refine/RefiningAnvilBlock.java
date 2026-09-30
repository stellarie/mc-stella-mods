package com.stella.equips.refine;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Refining Anvil: an anvil in silver that never wears and never falls. Using it opens the anvil menu on its
 * Refine tab. The menu has no block access, so neither a repair nor a refine can damage the block.
 */
public class RefiningAnvilBlock extends Block {
	public static final EnumProperty<Direction> FACING = AnvilBlock.FACING;

	public RefiningAnvilBlock(final Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
	}

	// The vanilla anvil's own shape, turned the same way.
	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, state.getValue(FACING)).getShape(level, pos, context);
	}

	@Override
	protected InteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos,
		final Player player, final InteractionHand hand, final BlockHitResult hit) {
		return hand == InteractionHand.MAIN_HAND ? this.open(player) : InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		return this.open(player);
	}

	private InteractionResult open(final Player player) {
		if (player instanceof ServerPlayer refiner) {
			refiner.openMenu(new SimpleMenuProvider(
				(id, inventory, owner) -> new AnvilMenu(id, inventory, ContainerLevelAccess.NULL),
				Component.translatable("container.repair")
			));
			ServerPlayNetworking.send(refiner, OpenRefineTabPayload.INSTANCE);
		}
		return InteractionResult.SUCCESS;
	}
}
