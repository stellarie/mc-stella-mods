package com.stella.ruins.site.ambushcamp;

/** The marker names of the camp layout. The core and the vault use the framework's own names. */
public final class CampMarkers {
	/** Where a patrol bandit stands, in the yard. */
	public static final String PATROL = "spawn.bandit";
	/** The platform of a lookout tower. */
	public static final String LOOKOUT = "spawn.lookout";
	/** The outer end of the trail, where reinforcements arrive. */
	public static final String TRAIL = "trail";
	public static final String TENT_PREFIX = "tent.";
	public static final String DOOR_PREFIX = "door.";

	private CampMarkers() {
	}

	/** The floor centre of tent {@code id}, numbered from 1. */
	public static String tent(final int id) {
		return TENT_PREFIX + id;
	}

	/** The step in front of the door of tent {@code id}. */
	public static String door(final int id) {
		return DOOR_PREFIX + id;
	}
}
