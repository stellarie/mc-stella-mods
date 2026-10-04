package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.StellaRuins;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * The shaft of a crystal pillar. A player strikes it by attacking it; {@link ChimeStrikes} reads the strike. It has
 * no item and resists breaking at all times, like the core, so a strike never destroys a pillar.
 */
public final class ResonantCrystalBlock extends Block {
	public static final EnumProperty<Tint> TINT = EnumProperty.create("tint", Tint.class);
	public static final int LIGHT = 10;

	public static final ResonantCrystalBlock BLOCK = register();

	public ResonantCrystalBlock(final Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(TINT, Tint.CYAN));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TINT);
	}

	public BlockState stateOf(final Tint tint) {
		return defaultBlockState().setValue(TINT, tint);
	}

	private static ResonantCrystalBlock register() {
		Identifier id = StellaRuins.id("resonant_crystal");
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE)
			.strength(-1.0F, 3_600_000.0F)
			.noLootTable()
			.lightLevel(state -> LIGHT)
			.sound(SoundType.AMETHYST)
			.setId(ResourceKey.create(Registries.BLOCK, id));
		return Registry.register(BuiltInRegistries.BLOCK, id, new ResonantCrystalBlock(properties));
	}

	/** Loads the class, which registers the block. */
	public static void init() {
	}
}
