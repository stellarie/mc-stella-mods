package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.StellaRuins;
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
 * The one block of this site. The throne door fills the doorway between the antechamber and the throne room.
 * It has no item, and it resists breaking and explosions, so only the objective opens it.
 */
public final class KeepBlocks {
	public static final Block THRONE_DOOR = register("keep_throne_door", BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.sound(SoundType.POLISHED_DEEPSLATE)
		.pushReaction(PushReaction.IMMOVEABLE));

	private KeepBlocks() {
	}

	private static Block register(final String path, final BlockBehaviour.Properties properties) {
		Identifier id = StellaRuins.id(path);
		return Registry.register(BuiltInRegistries.BLOCK, id, new Block(properties.setId(ResourceKey.create(Registries.BLOCK, id))));
	}

	/** Loads the class, which registers the block. */
	public static void register() {
	}
}
