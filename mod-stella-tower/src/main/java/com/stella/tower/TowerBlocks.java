package com.stella.tower;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The tower's own blocks. The wall, the lamp and the frame cannot be mined, blown up, or broken by a wither
 * or the dragon (their tags say so); the maze's walls can be mined, hard. None has an item, so no player
 * can carry one out of the tower.
 */
public final class TowerBlocks {
	/** The walls, the ceiling and the ring under each floor's edge. */
	public static final Block TOWER_WALL = register("tower_wall", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.sound(SoundType.POLISHED_DEEPSLATE)
		.pushReaction(PushReaction.IMMOVEABLE));
	/** The maze's walls; how many times obsidian's hardness they take to break. */
	public static final float MAZE_HARDNESS_OVER_OBSIDIAN = 4.0F;
	/**
	 * The maze's walls: a pickaxe of diamond or better breaks one, at four times obsidian's hardness, and
	 * obsidian's resistance to blasts. Withers and the dragon cannot, and a broken wall yields nothing. Its
	 * {@link TowerSkin} is the floor's look; every skin is this one block.
	 */
	public static final TowerWallBlock TOWER_MAZE_WALL = register("tower_maze_wall", TowerWallBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK)
		.strength(Blocks.OBSIDIAN.defaultDestroyTime() * MAZE_HARDNESS_OVER_OBSIDIAN, Blocks.OBSIDIAN.getExplosionResistance())
		.requiresCorrectToolForDrops()
		.noLootTable()
		.sound(SoundType.POLISHED_DEEPSLATE)
		.pushReaction(PushReaction.IMMOVEABLE));
	/** Each floor's way up: crying obsidian to look at, but it cannot be mined, blown up, or broken by a wither. */
	public static final Block TOWER_FRAME = register("tower_frame", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.lightLevel(state -> 10)
		.sound(SoundType.STONE)
		.pushReaction(PushReaction.IMMOVEABLE));
	/** The light set into each floor's ceilings, in its band's {@link TowerLampSkin}. */
	public static final TowerLampBlock TOWER_LAMP = register("tower_lamp", TowerLampBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.QUARTZ)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.lightLevel(state -> 15)
		.sound(SoundType.GLASS)
		.pushReaction(PushReaction.IMMOVEABLE));
	/** The blue portal a floor's frame holds. A blast cannot take it, as it cannot take the frame. */
	public static final TowerPortalBlock TOWER_PORTAL = register("tower_portal", TowerPortalBlock::new, BlockBehaviour.Properties.of()
		.noCollision()
		.randomTicks()
		.strength(-1.0F, 3_600_000.0F)
		.sound(SoundType.GLASS)
		.lightLevel(state -> 11)
		.noLootTable()
		.pushReaction(PushReaction.IMMOVEABLE));

	/**
	 * A chest that stays: it cannot be mined, blown up, pushed, or broken by a wither or the dragon. It has no
	 * item, so no player carries one out; the tower places it (see {@link TowerChests}).
	 */
	public static final TowerChestBlock TOWER_CHEST = register("tower_chest", TowerChestBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.sound(SoundType.WOOD)
		.pushReaction(PushReaction.IMMOVEABLE));

	/** The traps, one block for each theme that is laid. */
	public static final java.util.Map<TrapTheme, TowerTrapBlock> TRAPS = registerTraps();

	private TowerBlocks() {
	}

	private static java.util.Map<TrapTheme, TowerTrapBlock> registerTraps() {
		java.util.Map<TrapTheme, TowerTrapBlock> traps = new java.util.EnumMap<>(TrapTheme.class);
		for (TrapTheme theme : TrapTheme.values()) {
			if (theme.laid()) {
				traps.put(theme, register("tower_trap_" + theme.name().toLowerCase(java.util.Locale.ROOT),
					properties -> new TowerTrapBlock(theme, properties), BlockBehaviour.Properties.of()
						.mapColor(MapColor.COLOR_BLACK)
						.strength(Blocks.OBSIDIAN.defaultDestroyTime() * MAZE_HARDNESS_OVER_OBSIDIAN, Blocks.OBSIDIAN.getExplosionResistance())
						.requiresCorrectToolForDrops()
						.noLootTable()
						.sound(SoundType.STONE)
						.pushReaction(PushReaction.IMMOVEABLE)));
			}
		}
		return java.util.Collections.unmodifiableMap(traps);
	}

	private static <B extends Block> B register(
		final String path, final java.util.function.Function<BlockBehaviour.Properties, B> factory, final BlockBehaviour.Properties properties
	) {
		Identifier id = StellaTower.id(path);
		return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id))));
	}

	public static void register() {
		// Vanilla's chest block entity and its renderer serve the tower's chest too.
		((net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType) net.minecraft.world.level.block.entity.BlockEntityTypes.CHEST)
			.addValidBlock(TOWER_CHEST);
		StellaTower.LOGGER.debug("Registered the tower blocks");
	}
}
