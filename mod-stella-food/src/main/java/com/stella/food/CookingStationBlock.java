package com.stella.food;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public final class CookingStationBlock extends Block {
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(
		Registries.BLOCK,
		StellaFood.id("cooking_station")
	);
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(
		Registries.ITEM,
		StellaFood.id("cooking_station")
	);
	public static final CookingStationBlock BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		StellaFood.id("cooking_station"),
		new CookingStationBlock(
			BlockBehaviour.Properties.of()
				.setId(BLOCK_KEY)
				.mapColor(MapColor.WOOD)
				.strength(2.5F)
				.sound(SoundType.WOOD)
		)
	);
	public static final Item ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		StellaFood.id("cooking_station"),
		new BlockItem(BLOCK, new Item.Properties().setId(ITEM_KEY).useBlockDescriptionPrefix())
	);

	private CookingStationBlock(final Properties properties) {
		super(properties);
	}

	public static void register() {
	}

	@Override
	protected InteractionResult useItemOn(
		final ItemStack stack,
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final InteractionHand hand,
		final BlockHitResult hit
	) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new SimpleMenuProvider(
				(id, inventory, opener) -> new CookingStationMenu(
					id,
					inventory,
					ContainerLevelAccess.create(level, pos)
				),
				Component.translatable("block.mod-stella-food.cooking_station")
			));
		}
		return InteractionResult.SUCCESS;
	}
}
