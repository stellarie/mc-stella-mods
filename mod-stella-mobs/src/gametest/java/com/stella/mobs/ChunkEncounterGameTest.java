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

	static {
		// Only a test's own player drives the scan, so no other test gets a stray encounter.
		ChunkEncounters.setScanFilter(player -> SCANNING.contains(player.getUUID()));
		ChunkEncounters.setRecorder((level, pos, encounter, spawned) -> SPAWNED.put(pos, List.copyOf(spawned)));
	}

	@GameTest
	public void theRollRatesAreWhatShips(final GameTestHelper helper) {
		int elites = 0;
		int pairs = 0;
		int wrongSide = 0;
		for (int i = 0; i < SAMPLES; i++) {
			ChunkPos pos = new ChunkPos(i % 317 - 158, i / 317 - 158);
			Encounter safe = ChunkEncounters.decide(ChunkEncounters.chunkRandom(WORLD_SEED, pos), true);
			Encounter wild = ChunkEncounters.decide(ChunkEncounters.chunkRandom(WORLD_SEED, pos), false);
			elites += safe == Encounter.ELITE ? 1 : 0;
			pairs += wild == Encounter.PILLAGER_PAIR ? 1 : 0;
			wrongSide += (safe == Encounter.PILLAGER_PAIR ? 1 : 0) + (wild == Encounter.ELITE ? 1 : 0);
		}
		double eliteRate = (double) elites / SAMPLES;
		double pairRate = (double) pairs / SAMPLES;
		LOG.info("[BEHAVIOR] test=theRollRatesAreWhatShips RESULT samples={} eliteRate={} pairRate={} wrongSide={}", SAMPLES, eliteRate, pairRate, wrongSide);
		helper.assertTrue(Math.abs(eliteRate - ChunkEncounters.SAFE_ELITE_CHANCE) <= band(ChunkEncounters.SAFE_ELITE_CHANCE), "safe-zone elite rate " + eliteRate);
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
	public void aSafeZoneEliteIsOneEliteThatDoesNotBurnAndStays(final GameTestHelper helper) {
		helper.setTime(6_000L);
		LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
		List<Mob> spawned = ChunkEncounters.spawn(helper.getLevel(), chunk, Encounter.ELITE, RandomSource.create(7L));
		helper.assertTrue(spawned.size() == 1, "a safe-zone elite roll spawned " + spawned.size() + " mobs");
		Mob elite = spawned.get(0);
		helper.assertTrue(Elite.isElite(elite), "the safe-zone mob is not elite");
		helper.assertTrue(ChunkEncounters.SAFE_ELITE_TYPES.contains(elite.getType()), "the safe-zone elite is a " + elite.getType());
		helper.assertTrue(elite.isPersistenceRequired(), "the safe-zone elite can despawn");
		helper.assertTrue(elite.chunkPosition().equals(chunk.getPos()), "the elite spawned outside its chunk");
		LOG.info("[BEHAVIOR] test=aSafeZoneEliteIsOneEliteThatDoesNotBurnAndStays RESULT type={} elite=true persistent=true", elite.getType());
		spawned.forEach(Mob::discard);
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
		Map<ChunkPos, Boolean> rolled = new ConcurrentHashMap<>();
		ChunkEncounters.setScanRoller((level, chunk, safe) -> {
			if (level == helper.getLevel()) {
				rolled.merge(chunk.getPos(), Boolean.TRUE, (a, b) -> Boolean.FALSE);
			}
		});
		SCANNING.add(player.getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(!rolled.isEmpty(), "no chunk near the player has rolled yet");
			for (Map.Entry<ChunkPos, Boolean> entry : rolled.entrySet()) {
				int ring = Math.max(Math.abs(entry.getKey().x() - center.x()), Math.abs(entry.getKey().z() - center.z()));
				helper.assertTrue(ring >= ChunkEncounters.MIN_RING && ring <= ChunkEncounters.MAX_RING, "chunk " + entry.getKey() + " rolled at ring " + ring);
				helper.assertTrue(entry.getValue(), "chunk " + entry.getKey() + " rolled twice");
			}
			SCANNING.remove(player.getUUID());
			ChunkEncounters.setScanRoller(ChunkEncounters.defaultRoller());
			LOG.info("[BEHAVIOR] test=theChunksAroundAPlayerRollAsTheyComeNear RESULT rolled={}", rolled.size());
		});
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
