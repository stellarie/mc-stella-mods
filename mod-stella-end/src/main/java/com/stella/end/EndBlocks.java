package com.stella.end;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The End's own blocks: the soul flame of the Sovereign's ring. It has no item and drops nothing. */
public final class EndBlocks {
	public static final Identifier SOUL_FLAME_ID = StellaEnd.id("soul_flame");
	private static SoulFlameBlock soulFlame;

	private EndBlocks() {
	}

	public static void register() {
		soulFlame = Registry.register(BuiltInRegistries.BLOCK, SOUL_FLAME_ID, new SoulFlameBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_FIRE)
				.setId(ResourceKey.create(Registries.BLOCK, SOUL_FLAME_ID))
				.noLootTable()
		));
	}

	public static SoulFlameBlock soulFlame() {
		return soulFlame;
	}
}
