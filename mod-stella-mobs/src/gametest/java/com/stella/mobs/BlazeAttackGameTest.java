package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.BlazeAttackGoal;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

/**
 * Every blaze fires one fireball on a steady beat, every two seconds, or every one and a half for an
 * elite, and each ball bursts in an explosion that harms bodies and never the ground. A boss blaze
 * calls an ordinary blaze on one attack in ten, up to its cap.
 */
public class BlazeAttackGameTest {
	private static final String TEST = "blazeAttack";
	private static final int SAMPLES = 100_000;
	private static final Map<UUID, List<Integer>> FIRED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Blaze>> CALLED = new ConcurrentHashMap<>();
	/** Explosion hits a blaze's ball dealt, keyed by the victim. */
	private static final Map<UUID, List<UUID>> BLASTED = new ConcurrentHashMap<>();

	static {
		BlazeAttackGoal.setRecorder(new BlazeAttackGoal.Recorder() {
			@Override
			public void fired(final Blaze blaze, final int serverTick) {
				FIRED.computeIfAbsent(blaze.getUUID(), key -> new CopyOnWriteArrayList<>()).add(serverTick);
			}

			@Override
			public void summoned(final Blaze boss, final Blaze called) {
				CALLED.computeIfAbsent(boss.getUUID(), key -> new CopyOnWriteArrayList<>()).add(called);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
			if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() instanceof Blaze blaze) {
				BLASTED.computeIfAbsent(victim.getUUID(), key -> new CopyOnWriteArrayList<>()).add(blaze.getUUID());
			}
		});
	}

	@GameTest(maxTicks = 300)
	public void anOrdinaryBlazeFiresEveryTwoSeconds(final GameTestHelper helper) {
		assertBeat(helper, "anOrdinaryBlazeFiresEveryTwoSeconds", false, BlazeAttackGoal.ORDINARY_INTERVAL_TICKS);
	}

	@GameTest(maxTicks = 300)
	public void anEliteBlazeFiresEveryOneAndAHalfSeconds(final GameTestHelper helper) {
		assertBeat(helper, "anEliteBlazeFiresEveryOneAndAHalfSeconds", true, BlazeAttackGoal.ELITE_INTERVAL_TICKS);
	}

