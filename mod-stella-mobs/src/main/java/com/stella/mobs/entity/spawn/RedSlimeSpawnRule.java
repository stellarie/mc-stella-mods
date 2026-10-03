package com.stella.mobs.entity.spawn;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.entity.RedSlime;
import com.stella.mobs.entity.StellaEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The red slime rule: a biome holds at most {@link #MAX_PER_BIOME} red slimes. They spawn only in the
 * morning and evening windows, in random loaded chunks near a player, never within 7 blocks of a light source. After one dies, its biome waits
 * {@link #REFILL_TICKS} ticks before it fills again. The slimes are category MISC, so they never count
 * against the mob cap, and their spawn reason is EVENT.
 */
public final class RedSlimeSpawnRule implements SpawnRule {
	public static final RedSlimeSpawnRule INSTANCE = new RedSlimeSpawnRule();
	public static final int MAX_PER_BIOME = 2;
	/** Ten seconds. */
	public static final int REFILL_TICKS = 200;
	/** A slime never spawns this close to a player. */
	public static final double MIN_PLAYER_DISTANCE = 24.0;
	public static final int CHUNK_RADIUS = 2;
	public static final int TRIES_PER_CHUNK = 16;
	public static final long DAY_TICKS = 24000L;
	public static final long MORNING_START = 0L;
	public static final long MORNING_END = 3000L;
	public static final long EVENING_START = 11000L;
	public static final long EVENING_END = 14000L;

	private final Map<ResourceKey<Level>, Map<ResourceKey<Biome>, Long>> cooldowns = new HashMap<>();

	private RedSlimeSpawnRule() {
	}

	static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof RedSlime && entity.level() instanceof ServerLevel level) {
				INSTANCE.noteDeath(level, level.getBiome(entity.blockPosition()));
			}
		});
	}

	/** True in the morning window [0, 3000) and the evening window [11000, 14000) of a day. */
	public static boolean inWindow(final long clockTime) {
		long time = Math.floorMod(clockTime, DAY_TICKS);
		return time >= MORNING_START && time < MORNING_END || time >= EVENING_START && time < EVENING_END;
	}

	@Override
	public String name() {
		return "red_slime";
	}

	@Override
	public void reset() {
		this.cooldowns.clear();
	}

	@Override
	public void cycle(final ServerLevel level, final List<ServerPlayer> players, final long clock) {
		if (level.getDifficulty() == Difficulty.PEACEFUL || !inWindow(level.getOverworldClockTime())) {
			return;
		}
		Set<ChunkPos> near = new HashSet<>();
		for (ServerPlayer player : players) {
			ChunkPos center = player.chunkPosition();
			for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
				for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
					near.add(new ChunkPos(center.x() + dx, center.z() + dz));
				}
			}
		}
		List<ChunkPos> chunks = new ArrayList<>(near);
		Collections.shuffle(chunks, new Random(level.getRandom().nextLong()));
		for (ChunkPos chunk : chunks) {
			trySpawn(level, chunk, clock);
		}
	}

	/** How many loaded red slimes stand in the biome. */
	public static int countInBiome(final ServerLevel level, final Holder<Biome> biome) {
		return level.getEntities(
			StellaEntities.RED_SLIME, slime -> slime.isAlive() && level.getBiome(slime.blockPosition()).is(biome)
		).size();
	}

	/** The spawner clock value at which the biome may fill again. */
	public long cooldownUntil(final ServerLevel level, final Holder<Biome> biome) {
		Map<ResourceKey<Biome>, Long> biomes = this.cooldowns.getOrDefault(level.dimension(), Map.of());
		return biome.unwrapKey().map(key -> biomes.getOrDefault(key, 0L)).orElse(0L);
	}

	private void noteDeath(final ServerLevel level, final Holder<Biome> biome) {
		long until = StellaSpawner.clock(level) + REFILL_TICKS;
		biome.unwrapKey().ifPresent(key ->
			this.cooldowns.computeIfAbsent(level.dimension(), dimension -> new HashMap<>()).put(key, until));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=redSpawner DEATH biome={} refillAt={}", biome.getRegisteredName(), until);
	}

	/** One spawn attempt in a chunk. Returns true when a red slime spawned. */
	public boolean trySpawn(final ServerLevel level, final ChunkPos chunk, final long clock) {
		if (!level.hasChunk(chunk.x(), chunk.z())) {
			return false;
		}
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < TRIES_PER_CHUNK; attempt++) {
			int x = chunk.getMinBlockX() + random.nextInt(16);
			int z = chunk.getMinBlockZ() + random.nextInt(16);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (y <= level.getMinY()
				|| !SpawnPlacements.isSpawnPositionOk(StellaEntities.RED_SLIME, level, pos)
				|| !level.noCollision(StellaEntities.RED_SLIME.getSpawnAABB(x + 0.5, y, z + 0.5))
				|| LightSources.near(level, pos, LightSources.RADIUS)
				|| level.getNearestPlayer(x + 0.5, y, z + 0.5, MIN_PLAYER_DISTANCE, false) != null) {
				continue;
			}
			// One roll per attempt: a retry at another spot would undo the falloff.
			if (!SlimeFalloff.passes(random, x + 0.5, z + 0.5)) {
				return false;
			}
			Holder<Biome> biome = level.getBiome(pos);
			if (clock < cooldownUntil(level, biome) || countInBiome(level, biome) >= MAX_PER_BIOME) {
				return false;
			}
			RedSlime slime = StellaEntities.RED_SLIME.create(level, EntitySpawnReason.EVENT);
			if (slime == null) {
				return false;
			}
			slime.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			slime.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
			if (level.addFreshEntity(slime)) {
				StellaMobs.LOGGER.info("[BEHAVIOR] test=redSpawner SPAWN chunk={} clock={}", chunk, clock);
				return true;
			}
		}
		return false;
	}
}
