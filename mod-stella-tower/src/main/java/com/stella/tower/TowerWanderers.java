package com.stella.tower;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Once the tower is open, its mobs get out. Each 4 by 4 chunk area of the overworld, the nether and the
 * end rolls once, when a player first comes near it: one in a hundred raises a tower mob of the highest
 * floor anyone has reached, the same mob in every way as on that floor.
 */
public final class TowerWanderers {
	public static final double CHANCE = 0.01;
	/** An area is this many chunks on a side; the chunk at its low corner holds its roll. */
	public static final int REGION_CHUNKS = 4;
	/** An area rolls when a player is this many chunks away: far enough not to pop in beside them. */
	public static final int MIN_RING = 3;
	public static final int MAX_RING = 5;
	public static final int SCAN_TICKS = 20;
	public static final int PLACE_ATTEMPTS = 16;
	public static final List<ResourceKey<Level>> WORLDS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);
	private static final long SALT = 0x5745_4E44_4552_4552L;

	public static final AttachmentType<Boolean> ROLLED = AttachmentRegistry.createPersistent(
		StellaTower.id("wanderer_rolled"), Codec.BOOL
	);

	private static volatile @Nullable Double pinnedChance;
	private static volatile Predicate<ServerPlayer> scanFilter = player -> !player.isSpectator();
	private static volatile Consumer<Mob> recorder = mob -> { };

	private TowerWanderers() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerWanderers::tick);
	}

	public static double chance() {
		Double pin = pinnedChance;
		return pin != null ? pin : CHANCE;
	}

	/** The test seam: pins the area chance, or clears the pin with null. */
	public static void pinChance(final @Nullable Double chance) {
		pinnedChance = chance;
	}

	/** Which players' surroundings are scanned; the test world narrows it to a test's own player. */
	public static void setScanFilter(final Predicate<ServerPlayer> filter) {
		scanFilter = filter;
	}

	public static Predicate<ServerPlayer> scanFilter() {
		return scanFilter;
	}

	/** The test seam: hears of each mob a scan raises. */
	public static void setRecorder(final @Nullable Consumer<Mob> heard) {
		recorder = heard == null ? mob -> { } : heard;
	}

	public static boolean isAnchor(final ChunkPos pos) {
		return Math.floorMod(pos.x(), REGION_CHUNKS) == 0 && Math.floorMod(pos.z(), REGION_CHUNKS) == 0;
	}

	/** The area's own roll, fixed by the world seed, its dimension and its position. */
	public static RandomSource regionRandom(final ServerLevel level, final ChunkPos pos) {
		return RandomSource.create(level.getSeed() ^ SALT ^ level.dimension().identifier().hashCode() ^ (pos.pack() * 0x9E3779B97F4A7C15L));
	}

	public static boolean rolls(final RandomSource random, final double chance) {
		return random.nextDouble() < chance;
	}

	/** The kind a wanderer of this floor is: any the floor itself may hold. */
	public static EntityType<? extends Mob> pick(final RandomSource random, final int floor) {
		List<EntityType<? extends Mob>> pool = TowerMobs.pool(floor);
		return pool.get(random.nextInt(pool.size()));
	}

	public static boolean isRolled(final LevelChunk chunk) {
		return Boolean.TRUE.equals(chunk.getAttached(ROLLED));
	}

	/** Clears an area's roll, so it rolls again; for tests and operators. */
	public static void forget(final LevelChunk chunk) {
		chunk.removeAttached(ROLLED);
	}

	/** Rolls the area whose low corner is this chunk, once; the mob it raised, or null. */
	public static @Nullable Mob rollRegion(final ServerLevel level, final LevelChunk chunk) {
		return rollRegion(level, chunk, TowerProgress.highest(level.getServer()), chance(),
			level.dimensionType().hasSkyLight() && level.isBrightOutside());
	}

	/** Rolls the area with a given highest floor and chance; nought means the tower is locked. */
	public static @Nullable Mob rollRegion(final ServerLevel level, final LevelChunk chunk, final int highest, final double chance) {
		return rollRegion(level, chunk, highest, chance, false);
	}

	/** Whether wanderers rise at this difficulty at all. */
	public static boolean raisesIn(final net.minecraft.world.Difficulty difficulty) {
		return difficulty != net.minecraft.world.Difficulty.PEACEFUL;
	}

	/** Rolls the area as in daylight or not, which a test cannot set for the whole level. */
	public static @Nullable Mob rollRegion(final ServerLevel level, final LevelChunk chunk, final int highest, final double chance, final boolean daylight) {
		ChunkPos pos = chunk.getPos();
		// A locked tower leaves the area unrolled, so it may still roll once the tower opens; and an area
		// whose entities are not loaded yet would lose what it raised.
		// Peaceful raises nothing, and a player already in the area would see it appear; either way the area
		// keeps its roll for later.
		if (highest <= 0 || !level.areEntitiesLoaded(pos.pack()) || isRolled(chunk) || !raisesIn(level.getDifficulty())
			|| playerWithin(level, pos, MIN_RING)) {
			return null;
		}
		RandomSource random = regionRandom(level, pos);
		if (!rolls(random, chance)) {
			chunk.setAttached(ROLLED, Boolean.TRUE);
			return null;
		}
		EntityType<? extends Mob> kind = pick(random, highest);
		if (daylight && kind.builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.BURN_IN_DAYLIGHT)) {
			// It would burn under the open sky, its drops going to no one: the area waits for the night.
			return null;
		}
		chunk.setAttached(ROLLED, Boolean.TRUE);
		Mob mob = kind.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(Vec3.atBottomCenterOf(pos.getMiddleBlockPosition(level.getSeaLevel())));
		// Finalized first: a magma cube's size, and so the room it needs, is only drawn here.
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
		TowerMobs.settle(mob);
		if (!place(level, pos, mob, random)) {
			mob.discard();
			StellaTower.LOGGER.debug("No footing for a floor {} wanderer in {} at {}", highest, level.dimension().identifier(), pos);
			return null;
		}
		TowerMobs.makeTowerMob(mob, highest, TowerElement.values()[random.nextInt(TowerElement.values().length)]);
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		StellaTower.LOGGER.debug("A floor {} {} wanders {} at {}", highest, mob.getType().getDescriptionId(), level.dimension().identifier(), mob.blockPosition());
		return mob;
	}

	/** Whether any player stands within this many chunks of the area's anchor. */
	private static boolean playerWithin(final ServerLevel level, final ChunkPos pos, final int chunks) {
		for (ServerPlayer player : level.players()) {
			ChunkPos at = player.chunkPosition();
			if (!player.isSpectator() && Math.max(Math.abs(at.x() - pos.x()), Math.abs(at.z() - pos.z())) < chunks) {
				return true;
			}
		}
		return false;
	}

	/** Finds the mob footing in the chunk: open ground under the sky, or a cave floor under a roof. */
	private static boolean place(final ServerLevel level, final ChunkPos pos, final Mob mob, final RandomSource random) {
		for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
			int x = pos.getMinBlockX() + random.nextInt(16);
			int z = pos.getMinBlockZ() + random.nextInt(16);
			int y = level.dimensionType().hasCeiling() ? underRoof(level, x, z) : surface(level, x, z);
			if (y == Integer.MIN_VALUE) {
				continue;
			}
			mob.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (level.noCollision(mob, mob.getBoundingBox()) && !level.containsAnyLiquid(mob.getBoundingBox())) {
				return true;
			}
		}
		return false;
	}

	private static int surface(final ServerLevel level, final int x, final int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		// Over the void the heightmap finds nothing at all.
		return y > level.getMinY() && standsOn(level, new BlockPos(x, y - 1, z)) ? y : Integer.MIN_VALUE;
	}

	/** The lowest cave floor above the lava sea, well beneath a roof the heightmap would find instead. */
	private static int underRoof(final ServerLevel level, final int x, final int z) {
		int roof = level.getMinY() + level.dimensionType().logicalHeight();
		for (int y = level.getSeaLevel() + 1; y < roof - 2; y++) {
			BlockPos feet = new BlockPos(x, y, z);
			if (standsOn(level, feet.below()) && open(level, feet) && open(level, feet.above())) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static boolean standsOn(final ServerLevel level, final BlockPos below) {
		BlockState state = level.getBlockState(below);
		return state.getFluidState().isEmpty() && state.isFaceSturdy(level, below, Direction.UP);
	}

	private static boolean open(final ServerLevel level, final BlockPos at) {
		BlockState state = level.getBlockState(at);
		return state.getFluidState().isEmpty() && state.getCollisionShape(level, at).isEmpty();
	}

	/** Every scan, the loaded areas in each player's ring roll, each once. */
	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % SCAN_TICKS != 0 || TowerProgress.highest(server) <= 0) {
			return;
		}
		for (ResourceKey<Level> key : WORLDS) {
			ServerLevel level = server.getLevel(key);
			if (level == null) {
				continue;
			}
			for (ServerPlayer player : List.copyOf(level.players())) {
				if (!scanFilter.test(player)) {
					continue;
				}
				ChunkPos center = player.chunkPosition();
				for (int dx = -MAX_RING; dx <= MAX_RING; dx++) {
					for (int dz = -MAX_RING; dz <= MAX_RING; dz++) {
						ChunkPos pos = new ChunkPos(center.x() + dx, center.z() + dz);
						if (Math.max(Math.abs(dx), Math.abs(dz)) < MIN_RING || !isAnchor(pos)) {
							continue;
						}
						LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
						if (chunk != null) {
							Mob mob = rollRegion(level, chunk);
							if (mob != null) {
								recorder.accept(mob);
							}
						}
					}
				}
			}
		}
	}
}
