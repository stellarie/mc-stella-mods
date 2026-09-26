package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.EscortSquad;
import com.stella.mobs.behavior.FollowAllyGoal;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.WitchElite;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RollSpec;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.SpawnScaling;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The elite framework on a headless rig: the roll, the marker that cannot compound, the kit, the escort
 * squad and its tier, and the shipped default rate. Every assertion is bound to the shipped constant.
 */
public class EliteFrameworkGameTest {
	private static final String ALWAYS_ELITE = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 1.0 }
		}""";
	private static final String ALWAYS_ROLL = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 1.0, "elite_chance": 1.0 }
		}""";
	private static final String NEVER_ELITE = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";
	private static final String WITCH_OVERRIDE = """
		{
			"version": 1,
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.20,
				"entities": { "minecraft:zombie": { "elite_chance": 0.75 } }
			}
		}""";
	private static final int SPAWN_X = 3;
	private static final int SPAWN_Y = 3;
	private static final int SPAWN_Z = 3;
	private static final int MAX_TICKS = 200;
	private static final double ESCORT_BOX = 8.0;
	private static final double EPSILON = 1.0e-6;
	private static final float ZOMBIE_BASE_HEALTH = 20.0F;
	private static final int RATE_ROLLS = 4000;
	private static final long RATE_SEED = 20260923L;
	private static final long REQUEUE_TICK = 20L;
	private static final long SETTLE_TICKS = 20L;

	@GameTest(maxTicks = MAX_TICKS)
	public void eliteRollsWithMarkerAndDoubledHealth(final GameTestHelper helper) {
		Zombie elite = spawn(helper, ALWAYS_ELITE);
		boolean eliteFlag = Elite.isElite(elite);
		float health = elite.getMaxHealth();
		// R12 scales the maximum too, so divide it out and assert the elite multiplier alone.
		double unscaledHealth = health / SpawnScaling.healthMultiplierOf(elite);
		float scale = elite.getScale();
		boolean markedOnce = elite.getAttribute(Attributes.SCALE).getModifier(Elite.SCALE_ID) != null;
		double bonus = markedOnce ? elite.getAttribute(Attributes.SCALE).getModifier(Elite.SCALE_ID).amount() : 0.0;

		result(
			"eliteRollsWithMarkerAndDoubledHealth",
			"elite=" + eliteFlag + " health=" + health + " unscaled=" + unscaledHealth + " expected="
				+ ZOMBIE_BASE_HEALTH * Elite.HEALTH_MULTIPLIER + " scale=" + scale
				+ " bonus=" + bonus + " tier=" + Elite.kitTier(elite)
		);

		helper.assertTrue(eliteFlag, "the elite roll did not mark the zombie");
		helper.assertTrue(
			Math.abs(unscaledHealth - ZOMBIE_BASE_HEALTH * Elite.HEALTH_MULTIPLIER) < 1.0e-4,
			"the elite has " + unscaledHealth + " health unscaled, not " + ZOMBIE_BASE_HEALTH * Elite.HEALTH_MULTIPLIER
				+ " (maximum " + health + ")"
		);
		helper.assertTrue(
			Math.abs(scale - (1.0F + (float) Elite.SCALE_BONUS)) < EPSILON,
			"the elite scale is " + scale + ", not " + (1.0F + Elite.SCALE_BONUS)
		);
		helper.assertTrue(
			Math.abs(bonus - Elite.SCALE_BONUS) < EPSILON, "the scale modifier carries " + bonus
		);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void eliteKitIsOneTierAndEnchanted(final GameTestHelper helper) {
		Zombie elite = spawn(helper, ALWAYS_ELITE);
		GearTier tier = Elite.kitTier(elite);
		boolean tierIsKitTier = Elite.KIT_TIERS.contains(tier);
		boolean armourAtTier = true;
		boolean dropAtEliteRate = true;
		boolean enchanted = true;

		for (EquipmentSlot slot : GearTier.armorSlots()) {
			ItemStack stack = elite.getItemBySlot(slot);
			armourAtTier &= stack.is(tier.armorFor(slot));
			enchanted &= stack.isEnchanted();
			dropAtEliteRate &= Math.abs(elite.getDropChances().byEquipment(slot) - Elite.KIT_DROP_CHANCE) < EPSILON;
		}

		boolean weaponAtTier = elite.getMainHandItem().is(tier.meleeWeapon())
			|| elite.getMainHandItem().is(tier.rangedWeapon());

		result(
			"eliteKitIsOneTierAndEnchanted",
			"tier=" + tier + " kitTier=" + tierIsKitTier + " armour=" + armourAtTier + " enchanted=" + enchanted
				+ " drop=" + elite.getDropChances().byEquipment(EquipmentSlot.CHEST) + " weapon=" + weaponAtTier
		);

		helper.assertTrue(tierIsKitTier, "the elite kit rolled outside the elite tier list: " + tier);
		helper.assertTrue(armourAtTier, "the elite armour is not all " + tier);
		helper.assertTrue(enchanted, "the elite armour carries no enchantment");
		helper.assertTrue(dropAtEliteRate, "the elite drop chance is not " + Elite.KIT_DROP_CHANCE);
		helper.assertTrue(weaponAtTier, "the elite weapon is not " + tier);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void eliteMarkerCannotCompound(final GameTestHelper helper) {
		Zombie elite = spawn(helper, ALWAYS_ELITE);
		float healthBefore = elite.getMaxHealth();
		GearTier tierBefore = Elite.kitTier(elite);
		boolean secondMark = Elite.mark(elite, tierBefore, Elite.HEALTH_MULTIPLIER);
		float healthAfter = elite.getMaxHealth();
		float scaleAfter = elite.getScale();
		int scaleModifiers = elite.getAttribute(Attributes.SCALE).getModifiers().size();

		result(
			"eliteMarkerCannotCompound",
			"secondMark=" + secondMark + " healthBefore=" + healthBefore + " healthAfter=" + healthAfter
				+ " scale=" + scaleAfter + " scaleModifiers=" + scaleModifiers
		);

		helper.assertFalse(secondMark, "a second mark claimed the mob again");
		helper.assertTrue(
			Math.abs(healthBefore - healthAfter) < EPSILON,
			"a second mark moved the health from " + healthBefore + " to " + healthAfter
		);
		helper.assertTrue(
			Math.abs(scaleAfter - (1.0F + (float) Elite.SCALE_BONUS)) < EPSILON,
			"a second mark moved the scale to " + scaleAfter
		);
		helper.assertValueEqual(Integer.valueOf(scaleModifiers), Integer.valueOf(1), "scale modifier count");
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void zeroChanceSpawnsNoEliteAndNoEscorts(final GameTestHelper helper) {
		Zombie plain = spawn(helper, NEVER_ELITE);
		helper.runAfterDelay(40L, () -> {
			List<Zombie> others = escortsOf(helper, plain);
			boolean eliteFlag = Elite.isElite(plain);

			result(
				"zeroChanceSpawnsNoEliteAndNoEscorts",
				"elite=" + eliteFlag + " scale=" + plain.getScale() + " modifiers="
					+ plain.getAttribute(Attributes.SCALE).getModifiers().size() + " escorts=" + others.size()
			);

			helper.assertFalse(eliteFlag, "a zero elite chance still marked the zombie");
			helper.assertTrue(
				Math.abs(plain.getScale() - 1.0F) < EPSILON, "a zero elite chance still changed the scale"
			);
			helper.assertValueEqual(
				Integer.valueOf(plain.getAttribute(Attributes.SCALE).getModifiers().size()),
				Integer.valueOf(0),
				"scale modifier count"
			);
			helper.assertValueEqual(Integer.valueOf(others.size()), Integer.valueOf(0), "escort count");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void escortSquadArrivesAndIsNeverElite(final GameTestHelper helper) {
		Zombie elite = spawn(helper, ALWAYS_ELITE);

		helper.succeedWhen(() -> {
			List<Zombie> escorts = escortsOf(helper, elite);
			helper.assertValueEqual(
				Integer.valueOf(escorts.size()), Integer.valueOf(EscortSquad.ESCORT_COUNT), "escort count"
			);
			Zombie escort = escorts.getFirst();
			// The escort must be a fully installed mob, not a bare summon.
			boolean installed = escort.getGoalSelector().getAvailableGoals().stream()
				.anyMatch(wrapped -> wrapped.getGoal() instanceof RetreatGoal);
			// The guard is exercised directly too: the same call
			// the load hook makes, on a real escort, at a chance of 1.0.
			Elite.rollAtSpawn(escort, helper.getLevel(), 1.0, true);
			boolean anyElite = escorts.stream().anyMatch(Elite::isElite);
			boolean allSummoned = escorts.stream().allMatch(EliteState::isSummoned);
			boolean anyScaled = escorts.stream().anyMatch(candidate -> Math.abs(candidate.getScale() - 1.0F) > EPSILON);

			if (escorts.size() == EscortSquad.ESCORT_COUNT && helper.getTick() < 5L) {
				result(
					"escortSquadArrivesAndIsNeverElite",
					"escorts=" + escorts.size() + " eliteAmong=" + anyElite + " summoned=" + allSummoned
						+ " scaled=" + anyScaled + " installed=" + installed + " tick=" + helper.getTick()
				);
			}

			helper.assertFalse(anyElite, "an escort rolled elite itself at a chance of 1.0");
			helper.assertTrue(allSummoned, "an escort is not flagged summoned");
			helper.assertFalse(anyScaled, "an escort carries the elite scale");
			helper.assertTrue(installed, "the escort never received the mod's goals");
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void requeuedSquadNeverGrowsPastTheCap(final GameTestHelper helper) {
		Zombie elite = spawn(helper, ALWAYS_ELITE);
		int[] first = { -1 };

		// Two more requests for the same elite must not add a single escort.
		helper.runAfterDelay(REQUEUE_TICK, () -> {
			first[0] = escortsOf(helper, elite).size();
			EscortSquad.queue(elite, Elite.kitTier(elite));
			EscortSquad.queue(elite, Elite.kitTier(elite));
		});
		helper.runAfterDelay(REQUEUE_TICK + SETTLE_TICKS, () -> {
			int after = escortsOf(helper, elite).size();
			result("requeuedSquadNeverGrowsPastTheCap", "first=" + first[0] + " after=" + after + " cap=" + EscortSquad.ESCORT_COUNT);
			helper.assertValueEqual(Integer.valueOf(after), Integer.valueOf(first[0]), "escorts after two more requests");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void requeueRefillsOnlyTheMissingEscorts(final GameTestHelper helper) {
		Zombie elite = spawn(helper, ALWAYS_ELITE);
		int[] first = { -1 };

		// Remove one escort, ask again, and the squad must return to its size and not overshoot it.
		helper.runAfterDelay(REQUEUE_TICK, () -> {
			List<Zombie> escorts = escortsOf(helper, elite);
			first[0] = escorts.size();
			escorts.getFirst().discard();
			EscortSquad.queue(elite, Elite.kitTier(elite));
		});
		helper.runAfterDelay(REQUEUE_TICK + SETTLE_TICKS, () -> {
			int after = escortsOf(helper, elite).size();
			result("requeueRefillsOnlyTheMissingEscorts", "first=" + first[0] + " after=" + after);
			helper.assertValueEqual(Integer.valueOf(after), Integer.valueOf(first[0]), "escorts after one loss and one request");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void taggedSpawnSkipsEverySpawnRoll(final GameTestHelper helper) {
		boolean[] rolled = rolledState(helper, true);
		result("taggedSpawnSkipsEverySpawnRoll", describe(rolled));
		helper.assertFalse(rolled[0] || rolled[1] || rolled[2], "a tagged natural zombie still took a spawn roll: " + describe(rolled));
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void untaggedSpawnStillRolls(final GameTestHelper helper) {
		boolean[] rolled = rolledState(helper, false);
		result("untaggedSpawnStillRolls", describe(rolled));
		helper.assertTrue(rolled[0], "an untagged natural zombie skipped the elite roll at a chance of 1.0: " + describe(rolled));
		helper.succeed();
	}

	/** One natural zombie under certain gear and elite rolls; only the tag differs between the two cases. */
	private static boolean[] rolledState(final GameTestHelper helper, final boolean tagged) {
		GearConfig previous = GearConfigStore.get();
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);

		try {
			GearConfigStore.set(GearConfigParser.parse(ALWAYS_ROLL));
			Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
			Vec3 at = helper.absoluteVec(new Vec3(SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5));
			zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
			if (tagged) {
				zombie.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
			}
			helper.getLevel().addFreshEntity(zombie);
			boolean elite = Elite.isElite(zombie);
			boolean gear = !zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || !zombie.getMainHandItem().isEmpty();
			boolean scaled = zombie.getAttribute(Attributes.MAX_HEALTH).getModifier(SpawnScaling.HEALTH_ID) != null;
			zombie.discard();
			return new boolean[] { elite, gear, scaled };
		} finally {
			GearConfigStore.set(previous);
		}
	}

	private static String describe(final boolean[] rolled) {
		return "elite=" + rolled[0] + " gear=" + rolled[1] + " scaled=" + rolled[2];
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void anOrdinaryZombieIsNotCountedAsAnEscort(final GameTestHelper helper) {
		Zombie elite = spawn(helper, ALWAYS_ELITE);

		// The squad drains a tick after the queue, so the count waits for it rather than sampling a fixed
		// tick: the wanderer clause comes after, when the state has settled.
		helper.succeedWhen(() -> helper.assertValueEqual(
			Integer.valueOf(escortsOf(helper, elite).size()), Integer.valueOf(EscortSquad.ESCORT_COUNT),
			"escort count before the wanderer"
		));
		helper.runAfterDelay(20L, () -> {
			int before = escortsOf(helper, elite).size();
			// A plain zombie standing in the same box: not summoned, so not an escort. The config is
			// pinned to zero elite chance first, or the wanderer would roll elite and queue its own squad.
			GearConfigStore.set(GearConfigParser.parse(NEVER_ELITE));
			Zombie wanderer = helper.spawnMob(EntityTypes.ZOMBIE, SPAWN_X + 2, SPAWN_Y, SPAWN_Z + 2).spawn();
			wanderer.setPersistenceRequired();
			int after = escortsOf(helper, elite).size();
			int nearby = helper.getLevel()
				.getEntitiesOfClass(
					Zombie.class, elite.getBoundingBox().inflate(ESCORT_BOX), candidate -> candidate != elite
				)
				.size();

			result(
				"escortIdentity",
				"before=" + before + " nearbyZombies=" + nearby + " counted=" + after
					+ " wandererSummoned=" + EliteState.isSummoned(wanderer)
					+ " wandererVehicle=" + (wanderer.getVehicle() == null ? "none" : "yes")
			);

			helper.assertValueEqual(
				Integer.valueOf(before), Integer.valueOf(EscortSquad.ESCORT_COUNT), "escort count before the wanderer"
			);
			helper.assertValueEqual(
				Integer.valueOf(after), Integer.valueOf(EscortSquad.ESCORT_COUNT),
				"escort count with an ordinary zombie in the box"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void escortKitIsOneGradeBelowTheElite(final GameTestHelper helper) {
		Zombie elite = spawn(helper, ALWAYS_ELITE);
		GearTier eliteTier = Elite.kitTier(elite);
		// The spec restated, not the function under test: netherite is escorted by diamond, diamond by iron.
		GearTier expected = eliteTier == GearTier.NETHERITE ? GearTier.DIAMOND : GearTier.IRON;

		helper.succeedWhen(() -> {
			List<Zombie> escorts = escortsOf(helper, elite);
			helper.assertValueEqual(
				Integer.valueOf(escorts.size()), Integer.valueOf(EscortSquad.ESCORT_COUNT), "escort count"
			);
			Zombie escort = escorts.getFirst();
			boolean chestAtTier = escort.getItemBySlot(EquipmentSlot.CHEST).is(expected.armorFor(EquipmentSlot.CHEST));
			boolean dropAtEscortRate = Math.abs(
				escort.getDropChances().byEquipment(EquipmentSlot.CHEST) - EscortSquad.DROP_CHANCE
			) < EPSILON;

			result(
				"escortKitIsOneGradeBelowTheElite",
				"eliteTier=" + eliteTier + " escortTier=" + expected + " chest="
					+ chestAtTier + " drop=" + escort.getDropChances().byEquipment(EquipmentSlot.CHEST)
			);

			helper.assertTrue(chestAtTier, "the escort chest is not " + expected);
			helper.assertTrue(dropAtEscortRate, "the escort drop chance is not " + EscortSquad.DROP_CHANCE);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void eliteChanceIsParsedGloballyAndPerType(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(WITCH_OVERRIDE));
		double global = GearConfigStore.get().rollDefaults().eliteChance();
		double zombie = GearConfigStore.get().rollFor(Identifier.parse("minecraft:zombie")).eliteChance();
		double skeleton = GearConfigStore.get().rollFor(Identifier.parse("minecraft:skeleton")).eliteChance();

		result(
			"eliteChanceIsParsedGloballyAndPerType",
			"global=" + global + " zombie=" + zombie + " skeleton=" + skeleton
		);

		helper.assertTrue(Math.abs(global - 0.20) < EPSILON, "the global elite chance is " + global);
		helper.assertTrue(Math.abs(zombie - 0.75) < EPSILON, "the zombie override is " + zombie);
		helper.assertTrue(Math.abs(skeleton - 0.20) < EPSILON, "the skeleton inherited " + skeleton);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void shippedDefaultCarriesTheEliteChance(final GameTestHelper helper) {
		Path file = GearConfigStore.path().resolveSibling("gametest-elite-default.json");

		try {
			Files.deleteIfExists(file);
			GearConfigStore.loadFrom(file);
			String written = Files.readString(file, StandardCharsets.UTF_8);
			double shipped = GearConfigStore.get().rollFor(Identifier.parse("minecraft:skeleton")).eliteChance();
			double witch = GearConfigStore.get().rollFor(Identifier.parse("minecraft:witch")).eliteChance();

			result(
				"shippedDefaultCarriesTheEliteChance",
				"shipped=" + shipped + " constant=" + RollSpec.DEFAULT_ELITE_CHANCE + " witch=" + witch
					+ " named=" + written.contains("\"elite_chance\"")
			);

			helper.assertTrue(written.contains("\"elite_chance\""), "the shipped default config names no elite chance");
			helper.assertTrue(
				Math.abs(shipped - RollSpec.DEFAULT_ELITE_CHANCE) < EPSILON,
				"the shipped default elite chance is " + shipped + ", not " + RollSpec.DEFAULT_ELITE_CHANCE
			);
			helper.assertTrue(
				Math.abs(witch - WitchElite.DEFAULT_CHANCE) < EPSILON,
				"the shipped witch elite chance is " + witch + ", not her shipped " + WitchElite.DEFAULT_CHANCE
			);
		} catch (IOException e) {
			helper.fail("config store file error: " + e);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void eliteRateMatchesTheShippedChance(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(RATE_SEED);
		int elites = 0;

		for (int i = 0; i < RATE_ROLLS; i++) {
			if (Elite.roll(random, RollSpec.DEFAULT_ELITE_CHANCE)) {
				elites++;
			}
		}

		double rate = (double) elites / RATE_ROLLS;
		result(
			"eliteRateMatchesTheShippedChance",
			"rolls=" + RATE_ROLLS + " elites=" + elites + " rate=" + String.format(Locale.ROOT, "%.4f", rate)
				+ " chance=" + RollSpec.DEFAULT_ELITE_CHANCE
		);

		helper.assertTrue(
			Math.abs(rate - RollSpec.DEFAULT_ELITE_CHANCE) < 0.02,
			"the measured elite rate " + rate + " is not near the shipped " + RollSpec.DEFAULT_ELITE_CHANCE
		);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void summonedMobNeverRollsElite(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(ALWAYS_ELITE));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		Zombie summoned = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.SPAWNER);
		if (summoned == null) {
			helper.fail("the test zombie could not be created");
			return;
		}

		summoned.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 3.0, 3.5)), 0.0F, 0.0F);
		EliteState.setSummoned(summoned, true);
		Elite.rollAtSpawn(summoned, helper.getLevel(), 1.0, true);

		helper.runAfterDelay(5L, () -> {
			// Escorts only, by identity: a wandering zombie in the box is not a queued escort. This
			// query used to accept any zombie, so a neighbour made a summon look like it queued a squad.
			List<Zombie> queued = summonedNear(helper, summoned);
			boolean eliteFlag = Elite.isElite(summoned);

			// A non-zero count here is either a real escort or the neighbourhood, so name every mob.
			if (!queued.isEmpty()) {
				for (Zombie candidate : queued) {
					StellaMobs.LOGGER.info(
						"[BEHAVIOR] test=summonNeighbours RESULT candidate={} distance={} summoned={} vehicle={} "
							+ "chest={} drop={} eliteFlag={} isSubject={} tick={}",
						candidate.getType(),
						String.format(Locale.ROOT, "%.2f", summoned.distanceTo(candidate)),
						EliteState.isSummoned(candidate),
						candidate.getVehicle() == null ? "none" : candidate.getVehicle().getType(),
						candidate.getItemBySlot(EquipmentSlot.CHEST),
						candidate.getDropChances().byEquipment(EquipmentSlot.CHEST),
						Elite.isElite(candidate),
						candidate == summoned,
						helper.getTick()
					);
				}
			}

			result(
				"summonedMobNeverRollsElite",
				"chance=1.0 elite=" + eliteFlag + " scale=" + summoned.getScale() + " escortsQueued=" + queued.size()
			);

			helper.assertFalse(eliteFlag, "a summoned mob rolled elite at a chance of 1.0");
			helper.assertValueEqual(Integer.valueOf(queued.size()), Integer.valueOf(0), "escorts queued by a summon");
			helper.succeed();
		});
	}

	private static Zombie spawn(final GameTestHelper helper, final String config) {
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, SPAWN_X, SPAWN_Y, SPAWN_Z).spawn();
		zombie.setPersistenceRequired();
		return zombie;
	}

	/**
	 * The elite's escorts, by identity rather than by neighbourhood: only a summoned mob counts, because
	 * {@link EscortSquad} flags every escort before it enters the level. A wandering zombie or a jockey
	 * inside the box is not an escort. While the count is wrong the method names every candidate it saw.
	 */
	private static List<Zombie> escortsOf(final GameTestHelper helper, final Zombie elite) {
		List<Zombie> candidates = helper.getLevel()
			.getEntitiesOfClass(Zombie.class, elite.getBoundingBox().inflate(ESCORT_BOX), candidate -> candidate != elite);
		// The box reaches into neighbouring arenas, and their escorts are summoned too. The escort that
		// belongs to THIS elite is the one whose follow goal names it, so the count cannot drift.
		List<Zombie> escorts = candidates.stream()
			.filter(EliteState::isSummoned)
			.filter(candidate -> follows(candidate, elite))
			.toList();

		if (escorts.size() != EscortSquad.ESCORT_COUNT && CANDIDATE_LOGS.getAndIncrement() < CANDIDATE_LOG_CAP) {
			if (candidates.isEmpty()) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=escortCandidates RESULT elite={} candidates=0 tick={}",
					Elite.kitTier(elite), helper.getTick()
				);
			}

			for (Zombie candidate : candidates) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=escortCandidates RESULT eliteTier={} candidate={} distance={} summoned={} "
						+ "vehicle={} chest={} drop={} eliteFlag={} baby={} tick={}",
					Elite.kitTier(elite),
					candidate.getType(),
					String.format(Locale.ROOT, "%.2f", elite.distanceTo(candidate)),
					EliteState.isSummoned(candidate),
					candidate.getVehicle() == null ? "none" : candidate.getVehicle().getType(),
					candidate.getItemBySlot(EquipmentSlot.CHEST),
					candidate.getDropChances().byEquipment(EquipmentSlot.CHEST),
					Elite.isElite(candidate),
					candidate.isBaby(),
					helper.getTick()
				);
			}
		}

		return escorts;
	}

	/** True when this mob carries the follow goal bound to that elite, which is the escort's identity. */
	private static boolean follows(final Mob candidate, final Mob elite) {
		for (WrappedGoal wrapped : candidate.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof FollowAllyGoal ally && ally.leader() == elite) {
				return true;
			}
		}

		return false;
	}

	/** The same identity rule for the summoned-mob test: it counts escorts, not the neighbourhood. */
	private static List<Zombie> summonedNear(final GameTestHelper helper, final Zombie subject) {
		return helper.getLevel()
			.getEntitiesOfClass(
				Zombie.class,
				subject.getBoundingBox().inflate(ESCORT_BOX),
				candidate -> candidate != subject && EliteState.isSummoned(candidate) && follows(candidate, subject)
			);
	}

	private static final java.util.concurrent.atomic.AtomicInteger CANDIDATE_LOGS =
		new java.util.concurrent.atomic.AtomicInteger();
	private static final int CANDIDATE_LOG_CAP = 24;

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
