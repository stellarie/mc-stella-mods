package com.stella.tower;

import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/**
 * The kinds of mob each biome raises. A biome's pool replaces {@link TowerMobs#pool(int)} for its floor: a floor of the
 * basalt deltas raises blazes and hoglins from the first floor on, whatever its number. A great hall raises the pool of
 * the biome below it. Only kinds the tower or the game already has.
 */
public final class BiomePools {
	private static final List<EntityType<? extends Mob>> RAINY_FOREST = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER,
		EntityTypes.CREEPER, EntityTypes.WITCH, EntityTypes.VINDICATOR, EntityTypes.BOGGED);
	private static final List<EntityType<? extends Mob>> DESTROYED_VILLAGE = List.of(EntityTypes.ZOMBIE, EntityTypes.ZOMBIE_VILLAGER,
		EntityTypes.HUSK, EntityTypes.SKELETON, EntityTypes.PILLAGER, EntityTypes.VINDICATOR, EntityTypes.WITCH);
	private static final List<EntityType<? extends Mob>> DESTROYED_CASTLE = List.of(EntityTypes.SKELETON, EntityTypes.WITHER_SKELETON,
		EntityTypes.VINDICATOR, EntityTypes.PILLAGER, EntityTypes.ZOMBIE, EntityTypes.WITCH);
	private static final List<EntityType<? extends Mob>> ADVENTURERS_GRAVEYARD = List.of(EntityTypes.SKELETON, EntityTypes.WITHER_SKELETON,
		EntityTypes.ZOMBIE, EntityTypes.STRAY, EntityTypes.VINDICATOR, EntityTypes.WITCH);
	private static final List<EntityType<? extends Mob>> SCORCHED_BATTLEFIELD = List.of(EntityTypes.SKELETON, EntityTypes.WITHER_SKELETON,
		EntityTypes.ZOMBIE, EntityTypes.VINDICATOR, EntityTypes.BLAZE, EntityTypes.PIGLIN_BRUTE, EntityTypes.PILLAGER);
	private static final List<EntityType<? extends Mob>> JUNGLE = List.of(EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER, EntityTypes.CREEPER,
		EntityTypes.VINDICATOR, EntityTypes.PILLAGER, EntityTypes.BOGGED, EntityTypes.ZOMBIE);
	private static final List<EntityType<? extends Mob>> MISTBOUND_MARSH = List.of(EntityTypes.BOGGED, EntityTypes.DROWNED, EntityTypes.ZOMBIE,
		EntityTypes.WITCH, EntityTypes.SLIME, EntityTypes.SPIDER, EntityTypes.SKELETON, EntityTypes.CREEPER);
	private static final List<EntityType<? extends Mob>> BASALT_DELTAS = List.of(EntityTypes.BLAZE, EntityTypes.MAGMA_CUBE,
		EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE, EntityTypes.HOGLIN, EntityTypes.SKELETON);
	private static final List<EntityType<? extends Mob>> ETERNAL_SNOWSTORM = List.of(EntityTypes.STRAY, EntityTypes.SKELETON, EntityTypes.ZOMBIE,
		EntityTypes.VINDICATOR, EntityTypes.CREEPER, EntityTypes.SPIDER);
	private static final List<EntityType<? extends Mob>> DESERT_STORM = List.of(EntityTypes.HUSK, EntityTypes.SKELETON, EntityTypes.SPIDER,
		EntityTypes.CREEPER, EntityTypes.PILLAGER, EntityTypes.VINDICATOR);
	private static final List<EntityType<? extends Mob>> AMETHYST_CAVE = List.of(EntityTypes.CAVE_SPIDER, EntityTypes.SPIDER, EntityTypes.SKELETON,
		EntityTypes.ENDERMITE, EntityTypes.CREEPER, EntityTypes.ZOMBIE);
	private static final List<EntityType<? extends Mob>> SUNKEN_CATACOMBS = List.of(EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.DROWNED,
		EntityTypes.CAVE_SPIDER, EntityTypes.STRAY, EntityTypes.ENDERMITE);

	private BiomePools() {
	}

	/** The kinds a biome raises. A great hall has none of its own: see {@link TowerMobs#poolOf}. */
	public static List<EntityType<? extends Mob>> of(final FloorBiome biome) {
		return switch (biome) {
			case RAINY_FOREST -> RAINY_FOREST;
			case DESTROYED_VILLAGE -> DESTROYED_VILLAGE;
			case DESTROYED_CASTLE -> DESTROYED_CASTLE;
			case ADVENTURERS_GRAVEYARD -> ADVENTURERS_GRAVEYARD;
			case SCORCHED_BATTLEFIELD -> SCORCHED_BATTLEFIELD;
			case JUNGLE -> JUNGLE;
			case MISTBOUND_MARSH -> MISTBOUND_MARSH;
			case BASALT_DELTAS -> BASALT_DELTAS;
			case ETERNAL_SNOWSTORM -> ETERNAL_SNOWSTORM;
			case DESERT_STORM -> DESERT_STORM;
			case AMETHYST_CAVE -> AMETHYST_CAVE;
			case SUNKEN_CATACOMBS -> SUNKEN_CATACOMBS;
			case GREAT_HALL -> RAINY_FOREST;
		};
	}
}
