package com.stella.tower;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A chest of the tower: a vanilla chest to open, look at and pair into a double, that cannot be mined,
 * blown up, pushed, or broken by a wither or the dragon. Items may still leave it by hopper, and by hand.
 * It is a valid block of vanilla's chest block entity type, so vanilla's own renderer draws it and no client
 * code is needed.
 */
public class TowerChestBlock extends ChestBlock {
	public TowerChestBlock(final BlockBehaviour.Properties properties) {
		super(() -> BlockEntityTypes.CHEST, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
	}
}
