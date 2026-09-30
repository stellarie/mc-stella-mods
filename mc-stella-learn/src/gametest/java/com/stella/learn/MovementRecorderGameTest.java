package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** How a player moves against the monster after them is counted: which way they strafe, whether they charge, whether they kite. */
public class MovementRecorderGameTest {
	private static final int LONG = 400;

	/** The monster stands south of the player, so the player faces south and their right hand is west. */
	private static Mob hunterSouth(final GameTestHelper helper, final ServerPlayer player) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 30).spawn();
		zombie.setNoAi(true);
		zombie.setTarget(player);
		return zombie;
	}

	private static Vec3 shift(final ServerPlayer player, final double dx, final double dz) {
		Vec3 before = player.position();
		player.setPos(before.x + dx, before.y, before.z + dz);
		return before;
	}

	private interface Move {
		void run(ServerPlayer player, Mob hunter, List<Habit> counted);
	}

	private static void scenario(final GameTestHelper helper, final double dx, final double dz, final Move check) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob hunter = hunterSouth(helper, player);
		LearnArena.whenReady(helper, List.of(player, hunter), () -> {
			hunter.setTarget(player);
			Vec3 previous = shift(player, dx, dz);
			check.run(player, hunter, MovementRecorder.sampleFrom(player, previous));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aStepWestFacingSouthIsAStrafeRight(final GameTestHelper helper) {
		scenario(helper, -1.0, 0.0, (player, hunter, counted) -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aStepWestFacingSouthIsAStrafeRight RESULT counted={}", counted);
			helper.assertTrue(counted.contains(Habit.STRAFE_RIGHT) && !counted.contains(Habit.STRAFE_LEFT) && !counted.contains(Habit.CHARGE), "counted " + counted);
			helper.assertTrue(Profiles.value(player, Habit.STRAFE_RIGHT) > 0.0, "the counter did not rise");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aStepEastFacingSouthIsAStrafeLeft(final GameTestHelper helper) {
		scenario(helper, 1.0, 0.0, (player, hunter, counted) -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aStepEastFacingSouthIsAStrafeLeft RESULT counted={}", counted);
			helper.assertTrue(counted.contains(Habit.STRAFE_LEFT) && !counted.contains(Habit.STRAFE_RIGHT), "counted " + counted);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aStepStraightAtTheMonsterIsACharge(final GameTestHelper helper) {
		scenario(helper, 0.0, 1.0, (player, hunter, counted) -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aStepStraightAtTheMonsterIsACharge RESULT counted={}", counted);
			helper.assertTrue(counted.equals(List.of(Habit.CHARGE)), "counted " + counted);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aSlowStepCountsForNothing(final GameTestHelper helper) {
		scenario(helper, 0.3, 0.3, (player, hunter, counted) -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aSlowStepCountsForNothing RESULT counted={}", counted);
			helper.assertTrue(counted.isEmpty(), "counted " + counted);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void backingAwayIsNeitherAStrafeNorACharge(final GameTestHelper helper) {
		scenario(helper, 0.0, -1.0, (player, hunter, counted) -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=backingAwayIsNeitherAStrafeNorACharge RESULT counted={}", counted);
			helper.assertTrue(counted.isEmpty(), "counted " + counted);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void movingWithNoMonsterAfterThemCountsForNothing(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob idle = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 30).spawn();
		idle.setNoAi(true);
		LearnArena.whenReady(helper, List.of(player, idle), () -> {
			Vec3 previous = shift(player, -1.0, 0.0);
			List<Habit> counted = MovementRecorder.sampleFrom(player, previous);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=movingWithNoMonsterAfterThemCountsForNothing RESULT counted={}", counted);
			helper.assertTrue(counted.isEmpty(), "counted " + counted);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theVelocityIsStoredPerTick(final GameTestHelper helper) {
		scenario(helper, 1.0, 0.0, (player, hunter, counted) -> {
			Vec3 velocity = MovementRecorder.velocityOf(player);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theVelocityIsStoredPerTick RESULT velocity={}", velocity);
			helper.assertTrue(velocity != null && Math.abs(velocity.x - 1.0 / MovementRecorder.SAMPLE_TICKS) < 1.0e-6 && Math.abs(velocity.z) < 1.0e-6, "velocity was " + velocity);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aRetreatStraightAfterAShotIsAKite(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob hunter = hunterSouth(helper, player);
		LearnArena.whenReady(helper, List.of(player, hunter), () -> {
			hunter.setTarget(player);
			Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), null);
			hunter.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, player), 1.0F);
			Vec3 previous = shift(player, 0.0, -2.0);
			boolean retreated = RetreatRecorder.sampleFrom(player, previous);
			double kite = Profiles.value(player, Habit.KITE);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aRetreatStraightAfterAShotIsAKite RESULT retreated={} kite={}", retreated, kite);
			helper.assertTrue(retreated && kite > 0.0, "retreated=" + retreated + " kite=" + kite);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aRetreatWithNoShotBehindItIsNotAKite(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob hunter = hunterSouth(helper, player);
		LearnArena.whenReady(helper, List.of(player, hunter), () -> {
			hunter.setTarget(player);
			Vec3 previous = shift(player, 0.0, -2.0);
			boolean retreated = RetreatRecorder.sampleFrom(player, previous);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aRetreatWithNoShotBehindItIsNotAKite RESULT retreated={} kite={}", retreated, Profiles.value(player, Habit.KITE));
			helper.assertTrue(retreated, "the retreat was not counted, so the case proves nothing");
			helper.assertTrue(Profiles.value(player, Habit.KITE) == 0.0, "a retreat with no shot counted as a kite");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aFallingSwordHitIsACrit(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 28).spawn();
		target.setNoAi(true);
		LearnArena.whenReady(helper, List.of(player, target), () -> {
			player.setOnGround(false);
			player.fallDistance = 1.0F;
			target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
			double crit = Profiles.value(player, Habit.CRIT);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aFallingSwordHitIsACrit RESULT crit={} melee={}", crit, Profiles.value(player, Habit.MELEE));
			helper.assertTrue(crit > 0.0 && Profiles.value(player, Habit.MELEE) > 0.0, "crit=" + crit);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aHitFromTheGroundIsNotACrit(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 28).spawn();
		target.setNoAi(true);
		LearnArena.whenReady(helper, List.of(player, target), () -> {
			player.setOnGround(true);
			player.fallDistance = 0.0F;
			target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aHitFromTheGroundIsNotACrit RESULT crit={}", Profiles.value(player, Habit.CRIT));
			helper.assertTrue(Profiles.value(player, Habit.CRIT) == 0.0 && Profiles.value(player, Habit.MELEE) > 0.0, "a grounded hit counted as a crit");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aSprintingHitFromTheAirIsNotACrit(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 28).spawn();
		target.setNoAi(true);
		LearnArena.whenReady(helper, List.of(player, target), () -> {
			player.setOnGround(false);
			player.fallDistance = 1.0F;
			player.setSprinting(true);
			target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aSprintingHitFromTheAirIsNotACrit RESULT crit={}", Profiles.value(player, Habit.CRIT));
			helper.assertTrue(Profiles.value(player, Habit.CRIT) == 0.0, "a sprinting hit counted as a crit, as vanilla does not");
			helper.succeed();
		});
	}
}
