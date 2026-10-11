package com.stella.mobs.shield;

import com.stella.mobs.StellaMobs;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Registers the Shield Generator block, its item and its block entity. */
public final class ShieldBlocks {
	public static final String PATH = "shield_generator";
	private static final Identifier ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, PATH);

	public static final ShieldGeneratorBlock BLOCK = Registry.register(
		BuiltInRegistries.BLOCK, ID,
		new ShieldGeneratorBlock(BlockBehaviour.Properties.of()
			.setId(ResourceKey.create(Registries.BLOCK, ID))
			.mapColor(MapColor.EMERALD)
			.strength(3.0F, 6.0F)
			.lightLevel(state -> 7)
			.sound(SoundType.METAL)));
	public static final Item ITEM = Registry.register(
		BuiltInRegistries.ITEM, ID,
		new BlockItem(BLOCK, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID)).useBlockDescriptionPrefix()));
	public static final BlockEntityType<ShieldGeneratorBlockEntity> ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, ID, new BlockEntityType<>(ShieldGeneratorBlockEntity::new, Set.of(BLOCK)));

	private ShieldBlocks() {
	}

	/** Loads the class, which registers everything above, and starts the registry and the drop. */
	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ITEM));
		ShieldZones.register();
		ShieldDrops.register();
	}
}
