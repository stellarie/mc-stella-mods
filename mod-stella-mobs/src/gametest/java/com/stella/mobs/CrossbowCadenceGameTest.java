package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.CrossbowCadenceGoal;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.HoldStandoffGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * T02: a crossbow wielding mob is exempt from the ranged distance band and fires fully charged, as fast
 * as its attack speed allows. Vanilla never reads the attack speed attribute here, so its goal holds a
 * plain random 20 + nextInt(20) ticks after the charge. Both clauses are asserted against the charge
 * duration the game itself reports, so a number moves with the constant it measures.
 */
public class CrossbowCadenceGameTest {
	private static final String TEST = "crossbowCadence";
	private static final long NIGHT_TIME = 18000L;
	private static final int SPEC_Y = 3;
	private static final int SPEC_Z = 3;
	private static final double BOLT_BOX = 32.0;
	private static final int MAX_TICKS = 400;
	/** A zombie's hit points, so the fixture's target outlives the whole window. */
	private static final float TARGET_HEALTH = 10000.0F;
	/**
	 * The code defaults, which a leaked store must equal. The elite roll is off so the fixture's own mark
	 * is the only one, and the gear roll is off so no rolled hand can take the crossbow away.
	 */
	private static final String NO_ROLL = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"boss_chance": 0.0
			}
		}""";

	/**
	 * The crossbow fixture, from the side that can fail: a mob holding a crossbow resolves the ranged role and
	 * receives one distance goal so it kites, but no standoff goal and no vanilla crossbow goal. The control is a
	 * ranged mob of another kind that keeps its band, so a zero is the exemption rather than a mob the installer
	 * never reached.
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void crossbowMobKeepsDistanceWithoutStandoff(final GameTestHelper helper) {
		prepare(helper);
		Mob pillager = elitePillager(helper, 3);
		Mob stray = eliteSkeleton(helper, 6, EntityTypes.STRAY);
		int band = countGoals(pillager, KeepDistanceGoal.class);
		int standoff = countGoals(pillager, HoldStandoffGoal.class);
		int vanillaCrossbow = countGoals(pillager, RangedCrossbowAttackGoal.class);
		int cadence = countGoals(pillager, CrossbowCadenceGoal.class);
		int controlBand = countGoals(stray, KeepDistanceGoal.class);

		result(
			"crossbowExempt",
			"role=" + ReactiveState.get(pillager).getRole() + " crossbow=" + CrossbowCadenceGoal.holdsCrossbow(pillager)
				+ " distanceGoals=" + band + " standoffGoals=" + standoff + " vanillaCrossbowGoals=" + vanillaCrossbow
				+ " cadenceGoals=" + cadence + " bowControlDistanceGoals=" + controlBand + " bowControlRole="
				+ ReactiveState.get(stray).getRole()
		);
		helper.assertValueEqual(
			ReactiveState.get(pillager).getRole(), CombatRole.RANGED, "the crossbow mob's resolved role"
		);
		helper.assertTrue(CrossbowCadenceGoal.holdsCrossbow(pillager), "the fixture's mob holds no crossbow");
		helper.assertValueEqual(Integer.valueOf(band), Integer.valueOf(1), "distance goals on the crossbow mob");
		helper.assertValueEqual(Integer.valueOf(standoff), Integer.valueOf(0), "standoff goals on the crossbow mob");
		helper.assertValueEqual(
			Integer.valueOf(vanillaCrossbow), Integer.valueOf(0), "vanilla crossbow goals on the crossbow mob"
		);
		helper.assertValueEqual(Integer.valueOf(cadence), Integer.valueOf(1), "cadence goals on the crossbow mob");
		helper.assertTrue(controlBand > 0, "the bow control received no distance goal either");
		helper.succeed();
	}

	/**
	 * The cadence, with the bolt's own flight as the evidence. The cycle is the charge the game reports
	 * plus the attack speed delay, and the bound is vanilla's own fastest cycle, which is the charge plus
	 * the hold it always spends. A cycle at or over that bound means the random hold is still being paid,
	 * and a cycle under the charge means the string was loosed before the crossbow was charged.
	 */
	@GameTest(maxTicks = MAX_TICKS)
	@SuppressWarnings("removal")
	public void crossbowFiresFullyChargedAndFast(final GameTestHelper helper) {
		prepare(helper);
		Mob pillager = elitePillager(helper, 3);
		Mob victim = ordinary(helper, EntityTypes.ZOMBIE, 3, SPEC_Y, 7);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		victim.setHealth(TARGET_HEALTH);
		helper.onEachTick(() -> pillager.setTarget(victim));
		Map<UUID, Long> firstSeen = new LinkedHashMap<>();
		List<Long> intervals = new ArrayList<>();
		UUID[] lastSeen = new UUID[1];

		helper.onEachTick(() -> {
			for (Arrow arrow : helper.getLevel().getEntitiesOfClass(
				Arrow.class, pillager.getBoundingBox().inflate(BOLT_BOX), arrow -> arrow.getOwner() == pillager
			)) {
				if (firstSeen.putIfAbsent(arrow.getUUID(), helper.getTick()) != null) {
					continue;
				}

				// Only a hop between consecutive bolts is a cycle; a gap across a stop is not.
				if (lastSeen[0] != null) {
					intervals.add(firstSeen.get(arrow.getUUID()) - firstSeen.get(lastSeen[0]));
				}

				lastSeen[0] = arrow.getUUID();
			}
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(
				intervals.size() >= 1,
				"the crossbow loosed no second bolt inside " + MAX_TICKS + " ticks, so no cycle was measured"
			);
			ItemStack crossbow = pillager.getMainHandItem();
			double chargeTicks = CrossbowItem.getChargeDuration(crossbow, pillager);
			// The attribute instance is absent on a pillager, so the game's registered default is the read.
			AttributeInstance speed = pillager.getAttribute(Attributes.ATTACK_SPEED);
			double attackSpeed = speed == null
				? Attributes.ATTACK_SPEED.value().getDefaultValue()
				: speed.getValue();
			long fastest = intervals.stream().mapToLong(Long::longValue).min().orElseThrow();
			long slowest = intervals.stream().mapToLong(Long::longValue).max().orElseThrow();
			// Vanilla's own fastest cycle: the charge plus the hold its goal always spends before firing.
			long vanillaFastest = (long) chargeTicks + CrossbowCadenceGoal.REMOVED_HOLD_TICKS;
			helper.assertTrue(
				fastest >= (long) chargeTicks,
				"a bolt cycle took " + fastest + " ticks, under the " + chargeTicks
					+ " tick charge, so the crossbow was not fully charged"
			);
			helper.assertTrue(
				slowest < vanillaFastest,
				"the slowest cycle was " + slowest + " ticks, at or over vanilla's own fastest " + vanillaFastest
					+ ", so the random hold is still being spent"
			);
			result(
				"crossbowCadence",
				"attackSpeed=" + attackSpeed + " chargeTicks=" + chargeTicks + " cadenceHoldTicks="
					+ CrossbowCadenceGoal.holdTicks(pillager) + " removedRandomHoldTicks="
					+ CrossbowCadenceGoal.REMOVED_HOLD_TICKS + " bolts=" + firstSeen.size() + " intervals="
					+ intervals + " fastest=" + fastest + " slowest=" + slowest + " vanillaFastest=" + vanillaFastest
			);
		});
	}

	/**
	 * An elite pillager that holds a crossbow before the installer reads its hand. The mark also kits the
	 * off hand, which is the ammo the crossbow consumes, so the fixture needs nothing else to fire.
	 */
	@SuppressWarnings("removal")
	private static Mob elitePillager(final GameTestHelper helper, final int x) {
		Mob mob = helper.spawnMob(EntityTypes.PILLAGER, x, SPEC_Y, SPEC_Z)
			.spawnReason(EntitySpawnReason.NATURAL)
			.spawn();
		mob.setPersistenceRequired();
		Elite.mark(mob, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		BehaviorInstaller.install(mob);
		return mob;
	}

	/** The bow control: an elite archer, which reaches the installer holding its own bow. */
	@SuppressWarnings("removal")
	private static Mob eliteSkeleton(
		final GameTestHelper helper, final int x, final EntityType<? extends Mob> type
	) {
		Mob mob = helper.spawnMob(type, x, SPEC_Y, 7).spawnReason(EntitySpawnReason.NATURAL).spawn();
		mob.setPersistenceRequired();
		Elite.mark(mob, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		BehaviorInstaller.install(mob);
		return mob;
	}

	@SuppressWarnings("removal")
	private static Mob ordinary(
		final GameTestHelper helper, final EntityType<? extends Mob> type, final int x, final int y, final int z
	) {
		Mob mob = helper.spawnMob(type, x, y, z).spawnReason(EntitySpawnReason.NATURAL).spawn();
		mob.setPersistenceRequired();
		return mob;
	}

	private static void prepare(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
		helper.setTime(NIGHT_TIME);

		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static int countGoals(final Mob mob, final Class<? extends Goal> goalClass) {
		int found = 0;

		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (goalClass.isInstance(wrapped.getGoal())) {
				found++;
			}
		}

		return found;
	}

	private static void result(final String test, final String detail) {
		com.stella.mobs.StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {}", TEST, test, detail);
	}
}
