package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The two blocks of a site's objective. Neither has an item, so no player carries one out; layouts place them.
 * Both resist breaking and explosions at all times.
 */
public final class RuinBlocks {
	public static final RuinCoreBlock CORE = block("ruin_core", RuinCoreBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_PURPLE)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.lightLevel(state -> 7)
		.sound(SoundType.AMETHYST)
		.pushReaction(PushReaction.IMMOVEABLE));
	public static final RuinVaultBlock VAULT = block("ruin_vault", RuinVaultBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_GRAY)
		.strength(-1.0F, 3_600_000.0F)
		.noLootTable()
		.lightLevel(state -> state.getValue(RuinVaultBlock.LOCKED) ? 0 : 9)
		.sound(SoundType.METAL)
		.pushReaction(PushReaction.IMMOVEABLE));
	public static final BlockEntityType<RuinCoreBlockEntity> CORE_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, StellaRuins.id("ruin_core"), new BlockEntityType<>(RuinCoreBlockEntity::new, Set.of(CORE)));
	public static final BlockEntityType<RuinVaultBlockEntity> VAULT_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, StellaRuins.id("ruin_vault"), new BlockEntityType<>(RuinVaultBlockEntity::new, Set.of(VAULT)));

	private RuinBlocks() {
	}

	private static <B extends Block> B block(final String path, final Function<BlockBehaviour.Properties, B> factory, final BlockBehaviour.Properties properties) {
		Identifier id = StellaRuins.id(path);
		return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id))));
	}

	/** Loads the class, which registers everything above. */
	public static void register() {
	}
}
