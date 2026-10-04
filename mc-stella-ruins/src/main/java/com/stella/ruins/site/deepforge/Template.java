package com.stella.ruins.site.deepforge;

/**
 * The six chamber kinds. Every spot is in the chamber's own frame: {@code a} runs from the door wall inward
 * (1 to 9 inside) and {@code b} runs sideways (-5 to 5 inside).
 */
enum Template {
	BARRACKS("barracks", 8, 0, 5, 5, LeverWall.SIDE_POSITIVE, 4, -4),
	ORE_STORE("ore_store", 9, 0, 8, 5, LeverWall.SIDE_POSITIVE, 9, 3),
	BELLOWS("bellows", 9, 0, 8, 5, LeverWall.SIDE_POSITIVE, 2, -1),
	MINE("mine", 9, 0, 8, -5, LeverWall.SIDE_NEGATIVE, 8, -3),
	LAVA("lava", 8, 0, 8, 5, LeverWall.SIDE_POSITIVE, 3, 0),
	ARMORY("armory", 9, 0, 9, 4, LeverWall.BACK, 3, 3);

	/** Which wall a lever hangs on. */
	enum LeverWall {
		SIDE_POSITIVE,
		SIDE_NEGATIVE,
		BACK
	}

	final String id;
	final int holderA;
	final int holderB;
	final int leverA;
	final int leverB;
	final LeverWall leverWall;
	final int impA;
	final int impB;

	Template(final String id, final int holderA, final int holderB, final int leverA, final int leverB, final LeverWall leverWall, final int impA, final int impB) {
		this.id = id;
		this.holderA = holderA;
		this.holderB = holderB;
		this.leverA = leverA;
		this.leverB = leverB;
		this.leverWall = leverWall;
		this.impA = impA;
		this.impB = impB;
	}
}
