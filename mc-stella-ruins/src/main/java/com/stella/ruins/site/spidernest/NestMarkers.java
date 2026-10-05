package com.stella.ruins.site.spidernest;

/** The marker names of the nest layout. The core, the vault, and the weaver spawns use the framework's own names. */
public final class NestMarkers {
	/** One for each egg sac, in sac order. A sac that was broken grows back here. */
	public static final String SAC = "sac";
	/** Where the Brood Crawlers appear when the nest hatches. */
	public static final String CRAWLER = "spawn_crawler";
	/** The centre of the brood pit, where the Nest Matriarch rises. */
	public static final String MATRIARCH = "spawn_matriarch";
	/** The first floor cell behind the web plug. */
	public static final String ENTRANCE = "entrance";

	private NestMarkers() {
	}
}
