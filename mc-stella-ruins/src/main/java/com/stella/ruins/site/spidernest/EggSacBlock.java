package com.stella.ruins.site.spidernest;

import com.stella.ruins.StellaRuins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * An egg sac. It breaks in one hit, drops nothing, and tells the nest's objective when it goes, by any cause.
 * It glows at light level 4, so the sacs read as beacons in the dark.
 */
public final class EggSacBlock extends Block {
	public static final int LIGHT = 4;

	public static final EggSacBlock BLOCK = register();

	public EggSacBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel level, final BlockPos pos, final boolean movedByPiston) {
		SacBreaks.onRemoved(level, pos);
	}

	private static EggSacBlock register() {
		Identifier id = StellaRuins.id("egg_sac");
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.TERRACOTTA_YELLOW)
			.strength(0.0F)
			.noLootTable()
			.lightLevel(state -> LIGHT)
			.sound(SoundType.SLIME_BLOCK)
			.pushReaction(PushReaction.POPPED)
			.setId(ResourceKey.create(Registries.BLOCK, id));
		return Registry.register(BuiltInRegistries.BLOCK, id, new EggSacBlock(properties));
	}

	/** Loads the class, which registers the block. */
	public static void init() {
	}
}
