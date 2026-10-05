package com.stella.tower;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** The tower's lamp, in any {@link TowerLampSkin}. */
public final class TowerLampBlock extends Block {
	public static final EnumProperty<TowerLampSkin> SKIN = EnumProperty.create("skin", TowerLampSkin.class);

	public TowerLampBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SKIN, TowerLampSkin.SEA_LANTERN));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SKIN);
	}

	public BlockState skinned(final TowerLampSkin skin) {
		return this.defaultBlockState().setValue(SKIN, skin);
	}
}
