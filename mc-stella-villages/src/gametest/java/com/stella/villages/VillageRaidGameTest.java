package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.mixin.RaidAccessor;
import com.stella.villages.mixin.RaidsInvoker;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import com.stella.villages.patrol.RaidAlert;
import com.stella.villages.raid.VillageRaids;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.phys.Vec3;

/**
 * V3: the daily roll's rate and bookkeeping, the one-wave cap, and the attendance rule. A mini-raid wave holds only
 * pillagers, three to five of them, and a vanilla raid keeps its own first wave. A raid never recruits a village
 * guard, and any raid at the village alerts every guard, even when the raiders have no target of their own.
 */
public final class VillageRaidGameTest {
	private static final int TRIALS = 100_000;
	// 3.29 standard deviations of a binomial rate at the trial count: a 99.9% band around the shipped chance.
	private static final double BAND = 3.29 * Math.sqrt(VillageRaids.RAID_CHANCE * (1.0 - VillageRaids.RAID_CHANCE) / TRIALS);
	private static final int WAVES = 200;
	private static final double MEAN_BAND = 0.2;
	// The vanilla first wave on Normal: 4 pillagers,
	// plus a bonus of 0 or 1 pillager
	// and 0 or 1 vindicator.
	private static final int VANILLA_FIRST_WAVE_PILLAGERS = 4;
	private static final double VANILLA_FIRST_WAVE_MEAN = 5.0;
	private static final double VANILLA_MEAN_BAND = 0.3;
	private static final int ALERT_WINDOW_TICKS = 40;
	private static final float RAIDER_HEALTH = 10_000.0F;
	private static final int LARGE_SAMPLE = 20_000;
	private static final double SHARE_BAND = 0.03;

	/** Vanilla's private raid members. */
	private static final class Vanilla {
		static final Class<?> TYPES;
		static final Method DEFAULTS;
		static final Method BONUS;
		static final Method SPAWN;
		static final Field RANDOM;
		static final Field STARTED;

