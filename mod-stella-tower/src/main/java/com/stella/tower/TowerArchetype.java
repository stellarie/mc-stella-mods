package com.stella.tower;

import java.util.List;

/**
 * The kinds of floor. An ordinary floor is a labyrinth, a great hall or a crystal cavern (see
 * {@link TowerArchetypes}); every fifth floor is a {@link #HALL}, the maze round an elite's or a boss's hall.
 * <ul>
 * <li>HALL: one story under a lid, the hall in its middle, as elite and boss floors always were.</li>
 * <li>LABYRINTH: two mazes of paths six wide, one above the other, joined by a stair.</li>
 * <li>GREAT_HALL: a nave open to the roof, aisles under a gallery story with railed balconies, the portal in the gallery's apse.</li>
 * <li>CRYSTAL_CAVERN: two or three cave stories, one above another, joined by ramps.</li>
 * <li>BIOME: one open volume of ground, water and growth, drawn by {@link BiomeGen}; the floors of the tower now.</li>
 * </ul>
 */
public enum TowerArchetype {
	HALL,
	LABYRINTH,
	GREAT_HALL,
	CRYSTAL_CAVERN,
	BIOME;

	/** The archetypes an ordinary floor draws among. */
	public static final List<TowerArchetype> ORDINARY = List.of(LABYRINTH, GREAT_HALL, CRYSTAL_CAVERN);

	/** What a floor of this archetype is made of, by its band of ten floors. */
	public TowerTheme theme(final int floor) {
		return switch (this) {
			case GREAT_HALL -> TowerTheme.greatHall(floor);
			case CRYSTAL_CAVERN -> TowerTheme.cavern(floor);
			default -> TowerTheme.maze(floor);
		};
	}
}
