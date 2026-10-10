package com.stella.tower;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.Block;

/**
 * The portal dais's stair, in any {@link TowerSkin}: one block, with the tower wall's strength, so no blast, wither,
 * piston or survival player takes it. Each skin draws it with that stone's stair model (see the blockstate).
 */
public final class TowerStairBlock extends StairBlock {
	public static final EnumProperty<TowerSkin> SKIN = EnumProperty.create("skin", TowerSkin.class);

	public TowerStairBlock(final BlockState base, final Properties properties) {
		super(base, properties);
		this.registerDefaultState(this.defaultBlockState().setValue(SKIN, TowerSkin.BLACKSTONE));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(SKIN);
	}

	public BlockState skinned(final TowerSkin skin) {
		return this.defaultBlockState().setValue(SKIN, skin);
	}
}
