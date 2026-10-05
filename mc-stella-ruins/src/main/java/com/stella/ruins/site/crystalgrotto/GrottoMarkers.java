package com.stella.ruins.site.crystalgrotto;

/** The marker names of the grotto layout. The core and the vault use the framework's own names. */
public final class GrottoMarkers {
	/** The foot of each pillar shaft, in pillar order. */
	public static final String PILLAR = "pillar";
	/** The floor spokes of pillar {@code n}: the marker {@code spoke_n} lists the lantern cells. */
	public static final String SPOKE_PREFIX = "spoke_";
	/** The four cells where a wrong strike may summon Shard Sentinels. */
	public static final String SENTINEL = "sentinel";
	public static final String ENTRANCE = "entrance";

	private GrottoMarkers() {
	}

	public static String spoke(final int pillar) {
		return SPOKE_PREFIX + pillar;
	}
}
