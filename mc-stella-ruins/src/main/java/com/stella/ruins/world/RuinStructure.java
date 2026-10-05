package com.stella.ruins.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.ruins.StellaRuins;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The one structure type of the mod. Each structure file names its site, and the site's placement rule decides
 * whether the ground takes it. The structure set file holds the spread and the village exclusion.
 */
public final class RuinStructure extends Structure {
	public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		settingsCodec(instance),
		Codec.STRING.fieldOf("site").forGetter(RuinStructure::site)
	).apply(instance, RuinStructure::new));

	private final String site;

	public RuinStructure(final Structure.StructureSettings settings, final String site) {
		super(settings);
		this.site = site;
	}

	public String site() {
		return this.site;
	}

	@Override
	protected Optional<Structure.GenerationStub> findGenerationPoint(final Structure.GenerationContext context) {
		RuinSite found = RuinSites.get(this.site);
		if (found == null) {
			StellaRuins.LOGGER.warn("[RUINS] structure names site {}, which no mod registers", this.site);
			return Optional.empty();
		}
		Rotation rotation = Rotation.getRandom(context.random());
		long seed = context.random().nextLong();
		boolean turned = rotation.ordinal() % 2 == 1;
		int worldSizeX = turned ? found.sizeZ() : found.sizeX();
		int worldSizeZ = turned ? found.sizeX() : found.sizeZ();
		int west = context.chunkPos().getMiddleBlockX() - worldSizeX / 2;
		int north = context.chunkPos().getMiddleBlockZ() - worldSizeZ / 2;
		Placement.Probe probe = Placement.Probe.of(context.chunkGenerator(), context.heightAccessor(), context.randomState());
		Optional<BlockPos> min = found.placement().choose(probe, context.random(), west, north, worldSizeX, found.sizeY(), worldSizeZ);
		return min.map(pos -> new Structure.GenerationStub(pos, builder -> builder.addPiece(new RuinPiece(RuinFrame.of(found, pos, rotation, seed)))));
	}

	@Override
	public StructureType<?> type() {
		return RuinsWorld.STRUCTURE_TYPE;
	}
}
