package com.stella.mobs;

import com.stella.mobs.behavior.ChunkEncounters;
import com.stella.mobs.behavior.ChunkEncounters.Encounter;
import com.stella.mobs.behavior.Elite;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each overworld chunk rolls once, when a player first comes near it: inside the spawn safe zone it may
 * hold one elite that does not burn, and outside it a pair of patrolling Pillagers with no captain.
 */
public class ChunkEncounterGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-mobs-test.encounters");
	private static final int SAMPLES = 100_000;
	private static final long WORLD_SEED = 20_260_927L;
	/** What each chunk's roll spawned, keyed by the chunk, as the encounter recorded it. */
	private static final Map<ChunkPos, List<Mob>> SPAWNED = new ConcurrentHashMap<>();
	private static final Set<UUID> SCANNING = ConcurrentHashMap.newKeySet();
	/** Each chunk the scan handed over, and whether it came only once. */
	private static final Map<ChunkPos, Boolean> SCANNED = new ConcurrentHashMap<>();
	private static final int PLACEMENT_SAMPLES = 40;

	static {
		// Only a test's own player drives the scan, and the scan only records, so no test gets a stray encounter.
		ChunkEncounters.setScanFilter(player -> SCANNING.contains(player.getUUID()));
		ChunkEncounters.setScanRoller((level, chunk, safe) -> SCANNED.merge(chunk.getPos(), Boolean.TRUE, (a, b) -> Boolean.FALSE));
		ChunkEncounters.setRecorder((level, pos, encounter, spawned) -> SPAWNED.put(pos, List.copyOf(spawned)));
	}

	@GameTest
	public void theRollRatesAreWhatShips(final GameTestHelper helper) {
		int safeEncounters = 0;
		int pairs = 0;
		int wrongSide = 0;
		for (int i = 0; i < SAMPLES; i++) {
			ChunkPos pos = new ChunkPos(i % 317 - 158, i / 317 - 158);
			Encounter safe = ChunkEncounters.decide(ChunkEncounters.chunkRandom(WORLD_SEED, pos), true);
			Encounter wild = ChunkEncounters.decide(ChunkEncounters.chunkRandom(WORLD_SEED, pos), false);
			safeEncounters += safe == Encounter.NONE ? 0 : 1;
			pairs += wild == Encounter.PILLAGER_PAIR ? 1 : 0;
			wrongSide += safe == Encounter.PILLAGER_PAIR ? 1 : 0;
		}
		double pairRate = (double) pairs / SAMPLES;
		LOG.info("[BEHAVIOR] test=theRollRatesAreWhatShips RESULT samples={} safeEncounters={} pairRate={} wrongSide={}", SAMPLES, safeEncounters, pairRate, wrongSide);
		helper.assertTrue(safeEncounters == 0, "the safe zone rolled " + safeEncounters + " encounters; it rolls none");
		helper.assertTrue(Math.abs(pairRate - ChunkEncounters.PILLAGER_PAIR_CHANCE) <= band(ChunkEncounters.PILLAGER_PAIR_CHANCE), "pillager pair rate " + pairRate);
		helper.assertTrue(wrongSide == 0, wrongSide + " rolls gave an encounter to the wrong side of the safe zone");
		helper.succeed();
	}

	@GameTest
	public void theSafeZoneIsTheSpawnSafetyRadius(final GameTestHelper helper) {
		double radius = 1000.0;
		boolean[] actual = {
			ChunkEncounters.isSafe(Level.OVERWORLD, new ChunkPos(0, 0), 0.0, 0.0, radius),
			ChunkEncounters.isSafe(Level.OVERWORLD, new ChunkPos((int) (radius / 16.0) - 2, 0), 0.0, 0.0, radius),
			ChunkEncounters.isSafe(Level.OVERWORLD, new ChunkPos((int) (radius / 16.0) + 2, 0), 0.0, 0.0, radius),
			ChunkEncounters.isSafe(Level.NETHER, new ChunkPos(0, 0), 0.0, 0.0, radius),
			ChunkEncounters.isSafe(Level.OVERWORLD, new ChunkPos(0, 0), 0.0, 0.0, 0.0)
		};
		boolean[] expected = { true, true, false, false, false };
		LOG.info("[BEHAVIOR] test=theSafeZoneIsTheSpawnSafetyRadius RESULT actual={}", java.util.Arrays.toString(actual));
		helper.assertTrue(java.util.Arrays.equals(actual, expected), "safe zone answers " + java.util.Arrays.toString(actual));
		helper.succeed();
	}

	@GameTest(padding = 32)
	public void aPillagerPairPatrolsWithNoCaptain(final GameTestHelper helper) {
		LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
		List<Mob> spawned = ChunkEncounters.spawn(helper.getLevel(), chunk, Encounter.PILLAGER_PAIR, RandomSource.create(11L));
		helper.assertTrue(spawned.size() == ChunkEncounters.PILLAGER_PAIR_SIZE, "a pillager pair roll spawned " + spawned.size() + " mobs");
		long leaders = spawned.stream().filter(mob -> mob instanceof Pillager pillager && pillager.isPatrolLeader()).count();
		for (Mob mob : spawned) {
			helper.assertTrue(mob instanceof Pillager, "the pair holds a " + mob.getType());
			Pillager pillager = (Pillager) mob;
			helper.assertTrue(pillager.hasPatrolTarget(), "a pillager of the pair is not patrolling");
			helper.assertFalse(pillager.isCaptain(), "a pillager of the pair is a captain, so killing it gives Bad Omen");
		}
		helper.assertTrue(leaders == 1, "the pair has " + leaders + " leaders");
		LOG.info("[BEHAVIOR] test=aPillagerPairPatrolsWithNoCaptain RESULT pillagers={} leaders={} captains=0", spawned.size(), leaders);
		spawned.forEach(Mob::discard);
		helper.succeed();
	}

	@GameTest(padding = 32)
	public void aChunkRollsOnlyOnceAndSpawnsWhatItRolled(final GameTestHelper helper) {
		LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
		long seed = seedFor(Encounter.PILLAGER_PAIR, false);
		// A neighbouring test's scan may have reached this chunk, so it starts unrolled.
		ChunkEncounters.forget(chunk);
		Optional<Encounter> first = ChunkEncounters.roll(helper.getLevel(), chunk, false, RandomSource.create(seed));
		List<Mob> spawned = SPAWNED.getOrDefault(chunk.getPos(), List.of());
		Optional<Encounter> second = ChunkEncounters.roll(helper.getLevel(), chunk, false, RandomSource.create(seed));
		LOG.info("[BEHAVIOR] test=aChunkRollsOnlyOnceAndSpawnsWhatItRolled RESULT first={} spawned={} second={}", first, spawned.size(), second);
		helper.assertTrue(first.equals(Optional.of(Encounter.PILLAGER_PAIR)) && ChunkEncounters.isRolled(chunk), "the first roll was " + first);
		helper.assertTrue(spawned.size() == ChunkEncounters.PILLAGER_PAIR_SIZE && spawned.stream().allMatch(mob -> mob.isAlive() && mob instanceof Pillager), "the roll spawned " + spawned);
		helper.assertTrue(second.isEmpty(), "the chunk rolled a second time: " + second);
		spawned.forEach(Mob::discard);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 32)
	public void theChunksAroundAPlayerRollAsTheyComeNear(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		ChunkPos center = player.chunkPosition();
		SCANNING.add(player.getUUID());
		helper.succeedWhen(() -> {
			List<Map.Entry<ChunkPos, Boolean>> near = SCANNED.entrySet().stream()
				.filter(entry -> ring(entry.getKey(), center) <= ChunkEncounters.MAX_RING + 1).toList();
			helper.assertTrue(!near.isEmpty(), "no chunk near the player has rolled yet");
			for (Map.Entry<ChunkPos, Boolean> entry : near) {
				int ring = ring(entry.getKey(), center);
				helper.assertTrue(ring >= ChunkEncounters.MIN_RING && ring <= ChunkEncounters.MAX_RING, "chunk " + entry.getKey() + " rolled at ring " + ring);
				helper.assertTrue(entry.getValue(), "chunk " + entry.getKey() + " rolled twice");
			}
			SCANNING.remove(player.getUUID());
			LOG.info("[BEHAVIOR] test=theChunksAroundAPlayerRollAsTheyComeNear RESULT rolled={}", near.size());
		});
	}

	@GameTest(padding = 64)
	public void aPillagerPairStaysOutOfAVillage(final GameTestHelper helper) {
		// A chunk inside this test's padding holds no structure, so its ground is one height, as a village's is.
		BlockPos clear = helper.absolutePos(new BlockPos(1, 1, 1)).offset(32, 0, 0);
		LevelChunk chunk = helper.getLevel().getChunkAt(clear);
		BlockPos bell = helper.getLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, clear);
		helper.getLevel().setBlockAndUpdate(bell, net.minecraft.world.level.block.Blocks.BELL.defaultBlockState());
		// A village counts only claimed meeting points, so the bell is claimed as a villager would.
		var poi = helper.getLevel().getPoiManager();
		poi.take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING), (type, pos) -> pos.equals(bell), bell, 1);
		boolean village = helper.getLevel().isCloseToVillage(bell, 2);
		List<Mob> spawned = ChunkEncounters.spawn(helper.getLevel(), chunk, Encounter.PILLAGER_PAIR, RandomSource.create(11L));
		poi.release(bell);
		helper.getLevel().setBlockAndUpdate(bell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		spawned.forEach(Mob::discard);
		LOG.info("[BEHAVIOR] test=aPillagerPairStaysOutOfAVillage RESULT village={} spawned={}", village, spawned.size());
		helper.assertTrue(village, "the bell made no village, so the case proves nothing");
		helper.assertTrue(spawned.isEmpty(), "a pillager pair spawned inside a village");
		helper.succeed();
	}

	@GameTest(padding = 64)
	public void aPillagerPairStaysOutOfMushroomFields(final GameTestHelper helper) {
		LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
		var biomes = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
		var before = helper.getLevel().getBiome(helper.absolutePos(new BlockPos(1, 1, 1)));
		fillBiome(helper, chunk, biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.MUSHROOM_FIELDS));
		boolean mushroom = helper.getLevel().getBiome(helper.absolutePos(new BlockPos(1, 1, 1))).is(net.minecraft.world.level.biome.Biomes.MUSHROOM_FIELDS);
		List<Mob> spawned = ChunkEncounters.spawn(helper.getLevel(), chunk, Encounter.PILLAGER_PAIR, RandomSource.create(11L));
		fillBiome(helper, chunk, before);
		spawned.forEach(Mob::discard);
		LOG.info("[BEHAVIOR] test=aPillagerPairStaysOutOfMushroomFields RESULT mushroom={} spawned={}", mushroom, spawned.size());
		helper.assertTrue(mushroom, "the chunk did not turn to mushroom fields, so the case proves nothing");
		helper.assertTrue(spawned.isEmpty(), "a pillager pair spawned in mushroom fields");
		helper.succeed();
	}

	// The fill command caps its volume, so the full height is filled in bands. The biome lookup is fuzzy
	// near a chunk's edge, so a margin around the chunk is filled too.
	private static void fillBiome(final GameTestHelper helper, final LevelChunk chunk, final net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
		int band = 16;
		int margin = 8;
		for (int y = helper.getLevel().getMinY(); y <= helper.getLevel().getMaxY(); y += band) {
			net.minecraft.server.commands.FillBiomeCommand.fill(
				helper.getLevel(),
				new BlockPos(chunk.getPos().getMinBlockX() - margin, y, chunk.getPos().getMinBlockZ() - margin),
				new BlockPos(chunk.getPos().getMaxBlockX() + margin, Math.min(y + band - 1, helper.getLevel().getMaxY()), chunk.getPos().getMaxBlockZ() + margin),
				biome
			).ifRight(error -> {
				throw new IllegalStateException("fillbiome refused: " + error.getMessage());
			});
		}
	}

	@GameTest(padding = 32)
	public void aPillagerPairFindsRoomOnOpenGround(final GameTestHelper helper) {
		LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
		int placed = 0;
		for (int seed = 0; seed < PLACEMENT_SAMPLES; seed++) {
			List<Mob> spawned = ChunkEncounters.spawn(helper.getLevel(), chunk, Encounter.PILLAGER_PAIR, RandomSource.create(seed));
			placed += spawned.size() == ChunkEncounters.PILLAGER_PAIR_SIZE ? 1 : 0;
			spawned.forEach(Mob::discard);
		}
		double rate = (double) placed / PLACEMENT_SAMPLES;
		LOG.info("[BEHAVIOR] test=aPillagerPairFindsRoomOnOpenGround RESULT samples={} placed={} rate={} biome={}", PLACEMENT_SAMPLES, placed, rate, helper.getLevel().getBiome(helper.absolutePos(new BlockPos(1, 1, 1))).getRegisteredName());
		helper.assertTrue(placed == PLACEMENT_SAMPLES, "only " + placed + " of " + PLACEMENT_SAMPLES + " pairs found room on flat open ground");
		helper.succeed();
	}

	private static int ring(final ChunkPos pos, final ChunkPos center) {
		return Math.max(Math.abs(pos.x() - center.x()), Math.abs(pos.z() - center.z()));
	}

	// The first seed whose roll lands on the wanted encounter, so a roll can be driven without changing a chance.
	private static long seedFor(final Encounter wanted, final boolean safe) {
		for (long seed = 0L; ; seed++) {
			if (ChunkEncounters.decide(RandomSource.create(seed), safe) == wanted) {
				return seed;
			}
			if (seed > 10_000L) {
				throw new IllegalStateException("no seed rolls " + wanted);
			}
		}
	}

	// Four standard deviations of a sampled rate around the shipped chance.
	private static double band(final double chance) {
		return 4.0 * Math.sqrt(chance * (1.0 - chance) / SAMPLES);
	}
}
