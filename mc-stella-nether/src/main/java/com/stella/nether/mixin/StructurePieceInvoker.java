package com.stella.nether.mixin;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a piece place a block in its own coordinates, rotated with it. */
@Mixin(StructurePiece.class)
public interface StructurePieceInvoker {
	@Invoker("placeBlock")
	void stellaNether$placeBlock(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box);

	@Invoker("getWorldPos")
	net.minecraft.core.BlockPos.MutableBlockPos stellaNether$worldPos(int x, int y, int z);
}
