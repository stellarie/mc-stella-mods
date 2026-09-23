package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteAuraGoal;
import com.stella.mobs.behavior.FollowAllyGoal;
import com.stella.mobs.behavior.GuardianBeamCooldownGoal;
import com.stella.mobs.behavior.GuardianElite;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.SpawnScaling;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * O02 and O03: the elite guardian's aura, movement and held beam, and the elder's forced elite marker.
 * The elite guardian's aura radius is 16 at rank 8, its speed is 0.40 of the base on top of the plain
 * guardian's, and its beam is held 60 ticks after a landing. The elder is elite at a zero chance, blinds
 * and weakens inside 32 blocks, carries double the base health it arrived with, and takes no escorts.
 */
public class GuardianEliteGameTest {
	private static final String NO_ROLL = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}""";
	private static final String ALWAYS_ELITE = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 1.0, "boss_chance": 0.0 }
		}""";
	private static final double EPSILON = 1.0e-6;
	private static final float TARGET_HEALTH = 10000.0F;
	private static final int POOL_TOP = 5;
	private static final int NEAR_DISTANCE = 12;
	/** The plain guardian's own gap must fit inside the elite's floor, or the clauses prove nothing. */
	private static final int BEAM_WINDOW_TICKS = 380;

	@GameTest(maxTicks = 200)
	public void eliteGuardianBlindsNearbyPlayers(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			buildArena(helper, 4, 4);
			Guardian guardian = spawnGuardian(helper, 4, 4);
			ServerPlayer near = target(helper, 4, 3, 4 + NEAR_DISTANCE);
			ServerPlayer far = target(helper, 4, 3, 4 + 2 * NEAR_DISTANCE);
			pin(helper, guardian, 4.5, 3.0, 4.5);

			EliteAuraGoal<?> aura = auraOf(guardian);
			helper.runAfterDelay(60L, () -> {
				// The negative direction first: while the guardian is plain, its own aura wrote nothing.
				helper.assertTrue(aura != null, "the guardian carries no aura goal");
				helper.assertTrue(aura.affected().isEmpty(), "a plain guardian's aura applied to a player");
				Elite.mark(guardian, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			});

			helper.succeedWhen(() -> {
				helper.assertTrue(Elite.isElite(guardian), "the guardian was not marked elite");
				helper.assertValueEqual(
					Integer.valueOf(auraRank(guardian)), Integer.valueOf(BehaviorInstaller.OCEAN_AURA_PRIORITY),
					"the elite guardian's aura rank"
				);
				// The record is the aura's own product, so a neighbouring aura cannot answer for it.
				helper.assertTrue(
					aura.affected().contains(near.getUUID()),
					"the aura never applied to the player inside " + EliteAuraGoal.RADIUS + " blocks"
				);
				helper.assertFalse(
					aura.affected().contains(far.getUUID()),
					"the aura applied to the player outside " + EliteAuraGoal.RADIUS + " blocks"
				);
				helper.assertTrue(
					near.hasEffect(MobEffects.BLINDNESS), "the player inside the aura carries no blindness"
				);
				result(
					"eliteGuardianBlindsNearbyPlayers",
					"radius=" + EliteAuraGoal.RADIUS + " nearDistance=" + NEAR_DISTANCE + " farDistance="
						+ (2 * NEAR_DISTANCE) + " nearInRecord=true farInRecord=false nearBlind=true rank="
						+ auraRank(guardian)
				);
			});
		} finally {
			GearConfigStore.set(previous);
		}
	}

	@GameTest(maxTicks = 200)
	public void eliteGuardianMovesFasterThanAPlainOne(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		Guardian elite;
		Guardian plain;
		try {
			// The pinned roll is the shipped spawn path: the mark lands at load, before the installer.
			GearConfigStore.set(GearConfigParser.parse(ALWAYS_ELITE));
			elite = spawnGuardian(helper, 2, 2);
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			plain = spawnGuardian(helper, 6, 6);
		} finally {
			GearConfigStore.set(previous);
		}

		helper.runAfterDelay(4L, () -> {
			double plainValue = plain.getAttributeValue(Attributes.MOVEMENT_SPEED);
			double eliteValue = elite.getAttributeValue(Attributes.MOVEMENT_SPEED);
			double expected = plainValue * (1.0 + GuardianElite.SPEED_BONUS);
			AttributeInstance speed = elite.getAttribute(Attributes.MOVEMENT_SPEED);
			AttributeModifier modifier = speed == null ? null : speed.getModifier(GuardianElite.SPEED_ID);
			helper.assertTrue(Elite.isElite(elite), "the pinned roll did not mark the guardian elite");
			helper.assertFalse(Elite.isElite(plain), "the control guardian rolled elite");
			helper.assertTrue(
				modifier != null,
				"the elite guardian carries no speed modifier: " + eliteValue + " against the plain " + plainValue
			);
			helper.assertTrue(
				modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
				"the speed bonus is not on the base: " + modifier.operation()
			);
			helper.assertTrue(
				Math.abs(modifier.amount() - GuardianElite.SPEED_BONUS) < EPSILON,
				"the speed bonus is " + modifier.amount() + ", not the shipped " + GuardianElite.SPEED_BONUS
			);
			helper.assertTrue(
				Math.abs(eliteValue - expected) < EPSILON,
				"the elite guardian moves at " + eliteValue + ", not the plain " + plainValue + " times "
					+ (1.0 + GuardianElite.SPEED_BONUS)
			);
			// Idempotent: a reload runs the guarded write again and must change nothing.
			GuardianElite.speed(elite);
			helper.assertTrue(
				Math.abs(elite.getAttributeValue(Attributes.MOVEMENT_SPEED) - eliteValue) < EPSILON,
				"a second speed write changed the value"
			);
			result(
				"eliteGuardianMovesFaster",
				"plain=" + plainValue + " elite=" + eliteValue + " expected=" + expected + " bonus="
					+ GuardianElite.SPEED_BONUS
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void eliteGuardianHoldsItsTargetBetweenBeams(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			// Four small pockets inside the structure: a guardian cannot see through a neighbour's build.
			buildPocket(helper, 2, 2);
			buildPocket(helper, 6, 2);
			buildPocket(helper, 2, 7);
			buildPocket(helper, 6, 7);
			Guardian elite = spawnGuardian(helper, 2, 2);
			Guardian plain = spawnGuardian(helper, 6, 2);
			Mob eliteTarget = squidTarget(helper, 2, 7);
			Mob plainTarget = squidTarget(helper, 6, 7);
			Elite.mark(elite, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			// The aura gate needs a nearby player; this one stays above the guardians' own target range.
			witness(helper, 4, 25, 4);
			pin(helper, elite, 2.5, 3.0, 2.5);
			pin(helper, plain, 6.5, 3.0, 2.5);
			BeamWatch eliteWatch = new BeamWatch(helper, eliteTarget);
			BeamWatch plainWatch = new BeamWatch(helper, plainTarget);
			int[] ticks = new int[1];
			helper.onEachTick(() -> {
				eliteWatch.tick();
				plainWatch.tick();
				ticks[0]++;

				if (ticks[0] % 40 == 0) {
					StellaMobs.LOGGER.info(
						"[BEHAVIOR] test=guardianBeamProbe t={} eliteTarget={} plainTarget={} eliteActive={} eliteDist={}"
							+ " eliteLos={} health={} max={} near={}",
						ticks[0], elite.getTarget() != null, plain.getTarget() != null, elite.hasActiveAttackTarget(),
						elite.distanceTo(eliteTarget), elite.hasLineOfSight(eliteTarget), eliteTarget.getHealth(),
						eliteTarget.getMaxHealth(), ReactiveState.get(elite).playerInRange(elite)
					);
				}
			});

			helper.runAfterDelay(BEAM_WINDOW_TICKS, () -> {
				int floor = GuardianBeamCooldownGoal.HOLD_TICKS + elite.getAttackDuration();
				helper.assertTrue(
					eliteWatch.beams >= 2, "the elite guardian landed " + eliteWatch.beams + " beams, too few"
				);
				helper.assertTrue(
					plainWatch.beams >= 2, "the plain guardian landed " + plainWatch.beams + " beams, too few"
				);
				helper.assertTrue(
					eliteWatch.gap >= floor,
					"the elite's next beam came " + eliteWatch.gap + " ticks after its first, under the " + floor
						+ " the hold of " + GuardianBeamCooldownGoal.HOLD_TICKS + " and the beam duration of "
						+ elite.getAttackDuration() + " promise"
				);
				helper.assertTrue(
					plainWatch.gap < floor,
					"the plain guardian's beam gap was " + plainWatch.gap + ", over the " + floor
						+ " the elite's hold should cost only the elite"
				);
				helper.assertTrue(
					eliteTarget.isAlive() && plainTarget.isAlive(),
					"a target died during the window: elite=" + eliteTarget.isAlive() + " plain="
						+ plainTarget.isAlive()
				);
				result(
					"eliteGuardianHoldsItsTargetBetweenBeams",
					"hold=" + GuardianBeamCooldownGoal.HOLD_TICKS + " duration=" + elite.getAttackDuration()
						+ " eliteBeams=" + eliteWatch.beams + " eliteGap=" + eliteWatch.gap + " plainBeams="
						+ plainWatch.beams + " plainGap=" + plainWatch.gap + " targetMax=" + eliteTarget.getMaxHealth()
				);
				helper.succeed();
			});
		} finally {
			GearConfigStore.set(previous);
		}
	}

	@GameTest(maxTicks = 200)
	public void elderGuardianIsAlwaysEliteAtZeroChance(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			buildArena(helper, 4, 4);
			ServerLevel level = helper.getLevel();
			ElderGuardian elder = EntityTypes.ELDER_GUARDIAN.create(level, EntitySpawnReason.EVENT);
			helper.assertTrue(elder != null, "the elder guardian could not be created");
			// The base it carries before the load event, which is the number the forced mark multiplies.
			double carried = elder.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
			Vec3 spot = helper.absoluteVec(new Vec3(4.5, 3.0, 4.5));
			elder.snapTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
			elder.setPersistenceRequired();
			level.addFreshEntity(elder);

			helper.runAfterDelay(4L, () -> {
				double expectedBase = carried * Elite.HEALTH_MULTIPLIER;
				AttributeInstance health = elder.getAttribute(Attributes.MAX_HEALTH);
				helper.assertTrue(Elite.isElite(elder), "a fresh elder at a zero chance is not elite");
				helper.assertFalse(Elite.canBeElite(elder), "the ordinary roll claims it can mark an elder");
				helper.assertTrue(
					Math.abs(health.getBaseValue() - expectedBase) < EPSILON,
					"the elder's base health is " + health.getBaseValue() + ", not the carried " + carried
						+ " times " + Elite.HEALTH_MULTIPLIER
				);
				helper.assertTrue(
					Math.abs(elder.getMaxHealth() - expectedBase * SpawnScaling.healthMultiplierOf(elder)) < EPSILON,
					"the elder's maximum is " + elder.getMaxHealth() + ", not the base times the level scaling"
				);
				helper.assertValueEqual(
					Long.valueOf(scaleWrites(elder)), Long.valueOf(1L), "scale modifiers after the forced mark"
				);
				helper.assertValueEqual(
					Integer.valueOf(escortsOf(elder)), Integer.valueOf(0), "escorts the forced path queued"
				);

				// Idempotent: the forced marker runs again, as a reload would, and changes nothing.
				GearTier tierBefore = Elite.kitTier(elder);
				Elite.rollAtSpawn(elder, level, 0.0, true);
				helper.assertTrue(
					Math.abs(health.getBaseValue() - expectedBase) < EPSILON,
					"a second forced mark changed the base health"
				);
				helper.assertValueEqual(Elite.kitTier(elder), tierBefore, "tier after a second forced mark");
				helper.assertValueEqual(
					Long.valueOf(scaleWrites(elder)), Long.valueOf(1L), "scale modifiers after a second forced mark"
				);
				result(
					"elderGuardianForced",
					"carriedBase=" + carried + " baseAfter=" + health.getBaseValue() + " max=" + elder.getMaxHealth()
						+ " multiplier=" + Elite.HEALTH_MULTIPLIER + " elite=" + Elite.isElite(elder) + " canBeElite="
						+ Elite.canBeElite(elder) + " escorts=" + escortsOf(elder)
				);
				helper.succeed();
			});
		} finally {
			GearConfigStore.set(previous);
		}
	}

	@GameTest(maxTicks = 200)
	public void elderGuardianBlindsAndWeakensInsideItsTempleRadius(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			buildArena(helper, 4, 4);
			Guardian elder = helper.spawnMob(EntityTypes.ELDER_GUARDIAN, 4, 3, 4).spawn();
			elder.setPersistenceRequired();
			helper.assertTrue(elder.getType() == EntityTypes.ELDER_GUARDIAN, "the subject is not an elder");
			ServerPlayer near = target(helper, 4, 3, 4 + 20);
			ServerPlayer far = target(helper, 4, 3, 4 + 48);
			pin(helper, elder, 4.5, 3.0, 4.5);

			helper.succeedWhen(() -> {
				EliteAuraGoal<?> aura = auraOf(elder);
				helper.assertTrue(Elite.isElite(elder), "the elder is not elite");
				helper.assertValueEqual(
					Integer.valueOf(auraRank(elder)), Integer.valueOf(BehaviorInstaller.OCEAN_AURA_PRIORITY),
					"the elder's aura rank"
				);
				helper.assertTrue(aura != null, "the elder carries no aura goal");
				helper.assertTrue(
					aura.affected().contains(near.getUUID()),
					"the temple aura never applied to the player inside " + GuardianElite.ELDER_AURA_RADIUS + " blocks"
				);
				helper.assertFalse(
					aura.affected().contains(far.getUUID()),
					"the aura applied to the player outside " + GuardianElite.ELDER_AURA_RADIUS + " blocks"
				);
				helper.assertTrue(near.hasEffect(MobEffects.BLINDNESS), "the near player carries no blindness");
				helper.assertTrue(near.hasEffect(MobEffects.WEAKNESS), "the near player carries no weakness");
				result(
					"elderGuardianBlindsAndWeakens",
					"radius=" + GuardianElite.ELDER_AURA_RADIUS + " nearDistance=20.0 farDistance=48.0"
						+ " nearInRecord=true farInRecord=false nearBlind=true nearWeak=true rank=" + auraRank(elder)
				);
			});
		} finally {
			GearConfigStore.set(previous);
		}
	}

	/** One summary line per test, with the outcome as a named value. */
	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}

	/** The water pocket a guardian needs, and the floor under it. */
	private static void buildArena(final GameTestHelper helper, final int centreX, final int centreZ) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int y = 0; y <= POOL_TOP; y++) {
					helper.setBlock(centreX + dx, y, centreZ + dz, y == 0 ? Blocks.STONE : Blocks.WATER);
				}
			}
		}
	}

	/** A three by three pocket, so a beam arena fits inside the structure. */
	private static void buildPocket(final GameTestHelper helper, final int centreX, final int centreZ) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y <= POOL_TOP; y++) {
					helper.setBlock(centreX + dx, y, centreZ + dz, y == 0 ? Blocks.STONE : Blocks.WATER);
				}
			}
		}
	}

	private static Guardian spawnGuardian(final GameTestHelper helper, final int x, final int z) {
		Guardian guardian = helper.spawnMob(EntityTypes.GUARDIAN, x, 3, z).spawn();
		guardian.setPersistenceRequired();
		return guardian;
	}

	/** A squid the guardian may target: the selector accepts squid and axolotl, and a mock player is creative. */
	private static Mob squidTarget(final GameTestHelper helper, final int x, final int z) {
		Mob squid = helper.spawnMob(EntityTypes.SQUID, x, 3, z).spawn();
		squid.setPersistenceRequired();
		squid.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		squid.setHealth(TARGET_HEALTH);
		pin(helper, squid, x + 0.5, 3.0, z + 0.5);
		return squid;
	}

	/** A player the guardian may target: survival, high health, and held where the test put it. */
	@SuppressWarnings("removal")
	private static ServerPlayer target(final GameTestHelper helper, final int x, final int y, final int z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		player.setHealth(TARGET_HEALTH);
		Vec3 spot = helper.absoluteVec(new Vec3(x + 0.5, y, z + 0.5));
		helper.onEachTick(() -> {
			player.setInvulnerableTime(0);
			player.setPos(spot.x, spot.y, spot.z);
		});
		return player;
	}

	private static void pin(final GameTestHelper helper, final Mob mob, final double x, final double y, final double z) {
		Vec3 spot = helper.absoluteVec(new Vec3(x, y, z));
		helper.onEachTick(() -> mob.setPos(spot.x, spot.y, spot.z));
	}

	/** P6: the elite guardian's aura also slows, which is the tether that holds a runner for the beam. */
	@GameTest(maxTicks = 200)
	public void guardianTetherSlowsNearbyPlayers(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
			buildArena(helper, 4, 4);
			Guardian guardian = spawnGuardian(helper, 4, 4);
			ServerPlayer near = target(helper, 4, 3, 4 + NEAR_DISTANCE);
			pin(helper, guardian, 4.5, 3.0, 4.5);

			helper.runAfterDelay(60L, () -> {
				helper.assertFalse(near.hasEffect(MobEffects.SLOWNESS), "a plain guardian tethered a player");
				Elite.mark(guardian, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			});

			helper.succeedWhen(() -> {
				MobEffectInstance slow = near.getEffect(MobEffects.SLOWNESS);
				helper.assertTrue(
					slow != null, "the tether never slowed the player inside " + EliteAuraGoal.RADIUS + " blocks"
				);
				helper.assertValueEqual(
					Integer.valueOf(slow.getAmplifier()), Integer.valueOf(EliteAuraGoal.AMPLIFIER),
					"the tether's slow level"
				);
				result(
					"guardianTetherSlows",
					"radius=" + EliteAuraGoal.RADIUS + " slowLevel=" + slow.getAmplifier() + " slowTicks="
						+ slow.getDuration()
				);
			});
		} finally {
			GearConfigStore.set(previous);
		}
	}

	/** The rank the aura goal was given, so the free slot below the witch's 9 is asserted, not assumed. */
	private static int auraRank(final Mob mob) {
		return mob.getGoalSelector().getAvailableGoals().stream()
			.filter(wrapped -> wrapped.getGoal() instanceof EliteAuraGoal)
			.mapToInt(WrappedGoal::getPriority)
			.findFirst()
			.orElse(-1);
	}

	/** The aura this mob carries, so a clause reads the record the goal itself wrote. */
	private static EliteAuraGoal<?> auraOf(final Mob mob) {
		return mob.getGoalSelector().getAvailableGoals().stream()
			.map(WrappedGoal::getGoal)
			.filter(EliteAuraGoal.class::isInstance)
			.map(goal -> (EliteAuraGoal<?>) goal)
			.findFirst()
			.orElse(null);
	}

	/** A player near enough for the aura gate to be true, held above the guardians' target range. */
	@SuppressWarnings("removal")
	private static void witness(final GameTestHelper helper, final int x, final int y, final int z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 spot = helper.absoluteVec(new Vec3(x + 0.5, y, z + 0.5));
		helper.onEachTick(() -> player.setPos(spot.x, spot.y, spot.z));
	}

	/** How many times the elite scale marker was written, so a second forced mark can be told from the first. */
	private static long scaleWrites(final Mob mob) {
		AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
		return scale == null ? 0L : scale.getModifiers().stream().filter(mod -> mod.id().equals(Elite.SCALE_ID)).count();
	}

	/** The escorts the mod queued for this mob, by their own follow goal, in a box around it. */
	private static int escortsOf(final Mob elite) {
		return elite.level()
			.getEntitiesOfClass(
				Mob.class,
				elite.getBoundingBox().inflate(24.0),
				candidate -> candidate.getGoalSelector().getAvailableGoals().stream().anyMatch(
					wrapped -> wrapped.getGoal() instanceof FollowAllyGoal marker && marker.leader() == elite
				)
			)
			.size();
	}

	/**
	 * The beams one guardian landed on one target, read as drops on the target's health, and the gap in
	 * framework ticks between the first two. The game clock barely advances under the runner, so the gap
	 * is counted on the helper's own tick.
	 */
	private static final class BeamWatch {
		private final GameTestHelper helper;
		private final Mob target;
		private float lastHealth;
		private long firstTick = -1L;
		private int beams;
		private long gap;

		private BeamWatch(final GameTestHelper helper, final Mob target) {
			this.helper = helper;
			this.target = target;
			this.lastHealth = target.getHealth();
		}

		private void tick() {
			float health = this.target.getHealth();

			if (health >= this.lastHealth) {
				this.lastHealth = health;
				return;
			}

			this.lastHealth = health;
			this.beams++;
			long now = this.helper.getTick();

			if (this.firstTick < 0L) {
				this.firstTick = now;
			} else if (this.gap == 0L) {
				this.gap = now - this.firstTick;
			}
		}
	}
}