		static {
			try {
				TYPES = Class.forName("net.minecraft.world.entity.raid.Raid$RaiderType");
				DEFAULTS = Raid.class.getDeclaredMethod("getDefaultNumSpawns", TYPES, int.class, boolean.class);
				BONUS = Raid.class.getDeclaredMethod("getPotentialBonusSpawns", TYPES, RandomSource.class, int.class, DifficultyInstance.class, boolean.class);
				SPAWN = Raid.class.getDeclaredMethod("spawnGroup", ServerLevel.class, BlockPos.class);
				RANDOM = Raid.class.getDeclaredField("random");
				STARTED = Raid.class.getDeclaredField("started");
				for (var member : List.of(DEFAULTS, BONUS, SPAWN)) {
					member.setAccessible(true);
				}
				RANDOM.setAccessible(true);
				STARTED.setAccessible(true);
			} catch (ReflectiveOperationException error) {
				throw new ExceptionInInitializerError(error);
			}
		}
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void raidRollRateOverOneHundredThousandSeededDays(final GameTestHelper helper) {
		int[] raids = { 0, 0 };
		int differing = 0;
		for (long day = 0; day < TRIALS; day++) {
			boolean first = VillageRaids.rolls(20260926L, day);
			boolean second = VillageRaids.rolls(20260927L, day);
			raids[0] += first ? 1 : 0;
			raids[1] += second ? 1 : 0;
			differing += first != second ? 1 : 0;
		}

		double rate = (double) raids[0] / TRIALS;
		PatrolTestSupport.result("raidRollRateOverOneHundredThousandSeededDays",
			"rate=" + rate + " chance=" + VillageRaids.RAID_CHANCE + " band=" + BAND + " otherSeedRate=" + (double) raids[1] / TRIALS + " daysThatDiffer=" + differing);
		helper.assertTrue(Math.abs(rate - VillageRaids.RAID_CHANCE) <= BAND, "the measured raid rate " + rate + " is outside the band around " + VillageRaids.RAID_CHANCE);
		// A second world must raid on other days, or every world would share one raid calendar.
		helper.assertTrue(differing > 0, "two world seeds rolled the same raid days");
		helper.succeed();
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void dailyDecisionFollowsTheDayAndTheRoll(final GameTestHelper helper) {
		VillageWorldData data = VillageWorldData.empty();
		List<VillageRaids.Outcome> outcomes = new ArrayList<>();
		// The first day a world is seen only records it; then each new day rolls once, and the roll decides.
		outcomes.add(VillageRaids.decide(data, 3L, true));
		outcomes.add(VillageRaids.decide(data, 3L, true));
		outcomes.add(VillageRaids.decide(data, 4L, true));
		outcomes.add(VillageRaids.decide(data, 4L, true));
		outcomes.add(VillageRaids.decide(data, 5L, false));
		List<VillageRaids.Outcome> expected = List.of(
			VillageRaids.Outcome.FIRST_DAY, VillageRaids.Outcome.ALREADY_ROLLED, VillageRaids.Outcome.RAID,
			VillageRaids.Outcome.ALREADY_ROLLED, VillageRaids.Outcome.NO_RAID
		);
		PatrolTestSupport.result("dailyDecisionFollowsTheDayAndTheRoll", "outcomes=" + outcomes);
		helper.assertValueEqual(outcomes, expected, "decisions for days 3, 3, 4, 4, 5");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void modRaidHasExactlyOneWave(final GameTestHelper helper) {
		String test = "modRaidHasExactlyOneWave";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		VillageWorldData.VillageRecord village = village(helper);
		Raid raid = VillageRaids.start(helper.getLevel(), village);
		int waves = raid == null ? -1 : ((RaidAccessor) raid).mcStellaVillages$numGroups();
		if (raid != null) {
			raid.stop();
		}
		PatrolTestSupport.result(test, "started=" + (raid != null) + " waves=" + waves);
		helper.assertTrue(raid != null && waves == 1, "the village raid did not start with exactly one wave: started=" + (raid != null) + " waves=" + waves);
		helper.succeed();
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void vanillaRaidKeepsItsWaves(final GameTestHelper helper) {
		// The same construction outside a mod raid must keep vanilla's count, so the cap cannot leak into omen raids.
		Raid raid = new Raid(helper.absolutePos(new BlockPos(3, 1, 3)), helper.getLevel().getDifficulty());
		int waves = ((RaidAccessor) raid).mcStellaVillages$numGroups();
		PatrolTestSupport.result("vanillaRaidKeepsItsWaves", "waves=" + waves + " vanilla=" + raid.getNumGroups(helper.getLevel().getDifficulty()));
		helper.assertValueEqual(Integer.valueOf(waves), Integer.valueOf(raid.getNumGroups(helper.getLevel().getDifficulty())), "vanilla wave count");
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void miniRaidIsThreeToFivePillagers(final GameTestHelper helper) {
		String test = "miniRaidIsThreeToFivePillagers";
		BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
		double expectedMean = (VillageRaids.MINI_RAID_MIN + VillageRaids.MINI_RAID_MAX) / 2.0;
		StringBuilder summary = new StringBuilder();
		for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
			int pillagers = 0;
			int others = 0;
			int smallest = Integer.MAX_VALUE;
			int largest = 0;
			for (long seed = 0; seed < WAVES; seed++) {
				Raid raid = VillageRaids.newMiniRaid(center, difficulty);
				helper.assertTrue(VillageRaids.isMiniRaid(raid), "a mini-raid is not marked as one");
				Map<String, Integer> wave = composition(raid, seed, difficulty);
				int count = wave.get("PILLAGER");
				pillagers += count;
				others += wave.values().stream().mapToInt(Integer::intValue).sum() - count;
				smallest = Math.min(smallest, count);
				largest = Math.max(largest, count);
			}
			double mean = (double) pillagers / WAVES;
			summary.append(difficulty).append("[mean=").append(mean).append(" min=").append(smallest).append(" max=").append(largest)
				.append(" otherRaiders=").append(others).append("] ");
			helper.assertTrue(others == 0, "a " + difficulty + " mini-raid wave held " + others + " raiders that are not pillagers");
			helper.assertTrue(smallest >= VillageRaids.MINI_RAID_MIN && largest <= VillageRaids.MINI_RAID_MAX,
				"a " + difficulty + " mini-raid wave held " + smallest + " to " + largest + " pillagers");
			helper.assertTrue(Math.abs(mean - expectedMean) <= MEAN_BAND, "the " + difficulty + " mean " + mean + " is not within " + MEAN_BAND + " of " + expectedMean);
		}

		// A large sample pins the shape:
		// no size is likelier than another.
		int[] sizes = new int[VillageRaids.MINI_RAID_MAX + 1];
		for (long seed = WAVES; seed < WAVES + LARGE_SAMPLE; seed++) {
			sizes[composition(VillageRaids.newMiniRaid(center, Difficulty.NORMAL), seed, Difficulty.NORMAL).get("PILLAGER")]++;
		}
		double fair = 1.0 / (VillageRaids.MINI_RAID_MAX - VillageRaids.MINI_RAID_MIN + 1);
		for (int size = VillageRaids.MINI_RAID_MIN; size <= VillageRaids.MINI_RAID_MAX; size++) {
			double share = (double) sizes[size] / LARGE_SAMPLE;
			summary.append("share").append(size).append('=').append(share).append(' ');
			helper.assertTrue(Math.abs(share - fair) <= SHARE_BAND, "a wave of " + size + " pillagers came up " + share + " of the time, not about " + fair);
		}

		// Spawned for real, the raid's own method
		// must match its count methods.
		ServerLevel level = helper.getLevel();
		StringBuilder spawned = new StringBuilder();
		for (long seed = 0; seed < 5; seed++) {
			Raid raid = VillageRaids.newMiniRaid(center, Difficulty.NORMAL);
			setRandom(raid, RandomSource.create(mixed(seed)));
			Map<String, Integer> wave = spawnedWave(level, raid, center);
			spawned.append(wave).append(' ');
			helper.assertTrue(wave.size() == 1 && wave.containsKey("minecraft:pillager"), "a spawned mini-raid wave held " + wave);
			int count = wave.get("minecraft:pillager");
			helper.assertTrue(count >= VillageRaids.MINI_RAID_MIN && count <= VillageRaids.MINI_RAID_MAX, "a spawned mini-raid wave held " + count + " pillagers");
		}
		PatrolTestSupport.result(test, "waves=" + WAVES + " " + summary + "spawned=" + spawned);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void vanillaRaidFirstWaveKeepsItsRaiders(final GameTestHelper helper) {
		String test = "vanillaRaidFirstWaveKeepsItsRaiders";
		BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
		int totalRaiders = 0;
		int smallestPillagers = Integer.MAX_VALUE;
		int largestPillagers = 0;
		int largestVindicators = 0;
		for (long seed = 0; seed < WAVES; seed++) {
			Raid raid = new Raid(center, Difficulty.NORMAL);
			helper.assertFalse(VillageRaids.isMiniRaid(raid), "a vanilla raid is marked as a mini-raid");
			Map<String, Integer> wave = composition(raid, seed, Difficulty.NORMAL);
			int pillagers = wave.get("PILLAGER");
			int vindicators = wave.get("VINDICATOR");
			int others = wave.values().stream().mapToInt(Integer::intValue).sum() - pillagers - vindicators;
			helper.assertTrue(others == 0, "a vanilla first wave held " + others + " raiders that are not pillagers or vindicators");
			totalRaiders += pillagers + vindicators;
			smallestPillagers = Math.min(smallestPillagers, pillagers);
			largestPillagers = Math.max(largestPillagers, pillagers);
			largestVindicators = Math.max(largestVindicators, vindicators);
		}
		double mean = (double) totalRaiders / WAVES;
		PatrolTestSupport.result(test, "meanRaiders=" + mean + " pillagers=" + smallestPillagers + ".." + largestPillagers + " mostVindicators=" + largestVindicators);
		helper.assertTrue(smallestPillagers == VANILLA_FIRST_WAVE_PILLAGERS && largestPillagers == VANILLA_FIRST_WAVE_PILLAGERS + 1,
			"a vanilla first wave held " + smallestPillagers + " to " + largestPillagers + " pillagers, not 4 to 5");
		helper.assertTrue(largestVindicators == 1, "no vanilla first wave held a vindicator, or one held more than one: most=" + largestVindicators);
		helper.assertTrue(Math.abs(mean - VANILLA_FIRST_WAVE_MEAN) <= VANILLA_MEAN_BAND, "the vanilla first wave mean " + mean + " is not within " + VANILLA_MEAN_BAND + " of " + VANILLA_FIRST_WAVE_MEAN);

		// A real wave, at the level's difficulty:
		// pillagers and vindicators, 4 pillagers or more.
		Map<String, Integer> wave = spawnedWave(helper.getLevel(), new Raid(center, helper.getLevel().getDifficulty()), center);
		PatrolTestSupport.result(test, "spawned=" + wave);
		helper.assertTrue(wave.getOrDefault("minecraft:pillager", 0) >= VANILLA_FIRST_WAVE_PILLAGERS, "a spawned vanilla first wave held " + wave);
		helper.assertTrue(wave.keySet().stream().allMatch(type -> type.equals("minecraft:pillager") || type.equals("minecraft:vindicator")), "a spawned vanilla first wave held " + wave);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void guardsNeverJoinARaid(final GameTestHelper helper) {
		String test = "guardsNeverJoinARaid";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		// Vanilla's spawn code flags a raider.
		// Save and load each guard with the flag set.
		List<Mob> guards = new ArrayList<>();
		for (Mob guard : guards(helper)) {
			((Raider) guard).setCanJoinRaid(true);
			guards.add(PatrolTestSupport.reload(helper, guard));
		}
		helper.assertValueEqual(Integer.valueOf(guards.size()), Integer.valueOf(PatrolService.PILLAGER_COUNT + PatrolService.RANGED_GUARD_COUNT), "guards of the village");
		Raid raid = VillageRaids.start(helper.getLevel(), village(helper));
		helper.assertTrue(raid != null && helper.getLevel().getRaidAt(guards.getFirst().blockPosition()) == raid, "the raid is not active where the guards stand");
		markStarted(raid);

		helper.runAfterDelay(ALERT_WINDOW_TICKS, () -> {
			List<String> joined = new ArrayList<>();
			for (Mob guard : guards) {
				Raider raider = (Raider) guard;
				if (raider.canJoinRaid() || Raids.canJoinRaid(raider) || raider.getCurrentRaid() != null) {
					joined.add(PatrolState.role(guard) + ":canJoin=" + raider.canJoinRaid() + ",raid=" + raider.getCurrentRaid());
				}
			}
			raid.stop();
			PatrolTestSupport.result(test, "guards=" + guards.size() + " couldJoin=" + joined);
			helper.assertTrue(joined.isEmpty(), "a guard can join a raid: " + joined);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void raidAlertsGuards(final GameTestHelper helper) {
		alertScenario(helper, "raidAlertsGuards", false);
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void raidAlertsGuardsInVanillaRaid(final GameTestHelper helper) {
		alertScenario(helper, "raidAlertsGuardsInVanillaRaid", true);
	}

	/**
	 * A raid with a raider beyond the alert range alerts no guard. Then a raider within the range joins, and every
	 * guard targets it within the window although it targets nobody. When the raid stops, the guards stand down.
	 */
	private static void alertScenario(final GameTestHelper helper, final String test, final boolean vanilla) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		VillageWorldData.VillageRecord village = village(helper);
		List<Mob> guards = guards(helper);
		helper.assertValueEqual(Integer.valueOf(guards.size()), Integer.valueOf(PatrolService.PILLAGER_COUNT + PatrolService.RANGED_GUARD_COUNT), "guards of the village");
		helper.assertTrue(guards.stream().allMatch(guard -> guard.getTarget() == null), "a guard had a target before the raid, so the case proves nothing");
		Raid raid = vanilla ? registerVanillaRaid(level, village) : VillageRaids.start(level, village);
		helper.assertTrue(raid != null && VillageRaids.isMiniRaid(raid) == !vanilla, "the raid is not the expected kind: vanilla=" + vanilla);
		markStarted(raid);
		BlockPos bell = PatrolTestSupport.anchor(helper);

		// The far raider is a raid record only.
		// A guard can target it off the world.
		Raider far = EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
		far.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		Vec3 farAt = Vec3.atBottomCenterOf(bell).add(RaidAlert.ALERT_RANGE + 16.0, 0.0, 0.0);
		far.setPos(farAt.x, farAt.y, farAt.z);
		raid.addWaveMob(level, 1, far, true);
		far.setCurrentRaid(raid);

		Raider[] near = new Raider[1];
		long[] startedAt = {-1L};
		long[] allTargetedAt = {-1L};
		helper.onEachTick(() -> {
			if (near[0] != null && allTargetedAt[0] < 0L && guards.stream().allMatch(guard -> guard.getTarget() == near[0])) {
				allTargetedAt[0] = helper.getTick();
			}
		});
		helper.startSequence()
			.thenIdle(ALERT_WINDOW_TICKS)
			.thenExecute(() -> {
				List<String> targeting = guards.stream().filter(guard -> guard.getTarget() != null).map(guard -> PatrolState.role(guard).toString()).toList();
				PatrolTestSupport.result(test, "onlyAFarRaider guardsWithATarget=" + targeting);
				helper.assertTrue(targeting.isEmpty(), "a guard targeted a raider beyond the alert range: " + targeting);
				near[0] = joinNearRaider(helper, raid);
				startedAt[0] = helper.getTick();
			})
			.thenIdle(ALERT_WINDOW_TICKS)
			.thenExecute(() -> {
				PatrolTestSupport.result(test, "vanilla=" + vanilla + " alertedAfter=" + (allTargetedAt[0] < 0L ? "never" : allTargetedAt[0] - startedAt[0])
					+ " window=" + ALERT_WINDOW_TICKS + " targets=" + guards.stream().map(guard -> String.valueOf(guard.getTarget() == near[0])).toList());
				helper.assertTrue(allTargetedAt[0] >= 0L && allTargetedAt[0] - startedAt[0] <= ALERT_WINDOW_TICKS,
					"the guards did not all target the raider within " + ALERT_WINDOW_TICKS + " ticks");
				helper.assertTrue(near[0].getTarget() == null, "the raider targeted someone, so the case proves nothing");
				helper.assertTrue(guards.stream().allMatch(guard -> ((Raider) guard).getCurrentRaid() == null), "a guard joined the raid");
				raid.stop();
			})
			.thenIdle(10)
			.thenExecute(() -> {
				List<String> stillTargeting = guards.stream().filter(guard -> guard.getTarget() != null).map(guard -> PatrolState.role(guard).toString()).toList();
				near[0].discard();
				helper.assertTrue(stillTargeting.isEmpty(), "a guard kept its target after the raid stopped: " + stillTargeting);
			})
			.thenSucceed();
	}

	/** A raider in range that targets nobody. */
	private static Raider joinNearRaider(final GameTestHelper helper, final Raid raid) {
		BlockPos bell = PatrolTestSupport.anchor(helper);
		int chunkMinX = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(bell.getX()));
		int chunkMinZ = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(bell.getZ()));
		Raider raider = EntityTypes.PILLAGER.create(helper.getLevel(), EntitySpawnReason.EVENT);
		raider.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		raider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(RAIDER_HEALTH);
		raider.setHealth(RAIDER_HEALTH);
		raid.joinRaid(helper.getLevel(), 1, raider, new BlockPos(chunkMinX + 8, bell.getY() - 1, chunkMinZ + 8), false);
		raider.setNoAi(true);
		return raider;
	}

	private static Raid registerVanillaRaid(final ServerLevel level, final VillageWorldData.VillageRecord village) {
		RaidsInvoker registry = (RaidsInvoker) level.getRaids();
		Raid raid = new Raid(village.anchor(), level.getDifficulty());
		registry.mcStellaVillages$raidMap().put(registry.mcStellaVillages$getUniqueId(), raid);
		return raid;
	}

	/** Vanilla starts a raid at its first wave. */
	private static void markStarted(final Raid raid) {
		try {
			Vanilla.STARTED.setBoolean(raid, true);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("could not start a raid", error);
		}
	}

	private static List<Mob> guards(final GameTestHelper helper) {
		return PatrolTestSupport.patrols(helper).stream()
			.filter(mob -> PatrolState.role(mob) == PatrolState.Role.PILLAGER || PatrolState.role(mob) == PatrolState.Role.RANGED_GUARD)
			.toList();
	}

	/** One wave, counted by raider type. */
	private static Map<String, Integer> composition(final Raid raid, final long seed, final Difficulty difficulty) {
		try {
			RandomSource random = RandomSource.create(mixed(seed));
			setRandom(raid, random);
			DifficultyInstance instance = new DifficultyInstance(difficulty, 0L, 0L, 0.0F);
			Map<String, Integer> counts = new LinkedHashMap<>();
			for (Object type : Vanilla.TYPES.getEnumConstants()) {
				int spawns = (Integer) Vanilla.DEFAULTS.invoke(raid, type, 1, false) + (Integer) Vanilla.BONUS.invoke(raid, type, random, 1, instance, false);
				counts.put(((Enum<?>) type).name(), spawns);
			}
			return counts;
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("could not read the wave of a raid", error);
		}
	}

	/**
	 * Consecutive seeds give nearly equal first draws.
	 * So each wave takes a mixed seed (splitmix64).
	 */
	private static long mixed(final long index) {
		long z = (index + 1L) * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Both count methods read the raid's random. */
	private static void setRandom(final Raid raid, final RandomSource random) {
		try {
			Vanilla.RANDOM.set(raid, random);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("could not seed a raid", error);
		}
	}

	/** Spawns a wave, counts it, removes it. */
	private static Map<String, Integer> spawnedWave(final ServerLevel level, final Raid raid, final BlockPos at) {
		try {
			Vanilla.SPAWN.invoke(raid, level, at);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("could not spawn a raid wave", error);
		}

		Map<String, Integer> byType = new TreeMap<>();
		for (Raider raider : List.copyOf(raid.getAllRaiders())) {
			byType.merge(BuiltInRegistries.ENTITY_TYPE.getKey(raider.getType()).toString(), 1, Integer::sum);
			raider.discard();
		}
		raid.stop();
		return byType;
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void attendanceFollowsPlayerDistance(final GameTestHelper helper) {
		String test = "attendanceFollowsPlayerDistance";
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 privatePosition = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(privatePosition.x, privatePosition.y, privatePosition.z);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().players().contains(player), "the test player has not joined the level");
			BlockPos at = player.blockPosition();
			// Two detached records differ only by their distance to the joined player.
			VillageWorldData.VillageRecord near = VillageWorldData.empty().findOrCreate(helper.getLevel().dimension(), at.offset(4, 0, 0));
			VillageWorldData.VillageRecord far = VillageWorldData.empty().findOrCreate(
				helper.getLevel().dimension(), at.offset((int) VillageRaids.ATTENDANCE_RANGE + 8, 0, 0)
			);
			boolean attendsNear = VillageRaids.attendant(helper.getLevel(), near) == player;
			boolean attendsFar = VillageRaids.attendant(helper.getLevel(), far) == player;
			PatrolTestSupport.result(test, "attendsNear=" + attendsNear + " attendsFar=" + attendsFar);
			helper.assertTrue(attendsNear && !attendsFar,
				"attendance did not follow the player's distance: near=" + attendsNear + " far=" + attendsFar);
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}

	private static VillageWorldData.VillageRecord village(final GameTestHelper helper) {
		UUID id = PatrolTestSupport.villageId(helper);
		return VillageWorldData.get(helper.getLevel()).village(id).orElseThrow();
	}
}
