package com.stella.nether;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The raid's own blocks. The sealed chest cannot be mined, blown up, or broken by a wither or the dragon, and has no item. */
public final class NetherBlocks {
	public static final Identifier SEALED_CHEST_ID = StellaNether.id("sealed_chest");
	public static final SealedChestBlock SEALED_CHEST = Registry.register(BuiltInRegistries.BLOCK, SEALED_CHEST_ID, new SealedChestBlock(
		BlockBehaviour.Properties.of()
			.setId(ResourceKey.create(Registries.BLOCK, SEALED_CHEST_ID))
			.mapColor(MapColor.COLOR_BLACK)
			.strength(-1.0F, 3_600_000.0F)
			.noLootTable()
			.lightLevel(state -> state.getValue(SealedChestBlock.FILLED) ? 9 : 0)
			.sound(SoundType.POLISHED_DEEPSLATE)
			.pushReaction(PushReaction.IMMOVEABLE)
	));

	public static final Identifier SOUL_FLAME_ID = StellaNether.id("soul_flame");
	public static final SoulFlameBlock SOUL_FLAME = Registry.register(BuiltInRegistries.BLOCK, SOUL_FLAME_ID, new SoulFlameBlock(
		BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
			.setId(ResourceKey.create(Registries.BLOCK, SOUL_FLAME_ID))
			.noLootTable()
	));

	private NetherBlocks() {
	}

	public static void register() {
		BehaviorLog.info("Registered {}", SEALED_CHEST_ID);
	}
}
