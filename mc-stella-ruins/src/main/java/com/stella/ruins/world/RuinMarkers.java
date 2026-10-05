package com.stella.ruins.world;

/** The marker names the library itself reads. A layout may add any others. */
public final class RuinMarkers {
	/** Set by {@link LayoutContext#placeCore}. */
	public static final String CORE = "core";
	/** Set by {@link LayoutContext#placeVault}. */
	public static final String VAULT = "vault";
	/** The block a roster mob stands in when it spawns. A site names as many as it likes. */
	public static final String SPAWN = "spawn";

	private RuinMarkers() {
	}
}
