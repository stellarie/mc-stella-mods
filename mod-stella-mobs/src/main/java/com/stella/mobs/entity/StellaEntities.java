package com.stella.mobs.entity;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.entity.spawn.StellaSpawner;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Every entity type this mod adds. The types are new registry entries, so a client without this mod
 * cannot join a server that has it.
 */
public final class StellaEntities {
	/** The vanilla slime box: 0.52 blocks a size step, scaled by the slime size at runtime. */
	private static final float SLIME_BOX = 0.52F;
	private static final float SLIME_EYE_HEIGHT = 0.325F;
	private static final float SLIME_SPAWN_SCALE = 4.0F;
	private static final int SLIME_TRACKING_RANGE = 10;
	/** The spawn-list weight of the white and the black slime. A zombie is 95, so both are rare. */
	public static final int SLIME_SPAWN_WEIGHT = 8;
	public static final int SLIME_SPAWN_MIN_GROUP = 1;
	public static final int SLIME_SPAWN_MAX_GROUP = 1;

	public static final EntityType<BlueSlime> BLUE_SLIME = slime("blue_slime", MobCategory.MISC, BlueSlime::new);
	public static final EntityType<WhiteSlime> WHITE_SLIME = slime("white_slime", MobCategory.MONSTER, WhiteSlime::new);
	public static final EntityType<BlackSlime> BLACK_SLIME = slime("black_slime", MobCategory.MONSTER, BlackSlime::new);

	public static final EntityType<SkeletonWorker> SKELETON_WORKER = mob(
		"skeleton_worker", MobCategory.MONSTER, SkeletonWorker::new, 0.6F, 1.99F, 8, true
	);
	public static final EntityType<Ghost> GHOST = mob("ghost", MobCategory.MISC, Ghost::new, 0.6F, 1.8F, 10, false);

	private StellaEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(BLUE_SLIME, Monster.createMonsterAttributes());
		FabricDefaultAttributeRegistry.register(WHITE_SLIME, Monster.createMonsterAttributes());
		FabricDefaultAttributeRegistry.register(BLACK_SLIME, Monster.createMonsterAttributes());
		FabricDefaultAttributeRegistry.register(SKELETON_WORKER, AbstractSkeleton.createAttributes());
		FabricDefaultAttributeRegistry.register(GHOST, Ghost.createAttributes());

		// The blue slime has no vanilla spawn rule: only the custom spawner places it.
		SpawnPlacements.register(
			BLUE_SLIME, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules
		);
		SpawnPlacements.register(
			WHITE_SLIME, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			StellaEntities::checkHostileSlimeSpawn
		);
		SpawnPlacements.register(
			BLACK_SLIME, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			StellaEntities::checkHostileSlimeSpawn
		);

		// Only the custom spawner places the worker and the ghost, so the placement rule stays plain.
		SpawnPlacements.register(
			SKELETON_WORKER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules
		);
		SpawnPlacements.register(
			GHOST, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules
		);

		// Normal mob rules: the natural spawner picks these from the biome list, like any other hostile.
		for (EntityType<? extends Slime> type : java.util.List.of(WHITE_SLIME, BLACK_SLIME)) {
			BiomeModifications.addSpawn(
				BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, type,
				SLIME_SPAWN_WEIGHT, SLIME_SPAWN_MIN_GROUP, SLIME_SPAWN_MAX_GROUP
			);
		}

		WhiteSlime.register();
		BlackSlime.register();
		SkeletonWorker.register();
		StellaSpawner.register();
		StellaMobs.LOGGER.info("Registered the stella mobs");
	}

	/** The hostile rule: not in peaceful, dark enough, and a legal spot. It is the zombie rule without the Monster type. */
	private static <T extends Mob> boolean checkHostileSlimeSpawn(
		final EntityType<T> type,
		final ServerLevelAccessor level,
		final EntitySpawnReason reason,
		final BlockPos pos,
		final RandomSource random
	) {
		return level.getDifficulty() != Difficulty.PEACEFUL
			&& (EntitySpawnReason.ignoresLightRequirements(reason) || Monster.isDarkEnoughToSpawn(level, pos, random))
			&& Mob.checkMobSpawnRules(type, level, reason, pos, random);
	}

	private static <T extends Slime> EntityType<T> slime(
		final String path, final MobCategory category, final EntityType.EntityFactory<T> factory
	) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, path)
		);
		EntityType.Builder<T> builder = EntityType.Builder.of(factory, category)
			.sized(SLIME_BOX, SLIME_BOX)
			.eyeHeight(SLIME_EYE_HEIGHT)
			.spawnDimensionsScale(SLIME_SPAWN_SCALE)
			.clientTrackingRange(SLIME_TRACKING_RANGE);
		// The blue slime is a beginner mob and stays in peaceful. The other two are hostile like any slime.
		if (category == MobCategory.MONSTER) {
			builder = builder.notInPeaceful();
		}
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static <T extends Mob> EntityType<T> mob(
		final String path,
		final MobCategory category,
		final EntityType.EntityFactory<T> factory,
		final float width,
		final float height,
		final int trackingRange,
		final boolean hostile
	) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, path)
		);
		EntityType.Builder<T> builder = EntityType.Builder.of(factory, category)
			.sized(width, height)
			.clientTrackingRange(trackingRange);
		if (hostile) {
			builder = builder.notInPeaceful();
		} else {
			// The ghost floats in lava caves, so fire must not end it.
			builder = builder.fireImmune();
		}
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
