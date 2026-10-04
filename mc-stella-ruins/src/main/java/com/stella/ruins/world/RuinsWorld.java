package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Worldgen, objective cores, themed mobs, loot and clues. */
public final class RuinsWorld {
	/** The structure type every ruin file names: {@code mod-stella-ruins:ruin}. */
	public static final StructureType<RuinStructure> STRUCTURE_TYPE = Registry.register(
		BuiltInRegistries.STRUCTURE_TYPE, StellaRuins.id("ruin"), () -> RuinStructure.CODEC);
	public static final StructurePieceType PIECE = Registry.register(
		BuiltInRegistries.STRUCTURE_PIECE, StellaRuins.id("ruin"), (StructurePieceType) (context, tag) -> new RuinPiece(tag));

	private RuinsWorld() {
	}

	public static void register() {
		RuinConfig.load();
		RuinBlocks.register();
		RuinClueItem.register();
		RuinClues.register();
		RuinMobs.register();
		StellaRuins.LOGGER.debug("[RUINS] world library registered");
	}
}
