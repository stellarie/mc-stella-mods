package com.stella.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * The five surfaces a lost castle is built from, chosen by its seed. Every castle is the same octagonal
 * maze of storeys ({@link TowerHex}); a variant names the tower environment its stone comes from, so one
 * castle reads as the furnace's, the frost's, the void's, the shadow's or the bog's.
 */
public enum LostCastleVariant {
	/** The castle of the furnace: blackstone, burnt through. */
	CASTLE,
	/** The castle of the frost: deepslate under a crown of ice. */
	KEEP,
	/** The castle of the void: end stone, where nothing grows. */
	MAZE,
	/** The castle of the shadow: blackstone the dark has taken back. */
	FLOODED,
	/** The castle of the bog: mud brick, wet through. */
	CRYPT;

	/** The castle of the furnace: blackstone, burnt through. */
	public static final LostCastlePalette INFERNO = new LostCastlePalette(TrapTheme.INFERNO, Blocks.POLISHED_BLACKSTONE, Blocks.BASALT, Blocks.BLACKSTONE);
	/** The castle of the frost: deepslate under a crown of ice. */
	public static final LostCastlePalette FROST = new LostCastlePalette(TrapTheme.FROST, Blocks.DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE, Blocks.BLUE_ICE);
	/** The castle of the void: end stone, where nothing grows. */
	public static final LostCastlePalette VOID = new LostCastlePalette(TrapTheme.VOID, Blocks.END_STONE_BRICKS, Blocks.END_STONE, Blocks.PURPUR_BLOCK);
	/** The castle of the shadow: blackstone the dark has taken back. */
	public static final LostCastlePalette SHADOW = new LostCastlePalette(TrapTheme.SHADOW, Blocks.BLACKSTONE, Blocks.DEEPSLATE, Blocks.SCULK);
	/** The castle of the bog: mud brick, wet through. */
	public static final LostCastlePalette BOG = new LostCastlePalette(TrapTheme.BOG, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MOSS_BLOCK);

	/** The surface this variant is built from. */
	public LostCastlePalette palette() {
		return switch (this) {
			case CASTLE -> INFERNO;
			case KEEP -> FROST;
			case MAZE -> VOID;
			case FLOODED -> SHADOW;
			case CRYPT -> BOG;
		};
	}

	/** The mob this environment adds to its spawner tables: the husk of the furnace, the stray of the frost, and so on. */
	public String flavour() {
		return switch (this) {
			case CASTLE -> "minecraft:husk";
			case KEEP -> "minecraft:stray";
			case MAZE -> "minecraft:spider";
			case FLOODED -> "minecraft:cave_spider";
			case CRYPT -> "minecraft:zombie";
		};
	}

	/**
	 * What is fixed before building: the portal's lower block, the court (x and z, low and high), the lowest
	 * layer from the floor, and the variant's own numbers.
	 */
	public record Layout(int[] portal, int[] yard, int lowest, int[] numbers) {
	}

	/**
	 * The court the roaming mobs keep to: the ground storey's front way, x and z inclusive.
	 */
	public static int[] court() {
		int lowX = TowerHex.cellX(TowerHex.COLS / 2) + 1;
		int lowZ = TowerHex.cellZ(0) + 1;
		return new int[] {lowX, lowZ, lowX + TowerMaze.PATH - 3, lowZ + TowerMaze.PATH - 3};
	}

	/** The variant a castle of this seed takes. */
	public static LostCastleVariant of(final long seed) {
		long mixed = seed * 0x9E3779B97F4A7C15L;
		mixed ^= mixed >>> 29;
		return values()[(int) Math.floorMod(mixed, (long) values().length)];
	}

	/**
	 * The castle's fixed spots: its court on the ground storey's front way, and the lost room's portal on the
	 * middle of the top storey's middle way, entered from its lower z.
	 */
	public Layout layout(final long seed) {
		BlockPos portal = LostCastleHex.portalSpot();
		return new Layout(new int[] {portal.getX(), portal.getY(), portal.getZ()}, court(), 0, new int[0]);
	}
}
