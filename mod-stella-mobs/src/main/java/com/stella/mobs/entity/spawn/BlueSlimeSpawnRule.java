package com.stella.mobs.entity.spawn;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.entity.BlueSlime;
import com.stella.mobs.entity.StellaEntities;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The blue slime rule: a biome holds at most {@link #MAX_PER_BIOME} natural blue slimes, placed at
 * light level 15 and never within 7 blocks of a light source, and a chunk waits {@link #REFILL_TICKS} ticks after one dies before it fills again.
 * The count covers loaded slimes only, and a white slime's escorts are not counted. The slimes are
 * category MISC, so they never count against the mob cap, and their spawn reason is EVENT.
 */
public final class BlueSlimeSpawnRule implements SpawnRule {
	public static final BlueSlimeSpawnRule INSTANCE = new BlueSlimeSpawnRule();
	public static final int MAX_PER_BIOME = 1;
	public static final int LIGHT_LEVEL = 15;
	/** Ten seconds. */
	public static final int REFILL_TICKS = 200;
	/** A slime never spawns this close to a player. */
	public static final double MIN_PLAYER_DISTANCE = 24.0;
	/** The chunks around a player that the rule fills, on each side. */
	public static final int CHUNK_RADIUS = 2;
	public static final int TRIES_PER_SLOT = 16;
	private static final int PRUNE_AFTER_TICKS = 1200;

	private final Map<ResourceKey<Level>, Map<Long, ChunkState>> states = new HashMap<>();

	private BlueSlimeSpawnRule() {
	}

	private static final class ChunkState {
		private long cooldownUntil;
		private long lastSeen;
	}

	static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof BlueSlime && entity.level() instanceof ServerLevel level) {
				INSTANCE.noteDeath(level, entity.chunkPosition());
			}
		});
	}

	@Override
	public String name() {
		return "blue_slime";
	}

	@Override
	public void reset() {
		this.states.clear();
	}

	@Override
	public void cycle(final ServerLevel level, final List<ServerPlayer> players, final long clock) {
		Set<ChunkPos> chunks = new HashSet<>();
		for (ServerPlayer player : players) {
			ChunkPos center = player.chunkPosition();
			for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
				for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
					chunks.add(new ChunkPos(center.x() + dx, center.z() + dz));
				}
			}
		}

		for (ChunkPos chunk : chunks) {
			fillChunk(level, chunk, clock);
		}
		prune(level, clock);
	}

	/** The light a spawn is judged by: block light or daylight, so night outside is too dark. */
	public static int lightAt(final ServerLevel level, final BlockPos pos) {
		return level.getMaxLocalRawBrightness(pos);
	}

	/** Fills one chunk up to the cap, unless it is waiting out a refill. Returns how many it spawned. */
	public int fillChunk(final ServerLevel level, final ChunkPos chunk, final long clock) {
		if (!level.hasChunk(chunk.x(), chunk.z())) {
			return 0;
		}

		ChunkState state = state(level, chunk);
		state.lastSeen = clock;
		if (clock < state.cooldownUntil) {
			return 0;
		}

		int spawned = 0;
		for (int slot = 0; slot < MAX_PER_BIOME; slot++) {
			if (!trySpawn(level, chunk)) {
				break;
			}
			spawned++;
		}

		if (spawned > 0) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=blueSpawner SPAWN chunk={} spawned={} clock={}", chunk, spawned, clock
			);
		}
		return spawned;
	}

	/** How many loaded natural blue slimes stand in the biome. */
	public static int countInBiome(final ServerLevel level, final Holder<Biome> biome) {
		return level.getEntities(
			StellaEntities.BLUE_SLIME,
			slime -> slime.isAlive() && !slime.isEscort() && level.getBiome(slime.blockPosition()).is(biome)
		).size();
	}

	/** The clock value at which the chunk may fill again. */
	public long cooldownUntil(final ServerLevel level, final ChunkPos chunk) {
		return state(level, chunk).cooldownUntil;
	}

	private void noteDeath(final ServerLevel level, final ChunkPos chunk) {
		long until = StellaSpawner.clock(level) + REFILL_TICKS;
		state(level, chunk).cooldownUntil = until;
		StellaMobs.LOGGER.info("[BEHAVIOR] test=blueSpawner DEATH chunk={} refillAt={}", chunk, until);
	}

	private boolean trySpawn(final ServerLevel level, final ChunkPos chunk) {
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < TRIES_PER_SLOT; attempt++) {
			int x = chunk.getMinBlockX() + random.nextInt(16);
			int z = chunk.getMinBlockZ() + random.nextInt(16);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);

			if (y <= level.getMinY() || lightAt(level, pos) != LIGHT_LEVEL
				|| !SpawnPlacements.isSpawnPositionOk(StellaEntities.BLUE_SLIME, level, pos)
				|| !level.noCollision(StellaEntities.BLUE_SLIME.getSpawnAABB(x + 0.5, y, z + 0.5))
				|| LightSources.near(level, pos, LightSources.RADIUS)
				|| level.getNearestPlayer(x + 0.5, y, z + 0.5, MIN_PLAYER_DISTANCE, false) != null) {
				continue;
			}
			// One roll per attempt: a retry at another spot would undo the falloff.
			if (!SlimeFalloff.passes(random, x + 0.5, z + 0.5)) {
				return false;
			}
			if (countInBiome(level, level.getBiome(pos)) >= MAX_PER_BIOME) {
				return false;
			}

			BlueSlime slime = StellaEntities.BLUE_SLIME.create(level, EntitySpawnReason.EVENT);
			if (slime == null) {
				return false;
			}
			slime.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			slime.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
			slime.addTag(BlueSlime.HARMLESS_TAG);
			if (level.addFreshEntity(slime)) {
				return true;
			}
		}
		return false;
	}

	private ChunkState state(final ServerLevel level, final ChunkPos chunk) {
		return this.states
			.computeIfAbsent(level.dimension(), key -> new HashMap<>())
			.computeIfAbsent(chunk.pack(), key -> new ChunkState());
	}

	private void prune(final ServerLevel level, final long clock) {
		Map<Long, ChunkState> chunks = this.states.get(level.dimension());
		if (chunks != null) {
			chunks.values().removeIf(state -> clock - state.lastSeen > PRUNE_AFTER_TICKS && clock >= state.cooldownUntil);
		}
	}
}
