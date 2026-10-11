package com.stella.rpg.roll;

import java.util.List;
import java.util.Optional;

/** A non-stat roll. Flat values are in K units. A percent value is stored in tenths of a percent. */
public enum Affix {
	DAMAGE_FLAT("damage_flat", false, Kind.WEAPON),
	DAMAGE_PCT("damage_pct", true, Kind.WEAPON),
	ARMOR_PEN("armor_pen", true, Kind.WEAPON),
	MATK_FLAT("matk_flat", false, Kind.BOOK),
	MATK_PCT("matk_pct", true, Kind.BOOK),
	MAGIC_PEN("magic_pen", true, Kind.BOOK),
	HP_FLAT("hp_flat", false, Kind.ARMOR),
	HP_PCT("hp_pct", true, Kind.ARMOR),
	REDUCTION_FLAT("reduction_flat", false, Kind.ARMOR),
	REDUCTION_PCT("reduction_pct", true, Kind.ARMOR),
	MAGIC_RES_PCT("magic_res_pct", true, Kind.ARMOR),
	/** Rolls only on boots. The pool check lives in {@code WeaponRolls}. */
	MOVE_SPEED_PCT("move_speed_pct", true, Kind.ARMOR),
	/** Skips use damage. Materials and grants carry it, and the random gear roll never picks it. */
	DURABILITY_PCT("durability_pct", true, Kind.WEAPON, Kind.ARMOR);

	public static final int TENTHS = 10;

	public enum Kind {
		WEAPON,
		ARMOR,
		BOOK
	}

	private final String key;
	private final boolean percent;
	private final List<Kind> kinds;

	Affix(final String key, final boolean percent, final Kind... kinds) {
		this.key = key;
		this.percent = percent;
		this.kinds = List.of(kinds);
	}

	public String key() {
		return this.key;
	}

	public boolean percent() {
		return this.percent;
	}

	public boolean rollsOn(final Kind kind) {
		return this.kinds.contains(kind);
	}

	/** Old weapons rolled MATK before only books did. Their rolls still count, and so do the rolls of a staff. */
	public boolean readsOn(final Kind kind) {
		return this.rollsOn(kind) || (kind == Kind.WEAPON && (this == MATK_FLAT || this == MATK_PCT || this == MAGIC_PEN));
	}

	public static List<Affix> pool(final Kind kind) {
		return List.of(values()).stream().filter(affix -> affix.rollsOn(kind)).toList();
	}

	public static Optional<Affix> byKey(final String key) {
		return List.of(values()).stream().filter(affix -> affix.key.equals(key)).findFirst();
	}

	/** The stored value as it reads on an item: K units for a flat affix, a fraction for a percent affix. */
	public double amount(final int stored) {
		return this.percent ? stored / (TENTHS * 100.0) : stored;
	}

	/** The number a tooltip shows: K units, or percent. */
	public double shown(final int stored) {
		return this.percent ? stored / (double) TENTHS : stored;
	}
}
