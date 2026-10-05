package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.StellaRuins;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The two blocks of the meteor. Neither has an item: a player never carries one, and the crater alone places them.
 * The hot ore burns like magma. When the meteor cools, the crater turns every ore block into cooled meteorite.
 */
public final class MeteorBlocks {
	public static final int ORE_LIGHT = 10;
	public static final float ORE_HARDNESS = 3.0F;
	/** The damage of one step on hot ore, as on magma. */
	public static final float BURN = 1.0F;

	public static final StarmetalOreBlock STARMETAL_ORE = block("starmetal_ore", StarmetalOreBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_ORANGE)
		.strength(ORE_HARDNESS, ORE_HARDNESS)
		.lightLevel(state -> ORE_LIGHT)
		.sound(SoundType.STONE));
	public static final Block COOLED_METEORITE = block("cooled_meteorite", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.DEEPSLATE)
		.strength(ORE_HARDNESS, ORE_HARDNESS)
		.sound(SoundType.STONE));

	private MeteorBlocks() {
	}

	/** Hot Starmetal ore. It glows at light 10 and burns anything that stands on it without sneaking. */
	public static final class StarmetalOreBlock extends Block {
		public StarmetalOreBlock(final Properties properties) {
			super(properties);
		}

		@Override
		public void stepOn(final Level level, final BlockPos pos, final BlockState state, final Entity entity) {
			if (!entity.isSteppingCarefully() && entity instanceof LivingEntity && level instanceof ServerLevel server) {
				entity.hurtServer(server, server.damageSources().hotFloor(), BURN);
			}
			super.stepOn(level, pos, state, entity);
		}
	}

	private static <B extends Block> B block(final String path, final Function<BlockBehaviour.Properties, B> factory, final BlockBehaviour.Properties properties) {
		Identifier id = StellaRuins.id(path);
		return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id))));
	}

	/** Loads the class, which registers both blocks. */
	public static void init() {
	}
}
