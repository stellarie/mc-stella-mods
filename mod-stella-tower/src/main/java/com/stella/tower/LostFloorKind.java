package com.stella.tower;

/**
 * What one storey of a lost castle is: a barracks, a store, a library, a crypt, a chapel or the sanctum.
 * The kind fixes which storeys it may stand on and how many chest sites the storey is given.
 */
public enum LostFloorKind {
	MUSTER(Slot.GROUND, 2, 0),
	STOREROOM(Slot.GROUND, 0, 1),
	LIBRARY(Slot.MIDDLE, 1, 1),
	CRYPT(Slot.MIDDLE, 1, 1),
	CHAPEL(Slot.MIDDLE, 1, 1),
	SANCTUM(Slot.TOP, 0, 1);

	/** Where in the castle a kind may stand. */
	public enum Slot {
		GROUND,
		MIDDLE,
		TOP
	}

	private final Slot slot;
	private final int singles;
	private final int doubles;

	LostFloorKind(final Slot slot, final int singles, final int doubles) {
		this.slot = slot;
		this.singles = singles;
		this.doubles = doubles;
	}

	public Slot slot() {
		return this.slot;
	}

	/** The single chests and the double chests the storey is given, not counting the top storey's workstation chest. */
	public int singles() {
		return this.singles;
	}

	public int doubles() {
		return this.doubles;
	}

	/** The chest blocks the storey holds: a double is two, and the top storey adds the workstation chest. */
	public int chestBlocks() {
		return this.singles + 2 * this.doubles + (this.slot == Slot.TOP ? 1 : 0);
	}

	/** The kinds that may stand on a slot. */
	public static LostFloorKind[] of(final Slot slot) {
		return switch (slot) {
			case GROUND -> new LostFloorKind[] {MUSTER, STOREROOM};
			case MIDDLE -> new LostFloorKind[] {LIBRARY, CRYPT, CHAPEL};
			case TOP -> new LostFloorKind[] {SANCTUM};
		};
	}
}
