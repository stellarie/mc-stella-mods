package com.stella.tower;

/**
 * What one floor of a lost castle is: a muster hall, a storeroom, a library, a crypt, a chapel or the sanctum.
 * The kind fixes which floors it may stand on, and which mobs the floor's spawners raise.
 */
public enum LostFloorKind {
	MUSTER(Slot.TOP),
	STOREROOM(Slot.TOP),
	LIBRARY(Slot.MIDDLE),
	CRYPT(Slot.MIDDLE),
	CHAPEL(Slot.MIDDLE),
	SANCTUM(Slot.BOTTOM);

	/** Where in the castle a kind may stand. */
	public enum Slot {
		TOP,
		MIDDLE,
		BOTTOM
	}

	private final Slot slot;

	LostFloorKind(final Slot slot) {
		this.slot = slot;
	}

	public Slot slot() {
		return this.slot;
	}

	/** The kinds that may stand on a slot. */
	public static LostFloorKind[] of(final Slot slot) {
		return switch (slot) {
			case TOP -> new LostFloorKind[] {MUSTER, STOREROOM};
			case MIDDLE -> new LostFloorKind[] {LIBRARY, CRYPT, CHAPEL};
			case BOTTOM -> new LostFloorKind[] {SANCTUM};
		};
	}
}
