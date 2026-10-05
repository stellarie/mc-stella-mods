package com.stella.ruins.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The one piece of a ruin: its frame, drawn by its site's layout into each chunk it crosses. */
public final class RuinPiece extends StructurePiece {
	private final RuinFrame frame;

	public RuinPiece(final RuinFrame frame) {
		super(RuinsWorld.PIECE, 0, frame.box());
		this.frame = frame;
	}

	public RuinPiece(final CompoundTag tag) {
		super(RuinsWorld.PIECE, tag);
		this.frame = RuinFrame.CODEC.parse(NbtOps.INSTANCE, tag.getCompoundOrEmpty("Frame")).getOrThrow();
	}

	public RuinFrame frame() {
		return this.frame;
	}

	@Override
	protected void addAdditionalSaveData(final StructurePieceSerializationContext context, final CompoundTag tag) {
		tag.put("Frame", RuinFrame.CODEC.encodeStart(NbtOps.INSTANCE, this.frame).getOrThrow());
	}

	@Override
	public void postProcess(
		final WorldGenLevel level, final StructureManager structureManager, final ChunkGenerator generator, final RandomSource random,
		final BoundingBox chunkBox, final ChunkPos chunkPos, final BlockPos pivot
	) {
		build(level, chunkBox);
	}

	/** Draws the site into one box of a level. Worldgen calls it once for each chunk; tests call it with any box. */
	public void build(final WorldGenLevel level, final BoundingBox clip) {
		RuinSite site = this.frame.siteOrNull();
		if (site != null) {
			site.layout().draw(LayoutContext.forWorld(this.frame, level, clip));
		}
	}
}
