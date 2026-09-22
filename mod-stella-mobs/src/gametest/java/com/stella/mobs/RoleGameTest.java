package com.stella.mobs;

import com.stella.mobs.behavior.DefendAllyGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class RoleGameTest {
	private static final int MAX_TICKS = 200;
	private static final long NIGHT_TIME = 18000L;
	private static final int HIGH_LEVEL = 40;
	private static final String SKELETON_GEAR = """
		"entities": {
			"minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } }
		}""";

	@GameTest(maxTicks = MAX_TICKS)
	public void roleComesFromConfigAndDefaultsToMelee(final GameTestHelper helper) {
		prepare(helper, config(SKELETON_GEAR, """
			"roles": { "minecraft:skeleton": "ranged" }"""));
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 2, 3, 2).spawn();
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 4, 3, 4).spawn();

		helper.runAfterDelay(2L, () -> {
			helper.assertValueEqual(ReactiveState.get(skeleton).getRole(), CombatRole.RANGED, "configured skeleton role");
			helper.assertValueEqual(ReactiveState.get(zombie).getRole(), CombatRole.MELEE, "default zombie role");
			helper.assertTrue(hasGoal(skeleton, KeepDistanceGoal.class), "the ranged skeleton has no distance goal");
			helper.assertFalse(hasGoal(zombie, KeepDistanceGoal.class), "the melee zombie got a distance goal");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void impossibleRoleAddsNoGoal(final GameTestHelper helper) {
		prepare(helper, config("""
			"entities": {
				"minecraft:zombie": { "slots": { "mainhand": { "item": "minecraft:diamond_sword" } } }
			}""", """
			"roles": { "minecraft:zombie": "ranged" }"""));
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 2).spawn();

		helper.runAfterDelay(2L, () -> {
			helper.assertValueEqual(ReactiveState.get(zombie).getRole(), CombatRole.RANGED, "the configured role was dropped");
			helper.assertFalse(
				hasGoal(zombie, KeepDistanceGoal.class), "a distance goal was added for a role the mob cannot perform"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void roleIsResolvedOncePerMob(final GameTestHelper helper) {
		prepare(helper, config(SKELETON_GEAR, """
			"roles": { "minecraft:skeleton": "ranged" }"""));
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 2, 3, 2).spawn();

		helper.runAfterDelay(5L, () -> GearConfigStore.set(GearConfigParser.parse(config(SKELETON_GEAR, """
			"roles": { "minecraft:skeleton": "melee" }"""))));
		helper.runAfterDelay(20L, () -> {
			helper.assertValueEqual(
				ReactiveState.get(skeleton).getRole(), CombatRole.RANGED, "the role changed after a config reload"
			);
			helper.assertTrue(
				hasGoal(skeleton, KeepDistanceGoal.class), "the distance goal was lost after a config reload"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void rangedUnitTooCloseMovesAway(final GameTestHelper helper) {
		prepare(helper, skeletonBand(4.0, 6.0));
		Zombie attacker = spawnAttacker(helper, 4, 3);
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 2, 3, 3).spawn();
		skeleton.getGoalSelector().removeAllGoals(goal -> !(goal instanceof KeepDistanceGoal));
		skeleton.setTarget(attacker);
		double start = skeleton.position().distanceTo(attacker.position());

		helper.succeedWhen(() -> {
			helper.assertTrue(isRunning(skeleton, KeepDistanceGoal.class), "the distance goal never ran");
			helper.assertTrue(
				skeleton.position().distanceTo(attacker.position()) > start + 0.75,
				"the ranged unit did not back away: " + skeleton.position().distanceTo(attacker.position())
			);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void rangedUnitTooFarMovesCloser(final GameTestHelper helper) {
		prepare(helper, skeletonBand(2.0, 3.0));
		Zombie attacker = spawnAttacker(helper, 6, 3);
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 1, 3, 3).spawn();
		skeleton.getGoalSelector().removeAllGoals(goal -> !(goal instanceof KeepDistanceGoal));
		skeleton.setTarget(attacker);
		double start = skeleton.position().distanceTo(attacker.position());

		helper.succeedWhen(() -> {
			helper.assertTrue(isRunning(skeleton, KeepDistanceGoal.class), "the distance goal never ran");
			helper.assertTrue(
				skeleton.position().distanceTo(attacker.position()) < start - 0.75,
				"the ranged unit did not close: " + skeleton.position().distanceTo(attacker.position())
			);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void rangedUnitInsideBandHoldsPosition(final GameTestHelper helper) {
		prepare(helper, skeletonBand(2.0, 6.0));
		Zombie attacker = spawnAttacker(helper, 5, 3);
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 2, 3, 3).spawn();
		skeleton.getGoalSelector().removeAllGoals(goal -> !(goal instanceof KeepDistanceGoal));
		skeleton.setTarget(attacker);
		double[] start = new double[1];

		helper.startSequence()
			.thenIdle(8)
			.thenExecute(() -> start[0] = skeleton.position().distanceTo(attacker.position()))
			.thenIdle(40)
			.thenExecute(() -> {
				helper.assertFalse(isRunning(skeleton, KeepDistanceGoal.class), "the distance goal ran inside the band");
				helper.assertTrue(
					Math.abs(skeleton.position().distanceTo(attacker.position()) - start[0]) <= 0.5,
					"the ranged unit moved while inside its band"
				);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void meleeUnitDoesNotBackAway(final GameTestHelper helper) {
		prepare(helper, skeletonBand(4.0, 6.0));
		Zombie attacker = spawnAttacker(helper, 4, 3);
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 3).spawn();
		zombie.getGoalSelector().removeAllGoals(
			goal -> goal instanceof WaterAvoidingRandomStrollGoal || goal instanceof TargetGoal
		);
		zombie.setTarget(attacker);
		double[] start = new double[1];

		helper.startSequence()
			.thenIdle(8)
			.thenExecute(() -> start[0] = zombie.position().distanceTo(attacker.position()))
			.thenIdle(60)
			.thenExecute(() -> {
				helper.assertFalse(hasGoal(zombie, KeepDistanceGoal.class), "the melee zombie got a distance goal");
				helper.assertTrue(
					zombie.position().distanceTo(attacker.position()) <= start[0] + 0.5, "the melee zombie backed away"
				);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void noPlayerRunsNoRoleLogic(final GameTestHelper helper) {
		prepareWithoutPlayer(helper, skeletonBand(4.0, 6.0));
		buildFloor(helper, HIGH_LEVEL);
		Zombie attacker = spawnAttackerAt(helper, 4, HIGH_LEVEL + 2, 3);
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 2, HIGH_LEVEL + 2, 3).spawn();
		skeleton.getGoalSelector().removeAllGoals(goal -> !(goal instanceof KeepDistanceGoal));
		skeleton.setTarget(attacker);
		double[] start = new double[1];

		helper.startSequence()
			.thenIdle(8)
			.thenExecute(() -> start[0] = skeleton.position().distanceTo(attacker.position()))
			.thenIdle(60)
			.thenExecute(() -> {
				helper.assertFalse(
					ReactiveState.get(skeleton).playerInRange(skeleton), "a player was in range: " + nearestPlayer(skeleton)
				);
				helper.assertFalse(isRunning(skeleton, KeepDistanceGoal.class), "the role goal ran with no player in range");
				helper.assertTrue(
					Math.abs(skeleton.position().distanceTo(attacker.position()) - start[0]) <= 0.5,
					"the ranged unit moved with no player in range"
				);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void shieldBlocksAnArrow(final GameTestHelper helper) {
		prepare(helper, zombieShieldConfig());
		Zombie attacker = spawnAttacker(helper, 4, 5);
		Zombie defender = helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 3).spawn();
		defender.setTarget(attacker);
		float[] before = new float[1];

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(defender.isBlocking(), "the shielded zombie never raised its shield"))
			.thenIdle(4)
			.thenExecute(() -> {
				before[0] = defender.getHealth();
				Arrow arrow = helper.spawn(EntityTypes.ARROW, 2, 2, 6);
				arrow.setOwner(attacker);
				arrow.setDeltaMovement(0.0, 0.0, -1.5);
			})
			.thenIdle(6)
			.thenExecute(() -> {
				helper.assertTrue(defender.isBlocking(), "the shield dropped before the arrow landed");
				helper.assertValueEqual(
					Float.valueOf(defender.getHealth()), Float.valueOf(before[0]), "shielded zombie health after an arrow"
				);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void bossTypesGetNoRetreatGoal(final GameTestHelper helper) {
		prepare(helper, skeletonBand(4.0, 6.0));
		Mob ravager = helper.spawnMob(EntityTypes.RAVAGER, 2, 3, 2).spawn();
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 4, 3, 4).spawn();

		helper.runAfterDelay(2L, () -> {
			helper.assertFalse(hasGoal(ravager, RetreatGoal.class), "a boss type got a retreat goal");
			helper.assertTrue(hasGoal(ravager, DefendAllyGoal.class), "the boss lost its other goals");
			helper.assertTrue(hasGoal(zombie, RetreatGoal.class), "a normal monster lost its retreat goal");
			helper.succeed();
		});
	}

	private static void prepare(final GameTestHelper helper, final String config) {
		prepareWithoutPlayer(helper, config);
		addCreativePlayer(helper);
	}

	private static void prepareWithoutPlayer(final GameTestHelper helper, final String config) {
		GearConfigStore.set(GearConfigParser.parse(config));
		helper.setTime(NIGHT_TIME);
		buildFloor(helper);
	}

	private static String config(final String entities, final String roles) {
		return "{\n\t\"version\": 1,\n\t" + entities + ",\n\t" + roles + "\n}";
	}

	private static String skeletonBand(final double near, final double far) {
		return config(SKELETON_GEAR, """
			"roles": { "minecraft:skeleton": { "role": "ranged", "near": %s, "far": %s } }""".formatted(near, far));
	}

	private static String zombieShieldConfig() {
		return config("""
			"entities": {
				"minecraft:zombie": { "slots": { "offhand": { "item": "minecraft:shield" } } }
			}""", """
			"roles": {}""");
	}

	private static void buildFloor(final GameTestHelper helper) {
		buildFloor(helper, 0);
	}

	private static void buildFloor(final GameTestHelper helper, final int level) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, level, z, Blocks.STONE);
			}
		}
	}

	@SuppressWarnings("removal")
	private static void addCreativePlayer(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
		player.setPos(position.x, position.y, position.z);
	}

	private static Zombie spawnAttacker(final GameTestHelper helper, final int x, final int z) {
		return spawnAttackerAt(helper, x, 3, z);
	}

	private static Zombie spawnAttackerAt(final GameTestHelper helper, final int x, final int y, final int z) {
		Zombie attacker = helper.spawnMob(EntityTypes.ZOMBIE, x, y, z).withNoFreeWill().spawn();
		attacker.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return attacker;
	}

	private static boolean hasGoal(final Mob mob, final Class<? extends Goal> goalClass) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (goalClass.isInstance(wrapped.getGoal())) {
				return true;
			}
		}

		return false;
	}

	private static boolean isRunning(final Mob mob, final Class<? extends Goal> goalClass) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (goalClass.isInstance(wrapped.getGoal()) && wrapped.isRunning()) {
				return true;
			}
		}

		return false;
	}

	private static String nearestPlayer(final Mob mob) {
		Player player = mob.level().getNearestPlayer(mob, 128.0);
		return player == null
			? "no player within 128 blocks"
			: player.getScoreboardName() + " at " + player.position().distanceTo(mob.position()) + " blocks";
	}
}
