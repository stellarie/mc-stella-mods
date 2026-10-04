package com.stella.ruins.site.deepforge;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.RuinItems;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The two blocks of the Deep Forge. Both resist breaking and explosions at all times. */
public final class DeepForgeBlocks {
	public static final DeepForgeBlock FORGE = block("deep_forge", DeepForgeBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_GRAY)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.lightLevel(state -> state.getValue(DeepForgeBlock.LIT) ? 13 : 0)
		.sound(SoundType.DEEPSLATE_BRICKS)
		.pushReaction(PushReaction.IMMOVEABLE));
	public static final ForgeLeverBlock FORGE_LEVER = block("forge_lever", ForgeLeverBlock::new, BlockBehaviour.Properties.of()
		.noCollision()
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.sound(SoundType.STONE)
		.pushReaction(PushReaction.IMMOVEABLE));
	public static final Item FORGE_ITEM = RuinItems.register("deep_forge", properties -> new BlockItem(FORGE, properties), new Item.Properties().useBlockDescriptionPrefix());

	private DeepForgeBlocks() {
	}

	private static <B extends Block> B block(final String path, final Function<BlockBehaviour.Properties, B> factory, final BlockBehaviour.Properties properties) {
		Identifier id = StellaRuins.id(path);
		return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id))));
	}

	/** Loads the class, which registers everything above. */
	static void register() {
	}
}
