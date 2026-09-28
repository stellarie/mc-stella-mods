package com.stella.tower;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The tower's own blocks. The wall and the lamp cannot be mined or blown up, and neither has an item, so
 * no player can carry an unbreakable block out of the tower.
 */
public final class TowerBlocks {
	/** The walls, the ceiling and the ring under each floor's edge. */
	public static final Block TOWER_WALL = register("tower_wall", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.sound(SoundType.POLISHED_DEEPSLATE)
		.pushReaction(PushReaction.IMMOVEABLE));
	/** The light set into each floor's ceiling. */
	public static final Block TOWER_LAMP = register("tower_lamp", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.QUARTZ)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.lightLevel(state -> 15)
		.sound(SoundType.GLASS)
		.pushReaction(PushReaction.IMMOVEABLE));
	/** The blue portal a lit crying obsidian frame holds. */
	public static final TowerPortalBlock TOWER_PORTAL = register("tower_portal", TowerPortalBlock::new, BlockBehaviour.Properties.of()
		.noCollision()
		.randomTicks()
		.strength(-1.0F)
		.sound(SoundType.GLASS)
		.lightLevel(state -> 11)
		.noLootTable()
		.pushReaction(PushReaction.IMMOVEABLE));

	private TowerBlocks() {
	}

	private static <B extends Block> B register(
		final String path, final java.util.function.Function<BlockBehaviour.Properties, B> factory, final BlockBehaviour.Properties properties
	) {
		Identifier id = StellaTower.id(path);
		return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id))));
	}

	public static void register() {
		StellaTower.LOGGER.debug("Registered the tower blocks");
	}
}
