package com.stella.nether.mixin;

import com.stella.nether.EndChest;
import com.stella.nether.NetherBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every stronghold's portal room keeps one sealed chest, by the stairs up to the frame. */
@Mixin(StrongholdPieces.PortalRoom.class)
public abstract class StrongholdChestMixin {
	@Inject(method = "postProcess", at = @At("TAIL"))
	private void stellaNether$placeChest(final WorldGenLevel level, final StructureManager structures, final ChunkGenerator generator,
		final RandomSource random, final BoundingBox box, final ChunkPos chunk, final BlockPos pivot, final CallbackInfo ci) {
		StructurePieceInvoker piece = (StructurePieceInvoker) this;
		BlockPos at = piece.stellaNether$worldPos(EndChest.ROOM_X, EndChest.ROOM_Y, EndChest.ROOM_Z).immutable();
		// The room is placed chunk by chunk; only the pass whose chunk holds the spot places the chest.
		if (box.isInside(at)) {
			piece.stellaNether$placeBlock(level, NetherBlocks.SEALED_CHEST.defaultBlockState(), EndChest.ROOM_X, EndChest.ROOM_Y, EndChest.ROOM_Z, box);
			EndChest.placedInRoom(at);
		}
	}
}
