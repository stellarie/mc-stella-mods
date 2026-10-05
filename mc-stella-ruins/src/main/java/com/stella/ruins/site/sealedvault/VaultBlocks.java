package com.stella.ruins.site.sealedvault;

import com.stella.ruins.StellaRuins;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The blocks of the Sealed Vault. None has an item except through the seal's own drop: a player never carries a
 * wall out. Every block resists explosions at all times.
 */
public final class VaultBlocks {
	private static final float RESIST = 3_600_000.0F;
	private static final float WALL_HARDNESS = 3.0F;

	public static final VaultWallBlock WALL = block("vault_wall", VaultWallBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK).strength(WALL_HARDNESS, RESIST).noLootTable().sound(SoundType.POLISHED_DEEPSLATE).pushReaction(PushReaction.IMMOVEABLE));
	public static final VaultWallBlock FLOOR = block("vault_floor", VaultWallBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.DEEPSLATE).strength(WALL_HARDNESS, RESIST).noLootTable().sound(SoundType.DEEPSLATE_TILES).pushReaction(PushReaction.IMMOVEABLE));
	public static final VaultSealBlock SEAL = block("vault_seal", VaultSealBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.GOLD).strength(4.0F, RESIST).lightLevel(state -> 8).sound(SoundType.AMETHYST).pushReaction(PushReaction.IMMOVEABLE));
	public static final PitCoverBlock PIT_COVER = block("vault_pit_cover", PitCoverBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.DEEPSLATE).strength(-1.0F, RESIST).noLootTable().noOcclusion().sound(SoundType.DEEPSLATE_TILES).pushReaction(PushReaction.IMMOVEABLE));
	public static final PlateBlock ARROW_PLATE = block("vault_arrow_plate", PlateBlock::new, plateProperties());
	public static final PlateBlock DROP_PLATE = block("vault_drop_plate", PlateBlock::new, plateProperties());
	public static final VentBlock VENT = block("vault_vent", VentBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK).strength(-1.0F, RESIST).noLootTable().sound(SoundType.POLISHED_DEEPSLATE).pushReaction(PushReaction.IMMOVEABLE));

	private VaultBlocks() {
	}

	private static BlockBehaviour.Properties plateProperties() {
		return BlockBehaviour.Properties.of()
			.mapColor(MapColor.DEEPSLATE).strength(-1.0F, RESIST).noLootTable().noCollision().noOcclusion()
			.sound(SoundType.DEEPSLATE_TILES).pushReaction(PushReaction.IMMOVEABLE);
	}

	private static <B extends Block> B block(final String path, final Function<BlockBehaviour.Properties, B> factory, final BlockBehaviour.Properties properties) {
		Identifier id = StellaRuins.id(path);
		return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id))));
	}

	/** Loads the class, which registers every block. */
	public static void init() {
	}

	/**
	 * A wall or a floor of the vault. It cannot be mined while its vault is not complete. Afterwards it mines like
	 * deepslate, with the same tool rules. The client always predicts the deepslate time, because only the server
	 * knows the vault.
	 */
	public static final class VaultWallBlock extends Block {
		public VaultWallBlock(final Properties properties) {
			super(properties);
		}

		@Override
		protected float getDestroyProgress(final BlockState state, final Player player, final BlockGetter level, final BlockPos pos) {
			if (level instanceof ServerLevel server && !VaultRegistry.get(server).opened(server, pos)) {
				return 0.0F;
			}
			return Blocks.DEEPSLATE.defaultBlockState().getDestroyProgress(player, level, pos);
		}
	}

	/** A seal on its plinth. A player breaks it and gets one seal item from its loot table. */
	public static final class VaultSealBlock extends Block {
		public VaultSealBlock(final Properties properties) {
			super(properties);
		}
	}

	/**
	 * The floor of a drop trap. Closed it is a full block. Open it has no collision and no model, so whoever stands
	 * on it falls. Every cover of one pit joins to the next, so one flood opens or closes the whole pit.
	 */
	public static final class PitCoverBlock extends Block {
		public static final BooleanProperty OPEN = BooleanProperty.create("open");

		public PitCoverBlock(final Properties properties) {
			super(properties);
			registerDefaultState(this.stateDefinition.any().setValue(OPEN, false));
		}

		@Override
		protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(OPEN);
		}

		@Override
		protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
			return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
		}

		@Override
		protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
			return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
		}
	}

	/** A hidden pressure plate. A player who stands on an armed plate fires the trap and disarms the whole row. */
	public static final class PlateBlock extends Block {
		public static final BooleanProperty ARMED = BooleanProperty.create("armed");
		private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0);

		public PlateBlock(final Properties properties) {
			super(properties);
			registerDefaultState(this.stateDefinition.any().setValue(ARMED, true));
		}

		@Override
		protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(ARMED);
		}

		@Override
		protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected void entityInside(
			final BlockState state, final Level level, final BlockPos pos, final Entity entity, final InsideBlockEffectApplier applier, final boolean isPrecise
		) {
			if (level instanceof ServerLevel server && entity instanceof ServerPlayer player && !player.isSpectator() && state.getValue(ARMED)) {
				VaultTraps.fire(server, pos, state);
			}
		}

		@Override
		protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
			VaultTraps.reset(level, pos);
		}
	}

	/** A dart vent in a corridor wall. It faces into the corridor and shoots when a plate fires. */
	public static final class VentBlock extends Block {
		public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

		public VentBlock(final Properties properties) {
			super(properties);
			registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		protected BlockState rotate(final BlockState state, final Rotation rotation) {
			return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
		}
	}
}
