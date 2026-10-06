package com.stella.tower;

import net.minecraft.world.level.block.Block;

/**
 * The surface a lost castle is built from: one tower environment, the stone of its walls and of its rubble,
 * and one accent stone for its decoration. Every block is a full cube and none damages a stander, so the
 * castle walks as it did before.
 */
public record LostCastlePalette(TrapTheme environment, Block wall, Block rubble, Block accent) {
}
