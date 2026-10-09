package com.stella.spells;

/** The arrow kinds this mod adds. Plain and spectral arrows stay vanilla and are not listed. */
public enum ArrowKind {
	FLINT("flint_arrow", 1.20F),
	IRON("iron_arrow", 1.40F),
	DIAMOND("diamond_arrow", 1.75F),
	KEEN("keen_arrow", 1.15F),
	FIRE("fire_arrow", 1.0F),
	FROST("frost_arrow", 1.0F),
	VENOM("venom_arrow", 1.0F),
	BLAST("blast_arrow", 1.0F);

	private final String path;
	private final float damageFactor;

	ArrowKind(final String path, final float damageFactor) {
		this.path = path;
		this.damageFactor = damageFactor;
	}

	public String path() {
		return this.path;
	}

	/** How much harder this kind hits than a plain arrow. */
	public float damageFactor() {
		return this.damageFactor;
	}
}
