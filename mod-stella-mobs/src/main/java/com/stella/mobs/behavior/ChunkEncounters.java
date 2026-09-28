package com.stella.mobs.behavior;

import com.mojang.serialization.Codec;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.GearConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import org.jspecify.annotations.Nullable;

/** Each 8x8 overworld chunk grid rolls once for one wild Pillager pair. */
public final class ChunkEncounters {
	public static final double PILLAGER_PAIR_CHANCE = 0.01;
	public static final int PILLAGER_PAIR_SIZE = 2;
	public static final int PAIR_GRID_SIZE = 8;
	/** A chunk rolls when a player is this many chunks away: far enough not to pop in beside them, near enough to meet. */
	public static final int MIN_RING = 3;
	public static final int MAX_RING = 5;
	public static final int SCAN_INTERVAL_TICKS = 20;
	/** Marked the elites the safe zone once held; kept so one saved before the change still reads as forced. */
	public static final String CHUNK_ELITE_TAG = "mod-stella-mobs.chunk_elite";
	private static final int PLACEMENT_ATTEMPTS = 8;
	private static final double MIN_PLAYER_DISTANCE = 24.0;
	/** How many sections from a village a pillager pair keeps, as vanilla patrols do. */
	private static final int VILLAGE_SECTIONS = 2;
	private static final AttachmentType<Boolean> ROLLED = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "chunk_encounter_rolled"), Codec.BOOL
	);
	private static final AttachmentType<List<Long>> ROLLED_GRIDS = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "patrol_pair_grids"), Codec.LONG.listOf()
	);

	public enum Encounter { NONE, PILLAGER_PAIR }

	public interface Recorder {
		void rolled(ServerLevel level, ChunkPos pos, Encounter encounter, List<Mob> spawned);
	}

	/** How the scan rolls a chunk it has just marked; the default decides and spawns. */
	public interface Roller {
		void roll(ServerLevel level, LevelChunk chunk, boolean safe);
	}

	private static final Recorder SILENT = (level, pos, encounter, spawned) -> {
	};
	private static final Roller DEFAULT_ROLLER = (level, chunk, safe) -> resolve(level, chunk, safe, chunkRandom(level.getSeed(), chunk.getPos()));
	private static volatile Recorder recorder = SILENT;
	private static volatile Predicate<ServerPlayer> scanFilter = player -> !player.isSpectator();
	private static volatile Roller scanRoller = DEFAULT_ROLLER;

	private ChunkEncounters() {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered chunk encounter attachment {}", ROLLED.identifier());
		StellaMobs.LOGGER.debug("Registered patrol grid attachment {}", ROLLED_GRIDS.identifier());
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static void setScanFilter(final Predicate<ServerPlayer> filter) {
		scanFilter = filter;
	}

	public static void setScanRoller(final Roller roller) {
		scanRoller = roller == null ? DEFAULT_ROLLER : roller;
	}

	public static Roller defaultRoller() {
		return DEFAULT_ROLLER;
	}

	/** The chunk's own roll, fixed by the world seed and its position. */
	public static RandomSource chunkRandom(final long seed, final ChunkPos pos) {
		return RandomSource.create(seed ^ (gridKey(pos) * 0x9E3779B97F4A7C15L));
	}

	public static long gridKey(final ChunkPos pos) {
		return new ChunkPos(Math.floorDiv(pos.x(), PAIR_GRID_SIZE), Math.floorDiv(pos.z(), PAIR_GRID_SIZE)).pack();
	}

	public static Encounter decide(final RandomSource random, final boolean safe) {
		double roll = random.nextDouble();
		// No elite ever spawns in the safe zone.
		if (safe) {
			return Encounter.NONE;
		}
		return roll < PILLAGER_PAIR_CHANCE ? Encounter.PILLAGER_PAIR : Encounter.NONE;
	}

	/** True when the chunk's centre lies inside the spawn safe zone, which only the overworld has. */
	public static boolean isSafe(final ResourceKey<Level> dimension, final ChunkPos pos, final double spawnX, final double spawnZ, final double radius) {
		if (radius <= 0.0 || !Level.OVERWORLD.equals(dimension)) {
			return false;
		}

		double dx = pos.getMiddleBlockX() - spawnX;
		double dz = pos.getMiddleBlockZ() - spawnZ;
		return dx * dx + dz * dz <= radius * radius;
	}

	public static boolean isRolled(final LevelChunk chunk) {
		return Boolean.TRUE.equals(chunk.getAttached(ROLLED));
	}

	/** Clears the chunk's roll, so it rolls again; for tests and operators. */
	public static void forget(final LevelChunk chunk) {
		chunk.removeAttached(ROLLED);
		ServerLevel level = (ServerLevel) chunk.getLevel();
		List<Long> current = ((AttachmentTarget) level).getAttached(ROLLED_GRIDS);
		List<Long> remaining = new ArrayList<>(current == null ? List.of() : current);
		remaining.remove(gridKey(chunk.getPos()));
		((AttachmentTarget) level).setAttached(ROLLED_GRIDS, List.copyOf(remaining));
	}

	/** Rolls a chunk once and spawns what it rolled; empty when the chunk has rolled before. */
	public static Optional<Encounter> roll(final ServerLevel level, final LevelChunk chunk, final boolean safe, final RandomSource random) {
		if (!markRolled(chunk) || !markGridRolled(level, chunk.getPos())) {
			return Optional.empty();
		}
		return Optional.of(resolve(level, chunk, safe, random));
	}

	/** Every SCAN_INTERVAL_TICKS, the loaded overworld chunks in each player's ring roll, each once. */
	public static void tick(final ServerLevel level) {
		if (!Level.OVERWORLD.equals(level.dimension()) || level.getServer().getTickCount() % SCAN_INTERVAL_TICKS != 0) {
			return;
		}

		BlockPos spawn = level.getRespawnData().pos();
		double radius = GearConfigStore.get().spawnSafetyRadius();
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (!scanFilter.test(player)) {
				continue;
			}

			ChunkPos center = player.chunkPosition();
			for (int dx = -MAX_RING; dx <= MAX_RING; dx++) {
				for (int dz = -MAX_RING; dz <= MAX_RING; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) < MIN_RING) {
						continue;
					}

					LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz);
					if (chunk != null && markRolled(chunk)) {
						boolean safe = isSafe(level.dimension(), chunk.getPos(), spawn.getX(), spawn.getZ(), radius);
						if (markGridRolled(level, chunk.getPos())) {
							scanRoller.roll(level, chunk, safe);
						}
						// Round 8: the same first sight of a chunk rolls the ocean's skeleton bands, by 2x2 area.
						OceanBand.rollRegion(level, chunk, safe);
					}
				}
			}
		}
	}

	public static List<Mob> spawn(final ServerLevel level, final LevelChunk chunk, final Encounter encounter, final RandomSource random) {
		return switch (encounter) {
			case NONE -> List.of();
			case PILLAGER_PAIR -> spawnPillagerPair(level, chunk, random);
		};
	}

	private static boolean markRolled(final LevelChunk chunk) {
		if (isRolled(chunk)) {
			return false;
		}
		chunk.setAttached(ROLLED, Boolean.TRUE);
		return true;
	}

	private static boolean markGridRolled(final ServerLevel level, final ChunkPos pos) {
		long key = gridKey(pos);
		List<Long> stored = ((AttachmentTarget) level).getAttached(ROLLED_GRIDS);
		List<Long> rolled = stored == null ? List.of() : stored;
		if (rolled.contains(key)) {
			return false;
		}
		List<Long> updated = new ArrayList<>(rolled);
		updated.add(key);
		((AttachmentTarget) level).setAttached(ROLLED_GRIDS, List.copyOf(updated));
		return true;
	}

	private static Encounter resolve(final ServerLevel level, final LevelChunk chunk, final boolean safe, final RandomSource random) {
		Encounter encounter = decide(random, safe);
		List<Mob> spawned = spawn(level, chunk, encounter, random);
		if (encounter != Encounter.NONE) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=chunkEncounters ROLL chunk={} safe={} encounter={} spawned={}",
				chunk.getPos(), safe, encounter, spawned.size()
			);
		}
		recorder.rolled(level, chunk.getPos(), encounter, spawned);
		return encounter;
	}

	// A leader without the ominous banner walks the patrol, so killing it gives no Bad Omen. The pair is a
	// friendly village patrol on its rounds.
	private static List<Mob> spawnPillagerPair(final ServerLevel level, final LevelChunk chunk, final RandomSource random) {
		List<Mob> spawned = new ArrayList<>();
		BlockPos near = null;
		for (int i = 0; i < PILLAGER_PAIR_SIZE; i++) {
			Pillager pillager = EntityTypes.PILLAGER.create(level, EntitySpawnReason.PATROL);
			if (pillager == null) {
				continue;
			}

			BlockPos at = findSpot(level, chunk, pillager, random, near, spot -> mayPatrolAt(level, spot));
			if (at == null) {
				continue;
			}

			near = at;
			pillager.snapTo(Vec3.atBottomCenterOf(at), random.nextFloat() * 360.0F, 0.0F);
			pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.PATROL, null);
			pillager.setPatrolLeader(spawned.isEmpty());
			pillager.findPatrolTarget();
			FriendlyPatrol.befriend(pillager);
			pillager.setPersistenceRequired();
			if (level.addFreshEntity(pillager)) {
				spawned.add(pillager);
			}
		}
		return List.copyOf(spawned);
	}

	// A dry, open spot on the chunk's surface, away from every player; next to the first spot when one is given.
	private static @Nullable BlockPos findSpot(
		final ServerLevel level, final LevelChunk chunk, final Mob mob, final RandomSource random, final @Nullable BlockPos near,
		final Predicate<BlockPos> allowed
	) {
		ChunkPos pos = chunk.getPos();
		for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
			int x = near == null ? pos.getMinBlockX() + random.nextInt(16) : near.getX() + random.nextInt(5) - 2;
			int z = near == null ? pos.getMinBlockZ() + random.nextInt(16) : near.getZ() + random.nextInt(5) - 2;
			if (x < pos.getMinBlockX() || x > pos.getMaxBlockX() || z < pos.getMinBlockZ() || z > pos.getMaxBlockZ()) {
				continue;
			}

			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			BlockPos below = at.below();
			Vec3 feet = Vec3.atBottomCenterOf(at);
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
				&& level.getFluidState(at).isEmpty() && level.getFluidState(below).isEmpty()
				&& level.noCollision(mob, mob.getBoundingBox().move(feet.subtract(mob.position())))
				&& farFromPlayers(level, feet)
				&& allowed.test(at)) {
				return at;
			}
		}
		return null;
	}

	// Vanilla's own patrol rules: never in or beside a village, and only where the biome lets patrols spawn.
	// The biome's own value is read, because the level's value also follows vanilla's day-only timeline.
	private static boolean mayPatrolAt(final ServerLevel level, final BlockPos at) {
		return !level.isCloseToVillage(at, VILLAGE_SECTIONS)
			&& level.getBiome(at).value().getAttributes().applyModifier(
				EnvironmentAttributes.CAN_PILLAGER_PATROL_SPAWN, EnvironmentAttributes.CAN_PILLAGER_PATROL_SPAWN.defaultValue()
			);
	}

	private static boolean farFromPlayers(final ServerLevel level, final Vec3 at) {
		for (ServerPlayer player : level.players()) {
			if (scanFilter.test(player) && player.position().distanceToSqr(at) < MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE) {
				return false;
			}
		}
		return true;
	}
}