	@GameTest(maxTicks = 300)
	public void aBlazeShotExplodesWithoutHarmingTheGround(final GameTestHelper helper) {
		Blaze blaze = blaze(helper, false, false);
		Zombie target = target(helper, blaze);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(2 + dx, 1, 7 + dz, Blocks.GLASS);
				}
			}
		}
		int glassBefore = count(helper, Blocks.GLASS);
		helper.succeedWhen(() -> {
			helper.assertTrue(BLASTED.getOrDefault(target.getUUID(), List.of()).contains(blaze.getUUID()), "no blast from the blaze has reached the target yet");
			int glass = count(helper, Blocks.GLASS);
			int fire = count(helper, Blocks.FIRE);
			helper.assertTrue(glass == glassBefore && fire == 0, "the blast broke the ground: glass " + glassBefore + " -> " + glass + ", fire " + fire);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT blasts={} glass={} fire={}", TEST, BLASTED.get(target.getUUID()).size(), glass, fire);
		});
	}

	@GameTest
	public void theBossSummonChanceIsWhatShips(final GameTestHelper helper) {
		int summons = 0;
		for (int i = 0; i < SAMPLES; i++) {
			summons += BlazeAttackGoal.summons(RandomSource.create(i)) ? 1 : 0;
		}
		double rate = (double) summons / SAMPLES;
		double band = 4.0 * Math.sqrt(BlazeAttackGoal.BOSS_SUMMON_CHANCE * (1.0 - BlazeAttackGoal.BOSS_SUMMON_CHANCE) / SAMPLES);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT samples={} summonRate={}", TEST, SAMPLES, rate);
		helper.assertTrue(Math.abs(rate - BlazeAttackGoal.BOSS_SUMMON_CHANCE) <= band, "the boss summon rate was " + rate);
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void aBossBlazeCallsOrdinaryBlazesUpToItsCap(final GameTestHelper helper) {
		Blaze boss = blaze(helper, true, true);
		target(helper, boss);
		BlazeAttackGoal.forceSummonFor(boss.getUUID());
		// The cap is on living called blazes: a called blaze a blast kills leaves room for the next.
		int[] mostAlive = {0};
		helper.onEachTick(() -> mostAlive[0] = Math.max(mostAlive[0], (int) CALLED.getOrDefault(boss.getUUID(), List.of()).stream().filter(Blaze::isAlive).count()));
		helper.succeedWhen(() -> {
			int attacks = FIRED.getOrDefault(boss.getUUID(), List.of()).size();
			helper.assertTrue(attacks >= BlazeAttackGoal.BOSS_SUMMON_CAP + 2, "the boss has attacked " + attacks + " times");
			List<Blaze> called = CALLED.getOrDefault(boss.getUUID(), List.of());
			helper.assertTrue(called.size() >= BlazeAttackGoal.BOSS_SUMMON_CAP, "the boss called " + called.size() + " blazes over " + attacks + " attacks");
			helper.assertTrue(mostAlive[0] == BlazeAttackGoal.BOSS_SUMMON_CAP, "at most " + mostAlive[0] + " called blazes lived at once, not " + BlazeAttackGoal.BOSS_SUMMON_CAP);
			for (Blaze blaze : called) {
				helper.assertTrue(!Elite.isElite(blaze) && !EliteState.isBoss(blaze), "a called blaze is not an ordinary blaze");
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT attacks={} called={} mostAlive={} cap={}", TEST, attacks, called.size(), mostAlive[0], BlazeAttackGoal.BOSS_SUMMON_CAP);
			called.forEach(Blaze::discard);
		});
	}

	@GameTest(maxTicks = 100)
	public void aCalledBlazeAppearsBesideItsBoss(final GameTestHelper helper) {
		Blaze boss = blaze(helper, true, true);
		target(helper, boss);
		BlazeAttackGoal.forceSummonFor(boss.getUUID());
		helper.succeedWhen(() -> {
			List<Blaze> called = CALLED.getOrDefault(boss.getUUID(), List.of());
			helper.assertTrue(!called.isEmpty(), "the boss has called no blaze yet");
			float distance = called.get(0).distanceTo(boss);
			helper.assertTrue(distance <= 4.0F, "the first called blaze appeared " + distance + " blocks away");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT firstCalledDistance={}", TEST, distance);
			called.forEach(Blaze::discard);
		});
	}

	@GameTest(maxTicks = 300)
	public void anEliteThatIsNotABossCallsNoBlaze(final GameTestHelper helper) {
		Blaze elite = blaze(helper, true, false);
		target(helper, elite);
		BlazeAttackGoal.forceSummonFor(elite.getUUID());
		helper.succeedWhen(() -> {
			int attacks = FIRED.getOrDefault(elite.getUUID(), List.of()).size();
			helper.assertTrue(attacks >= 3, "the elite has attacked " + attacks + " times");
			helper.assertTrue(CALLED.getOrDefault(elite.getUUID(), List.of()).isEmpty(), "an elite that is not a boss called a blaze");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT eliteAttacks={} called=0", TEST, attacks);
		});
	}

	private static void assertBeat(final GameTestHelper helper, final String test, final boolean elite, final int interval) {
		Blaze blaze = blaze(helper, elite, false);
		target(helper, blaze);
		helper.succeedWhen(() -> {
			List<Integer> ticks = FIRED.getOrDefault(blaze.getUUID(), List.of());
			helper.assertTrue(ticks.size() >= 4, "the blaze has fired " + ticks.size() + " times");
			List<Integer> gaps = new ArrayList<>();
			for (int i = 1; i < ticks.size(); i++) {
				gaps.add(ticks.get(i) - ticks.get(i - 1));
			}
			helper.assertTrue(gaps.stream().allMatch(gap -> gap == interval), "the blaze fired " + gaps + " ticks apart, not every " + interval);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT elite={} gaps={}", test, elite, gaps);
		});
	}

	/** Marked and installed by hand, per the fixture rule: no config write, no chance pin. */
	private static Blaze blaze(final GameTestHelper helper, final boolean elite, final boolean boss) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Blaze blaze = helper.spawnMob(EntityTypes.BLAZE, 2, 3, 2).spawn();
		if (elite) {
			Elite.mark(blaze, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		}
		if (boss) {
			EliteState.setBoss(blaze, true);
		}
		BehaviorInstaller.installBlazeAttack(blaze);
		return blaze;
	}

	// A durable target the blaze must keep firing at: a dead one would stop the beat and read as a slow one.
	private static Zombie target(final GameTestHelper helper, final Blaze blaze) {
		Zombie target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		target.setHealth(1000.0F);
		helper.onEachTick(() -> {
			target.setInvulnerableTime(0);
			blaze.setTarget(target);
		});
		return target;
	}

	private static int count(final GameTestHelper helper, final net.minecraft.world.level.block.Block block) {
		int found = 0;
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y <= 3; y++) {
				for (int z = 0; z < 10; z++) {
					found += helper.getBlockState(new BlockPos(x, y, z)).is(block) ? 1 : 0;
				}
			}
		}
		return found;
	}
}
