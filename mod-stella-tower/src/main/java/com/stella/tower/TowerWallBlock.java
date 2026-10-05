package com.stella.tower;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** The maze's wall, in any {@link TowerSkin}: one block, so every skin is still the maze wall. */
public final class TowerWallBlock extends Block {
	public static final EnumProperty<TowerSkin> SKIN = EnumProperty.create("skin", TowerSkin.class);

	public TowerWallBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SKIN, TowerSkin.BLACKSTONE));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SKIN);
	}

	public BlockState skinned(final TowerSkin skin) {
		return this.defaultBlockState().setValue(SKIN, skin);
	}
}
