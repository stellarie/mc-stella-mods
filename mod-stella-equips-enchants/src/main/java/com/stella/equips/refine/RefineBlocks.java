package com.stella.equips.refine;

import com.stella.equips.StellaEquips;
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

/** The Refining Anvil's block and item. */
public final class RefineBlocks {
	public static final Identifier REFINING_ANVIL_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "refining_anvil");
	public static final RefiningAnvilBlock REFINING_ANVIL = Registry.register(
		BuiltInRegistries.BLOCK, REFINING_ANVIL_ID,
		new RefiningAnvilBlock(BlockBehaviour.Properties.of()
			.setId(ResourceKey.create(Registries.BLOCK, REFINING_ANVIL_ID))
			.mapColor(MapColor.METAL)
			.requiresCorrectToolForDrops()
			.strength(5.0F, 1200.0F)
			.sound(SoundType.ANVIL)
			.pushReaction(PushReaction.IMMOVEABLE))
	);
	public static final Item REFINING_ANVIL_ITEM = Registry.register(
		BuiltInRegistries.ITEM, REFINING_ANVIL_ID,
		new BlockItem(REFINING_ANVIL, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, REFINING_ANVIL_ID)).useBlockDescriptionPrefix())
	);

	private RefineBlocks() {
	}

	public static void register() {
		StellaEquips.LOGGER.debug("Registered {}", REFINING_ANVIL_ID);
	}
}
