package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a room of the castle was before it was left: the rooms of an abandoned base. Each room of a storey
 * takes one theme and no theme repeats on a storey. A theme is a recipe of props, and a prop is one block,
 * or for a bed two. Every prop is a block whose state does not depend on its neighbours, so a castle built
 * one chunk at a time is the castle built whole.
 */
public enum LostRoomTheme {
	BEDROOM(Prop.BED, Prop.CRAFTING_TABLE, Prop.COBWEB, Prop.COBWEB),
	SMITHY(Prop.FURNACE, Prop.ANVIL, Prop.SMITHING_TABLE, Prop.BLAST_FURNACE),
	ARMORY(Prop.GRINDSTONE, Prop.GRINDSTONE, Prop.FLETCHING_TABLE, Prop.CHAIN_RACK, Prop.CHAIN_RACK),
	LARDER(Prop.SMOKER, Prop.BARREL, Prop.BARREL, Prop.CAULDRON, Prop.HAY),
	STUDY(Prop.BOOKSHELF, Prop.BOOKSHELF, Prop.LECTERN, Prop.CANDLE, Prop.CANDLE),
	STOREROOM(Prop.BARREL, Prop.BARREL, Prop.CRATE, Prop.CRATE, Prop.COBWEB, Prop.COBWEB),
	BARRACKS(Prop.BED, Prop.BED),
	CHAPEL(Prop.ALTAR, Prop.PEW, Prop.PEW, Prop.CANDLE, Prop.CANDLE),
	CRYPT(Prop.SKULL, Prop.SKULL, Prop.GRAVE, Prop.GRAVE, Prop.SOUL_LANTERN, Prop.SOUL_LANTERN);

	/** One piece of a room's furniture, and the block that shows it. */
	public enum Prop {
		BED(2),
		CRAFTING_TABLE(1),
		COBWEB(1),
		FURNACE(1),
		ANVIL(1),
		SMITHING_TABLE(1),
		BLAST_FURNACE(1),
		GRINDSTONE(1),
		FLETCHING_TABLE(1),
		/** An iron chain hung from the ceiling, where a weapon would hang. */
		CHAIN_RACK(1),
		SMOKER(1),
		BARREL(1),
		CAULDRON(1),
		HAY(1),
		BOOKSHELF(1),
		LECTERN(1),
		CANDLE(1),
		CRATE(1),
		PEW(1),
		/** Chiseled stone with a lit candle on it. */
		ALTAR(1),
		SKULL(1),
		GRAVE(1),
		SOUL_LANTERN(1);

		private final int blocks;

		Prop(final int blocks) {
			this.blocks = blocks;
		}

		/** How many blocks of the prop's marker a room holds for each prop. */
		public int blocks() {
			return this.blocks;
		}

		/** Whether a block is this prop's marker. */
		public boolean matches(final BlockState state) {
			return switch (this) {
				case BED -> state.getBlock() instanceof AbstractBedBlock;
				case CRAFTING_TABLE -> state.is(Blocks.CRAFTING_TABLE);
				case COBWEB -> state.is(Blocks.COBWEB);
				case FURNACE -> state.is(Blocks.FURNACE);
				case ANVIL -> state.is(Blocks.ANVIL);
				case SMITHING_TABLE -> state.is(Blocks.SMITHING_TABLE);
				case BLAST_FURNACE -> state.is(Blocks.BLAST_FURNACE);
				case GRINDSTONE -> state.is(Blocks.GRINDSTONE);
				case FLETCHING_TABLE -> state.is(Blocks.FLETCHING_TABLE);
				case CHAIN_RACK -> state.is(Blocks.IRON_CHAIN);
				case SMOKER -> state.is(Blocks.SMOKER);
				case BARREL -> state.is(Blocks.BARREL);
				case CAULDRON -> state.is(Blocks.CAULDRON);
				case HAY -> state.is(Blocks.HAY_BLOCK);
				case BOOKSHELF -> state.is(Blocks.BOOKSHELF);
				case LECTERN -> state.is(Blocks.LECTERN);
				case CANDLE -> state.is(Blocks.CANDLE);
				case CRATE -> state.is(Blocks.SPRUCE_PLANKS);
				case PEW -> state.is(Blocks.OAK_SLAB);
				case ALTAR -> state.is(Blocks.CHISELED_STONE_BRICKS);
				case SKULL -> state.is(Blocks.SKELETON_SKULL);
				case GRAVE -> state.is(Blocks.STONE_BRICK_SLAB);
				case SOUL_LANTERN -> state.is(Blocks.SOUL_LANTERN);
			};
		}
	}

	private final Prop[] recipe;

	LostRoomTheme(final Prop... recipe) {
		this.recipe = recipe;
	}

	/** The props of the theme, in the order they are placed; the same prop twice is two. */
	public List<Prop> recipe() {
		return List.of(this.recipe);
	}

	/** The themes a storey of a kind draws from: all nine, so that nine rooms hold nine different themes. */
	public static List<LostRoomTheme> poolOf(final LostFloorKind kind) {
		return new ArrayList<>(List.of(values()));
	}
}
