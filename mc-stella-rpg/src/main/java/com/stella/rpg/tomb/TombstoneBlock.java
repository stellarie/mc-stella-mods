package com.stella.rpg.tomb;

import com.stella.rpg.StellaRpg;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TombstoneBlock extends Block implements EntityBlock {
	private static final int TICKS_PER_MINUTE = 1200;
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0),
		Block.box(4.0, 3.0, 7.0, 12.0, 14.0, 9.0)
	);
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, StellaRpg.id("tombstone"));
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, StellaRpg.id("tombstone"));
	public static final TombstoneBlock BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		StellaRpg.id("tombstone"),
		new TombstoneBlock(
			BlockBehaviour.Properties.of()
				.setId(BLOCK_KEY)
				.mapColor(MapColor.STONE)
				.strength(-1.0F, 3600000.0F)
				.pushReaction(PushReaction.IMMOVEABLE)
				.sound(SoundType.STONE)
				.noOcclusion()
				.noLootTable()
		)
	);
	public static final Item ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		StellaRpg.id("tombstone"),
		new BlockItem(BLOCK, new Item.Properties().setId(ITEM_KEY).useBlockDescriptionPrefix())
	);

	private TombstoneBlock(final Properties properties) {
		super(properties);
	}

	public static void register() {
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new TombstoneBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final BlockHitResult hit
	) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof TombstoneBlockEntity tomb)) {
			return InteractionResult.SUCCESS;
		}
		long remaining = tomb.ticksUntilOpen(player, level.getGameTime());
		if (remaining > 0L) {
			long minutes = (remaining + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE;
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback(
				"message.mod-stella-rpg.tombstone_locked",
				"This tombstone opens for others in %s minute(s).",
				minutes
			));
			return InteractionResult.SUCCESS;
		}
		serverPlayer.openMenu(new SimpleMenuProvider(
			(id, inventory, opener) -> new TombstoneMenu(id, inventory, tomb.slots(), ContainerLevelAccess.create(level, pos)),
			Component.translatableWithFallback("block.mod-stella-rpg.tombstone", "Tombstone")
		));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onExplosionHit(
		final BlockState state,
		final ServerLevel level,
		final BlockPos pos,
		final Explosion explosion,
		final BiConsumer<ItemStack, BlockPos> dropConsumer
	) {
	}

	@Override
	protected VoxelShape getShape(
		final BlockState state,
		final BlockGetter level,
		final BlockPos pos,
		final CollisionContext context
	) {
		return SHAPE;
	}
}
