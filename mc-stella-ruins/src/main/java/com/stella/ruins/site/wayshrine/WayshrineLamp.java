package com.stella.ruins.site.wayshrine;

import com.stella.ruins.StellaRuins;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The shrine lamp on top of the obelisk. It burns while the shrine is armed and goes dark while the shrine rests,
 * so a player sees the reset state from far off. It has no item: only the layout places it.
 */
public final class WayshrineLamp extends BaseEntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 9.0, 11.0);

	public static final WayshrineLamp LAMP = create();
	public static final BlockEntityType<WayshrineLampEntity> ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, StellaRuins.id("wayshrine_lamp"), new BlockEntityType<>(WayshrineLampEntity::new, Set.of(LAMP)));

	private WayshrineLamp(final BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(LIT, true));
	}

	private static WayshrineLamp create() {
		Identifier id = StellaRuins.id("wayshrine_lamp");
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_CYAN)
			.strength(-1.0F, 3_600_000.0F)
			.noLootTable()
			.noOcclusion()
			.lightLevel(state -> state.getValue(LIT) ? 15 : 0)
			.sound(SoundType.LANTERN)
			.setId(ResourceKey.create(Registries.BLOCK, id));
		return Registry.register(BuiltInRegistries.BLOCK, id, new WayshrineLamp(properties));
	}

	/** Loads the class, which registers the block and its entity. */
	public static void register() {
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new WayshrineLampEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		if (level.isClientSide() || type != ENTITY) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<WayshrineLampEntity>) (world, pos, current, lamp) -> lamp.serverTick((ServerLevel) world);
	}
}
