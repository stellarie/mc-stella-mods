package com.stella.mobs;

import com.stella.mobs.behavior.BloodMoon;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Surprise, mobs: every seventh night is a Blood Moon. Hostiles that spawn under it roll elite three
 * times as often and carry Strength until dawn, and the moon's rise and set are announced. The spawn
 * cases run in their own clock environment, a separate batch, so the moon never leaks into another test.
 */
public class BloodMoonGameTest {
	private static final String ENVIRONMENT = "mod-stella-mobs-test:blood_moon";
	/** The environment's clock: the night of day seven, which the calendar makes a Blood Moon. */
	private static final long BLOOD_MOON_CLOCK = 162_000L;
	private static final long MIDDAY = 6_000L;
	private static final long MIDNIGHT = 18_000L;
	private static final int CALENDAR_DAYS = 28;
	private static final int SPAWN_SAMPLE = 20;
	/** A base chance the multiplier lifts to certainty, so the case is deterministic. */
	private static final double BASE_ELITE_CHANCE = 0.34;
	private static final String BLOODY = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.34, "boss_chance": 0.0, "spawn_safety_radius": 0.0 }
		}""";
	private static final List<Boolean> CHANGES = new CopyOnWriteArrayList<>();
	private static final Set<UUID> EMPOWERED = ConcurrentHashMap.newKeySet();

	static {
		BloodMoon.setRecorder(new BloodMoon.Recorder() {
			@Override
			public void changed(final net.minecraft.server.level.ServerLevel level, final boolean risen, final int players) {
				CHANGES.add(risen);
			}

			@Override
			public void empowered(final Mob mob) {
				EMPOWERED.add(mob.getUUID());
			}
		});
	}

	@GameTest
	public void theBloodMoonRisesOnEverySeventhNight(final GameTestHelper helper) {
		List<Integer> bloodNights = new ArrayList<>();
		for (int day = 0; day < CALENDAR_DAYS; day++) {
			long dayStart = day * BloodMoon.DAY_TICKS;
			helper.assertFalse(BloodMoon.isBloodMoon(dayStart + MIDDAY), "day " + day + " has a Blood Moon at noon");
			if (BloodMoon.isBloodMoon(dayStart + MIDNIGHT)) {
				bloodNights.add(day);
			}
		}
		List<Integer> expected = new ArrayList<>();
		for (int day = BloodMoon.PERIOD_NIGHTS - 1; day < CALENDAR_DAYS; day += BloodMoon.PERIOD_NIGHTS) {
			expected.add(day);
		}
		helper.assertTrue(bloodNights.equals(expected), "Blood Moons fell on days " + bloodNights + ", not " + expected);
		helper.assertTrue(BloodMoon.isBloodMoon(BLOOD_MOON_CLOCK), "the test environment's clock is not a Blood Moon");
		result("calendar", "nights=" + CALENDAR_DAYS + " bloodMoons=" + bloodNights + " rate=1/" + BloodMoon.PERIOD_NIGHTS);
		helper.succeed();
	}

	@GameTest(environment = ENVIRONMENT, maxTicks = 100)
	public void hostilesUnderTheBloodMoonRollEliteThreeTimesAsOftenAndCarryStrength(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(BLOODY));
		Arena.buildFloor(helper);
		helper.assertTrue(BASE_ELITE_CHANCE * BloodMoon.ELITE_CHANCE_MULTIPLIER >= 1.0, "the base chance no longer reaches certainty, so the case is not deterministic");
		List<Zombie> zombies = new ArrayList<>();
		for (int i = 0; i < SPAWN_SAMPLE; i++) {
			Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1 + i % 6, 3, 1 + i / 6).spawnReason(EntitySpawnReason.NATURAL).spawn();
			zombie.setPersistenceRequired();
			zombies.add(zombie);
		}
		long elites = zombies.stream().filter(Elite::isElite).count();
		long strong = zombies.stream().filter(zombie -> {
			MobEffectInstance strength = zombie.getEffect(MobEffects.STRENGTH);
			return strength != null && strength.getDuration() > 0 && strength.getDuration() <= BloodMoon.ticksUntilDawn(BLOOD_MOON_CLOCK) + 1;
		}).count();
		helper.assertTrue(elites == SPAWN_SAMPLE, elites + " of " + SPAWN_SAMPLE + " zombies rolled elite under the Blood Moon");
		helper.assertTrue(strong == SPAWN_SAMPLE && zombies.stream().allMatch(zombie -> EMPOWERED.contains(zombie.getUUID())), strong + " of " + SPAWN_SAMPLE + " zombies carry the moon's Strength until dawn");
		result("spawns", "elites=" + elites + "/" + SPAWN_SAMPLE + " strength=" + strong);
		helper.succeed();
	}

	@GameTest(environment = ENVIRONMENT, maxTicks = 100)
	public void aPeacefulMobIsUntouchedByTheBloodMoon(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(BLOODY));
		Arena.buildFloor(helper);
		Cow cow = helper.spawnMob(EntityTypes.COW, 3, 3, 3).spawnReason(EntitySpawnReason.NATURAL).spawn();
		helper.assertFalse(cow.hasEffect(MobEffects.STRENGTH) || EMPOWERED.contains(cow.getUUID()), "the Blood Moon empowered a cow");
		result("peaceful", "strength=false");
		helper.succeed();
	}

	@GameTest(environment = ENVIRONMENT, maxTicks = 100)
	public void theBloodMoonRiseIsAnnounced(final GameTestHelper helper) {
		helper.succeedWhen(() -> {
			helper.assertTrue(CHANGES.contains(Boolean.TRUE), "no rise was announced: " + CHANGES);
			result("announce", "changes=" + CHANGES);
		});
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test=bloodMoon.{} RESULT {}", test, detail);
	}
}
