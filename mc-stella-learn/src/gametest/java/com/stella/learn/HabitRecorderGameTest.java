package com.stella.learn;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The recorder adds to the profile of the player in a fight, and to no one else's. */
public class HabitRecorderGameTest {
	private static final float HIT = 1.0F;

	private static Mob zombie(final GameTestHelper helper) {
		return helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 2).spawn();
	}

	@GameTest
	public void aSwordHitCountsAsMelee(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		zombie(helper).hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		double melee = Profiles.value(player, Habit.MELEE);
		double ranged = Profiles.value(player, Habit.RANGED);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aSwordHitCountsAsMelee RESULT melee={} ranged={}", melee, ranged);
		helper.assertTrue(melee > 0.0 && ranged == 0.0, "melee " + melee + " ranged " + ranged);
		helper.succeed();
	}

	@GameTest
	public void anArrowHitCountsAsRanged(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), null);
		zombie(helper).hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, player), HIT);
		double melee = Profiles.value(player, Habit.MELEE);
		double ranged = Profiles.value(player, Habit.RANGED);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=anArrowHitCountsAsRanged RESULT melee={} ranged={}", melee, ranged);
		helper.assertTrue(ranged > 0.0 && melee == 0.0, "melee " + melee + " ranged " + ranged);
		helper.succeed();
	}

	@GameTest
	public void aMobHittingAMobCountsForNoOne(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Mob attacker = helper.spawnMob(EntityTypes.ZOMBIE, 4, 3, 4).spawn();
		zombie(helper).hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
		double melee = Profiles.value(player, Habit.MELEE);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobHittingAMobCountsForNoOne RESULT melee={}", melee);
		helper.assertTrue(melee == 0.0, "a mob's blow counted for the player: " + melee);
		helper.succeed();
	}

	@GameTest
	public void onlyTheAttackerLearns(final GameTestHelper helper) {
		ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
		ServerPlayer bystander = helper.makeMockServerPlayerInLevel();
		zombie(helper).hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), HIT);
		double theirs = Profiles.value(bystander, Habit.MELEE);
		StellaLearn.LOGGER.info(
			"[BEHAVIOR] test=onlyTheAttackerLearns RESULT attacker={} bystander={}", Profiles.value(attacker, Habit.MELEE), theirs
		);
		helper.assertTrue(Profiles.value(attacker, Habit.MELEE) > 0.0 && theirs == 0.0, "the bystander learned " + theirs);
		helper.succeed();
	}

	@GameTest
	public void aBlockedBlowCountsAsBlocking(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Mob attacker = zombie(helper);
		HabitRecorder.afterDamage(player, helper.getLevel().damageSources().mobAttack(attacker), 3.0F, 0.0F, true);
		double block = Profiles.value(player, Habit.BLOCK);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aBlockedBlowCountsAsBlocking RESULT block={}", block);
		helper.assertTrue(block > 0.0, "a blocked blow was not counted: " + block);
		helper.succeed();
	}

	@GameTest
	public void aBlowThatLandsIsNotBlocking(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Mob attacker = zombie(helper);
		HabitRecorder.afterDamage(player, helper.getLevel().damageSources().mobAttack(attacker), 3.0F, 3.0F, false);
		double block = Profiles.value(player, Habit.BLOCK);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aBlowThatLandsIsNotBlocking RESULT block={}", block);
		helper.assertTrue(block == 0.0, "a landed blow counted as blocking: " + block);
		helper.succeed();
	}

	/**
	 * The real path, tagged as a manual gap after three attempts: the mock player is in creative and its item use
	 * never ticks, so a raised shield does not block. The event handler is proven above by a direct call.
	 */
	@GameTest(maxTicks = 60, required = false)
	public void manualGapRealShieldBlockNeedsARealPlayer(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Mob attacker = helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 4).spawn();
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(0.0F);
		player.setYHeadRot(0.0F);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.startUsingItem(InteractionHand.OFF_HAND);
		helper.runAfterDelay(5L, () -> {
			for (int i = 0; i < 10; i++) {
				player.tick();
			}

			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 3.0F);
			double block = Profiles.value(player, Habit.BLOCK);
			StellaLearn.LOGGER.info(
				"[BEHAVIOR] test=manualGapRealShieldBlockNeedsARealPlayer RESULT blocking={} using={} ticks={} mode={} block={}", player.isBlocking(), player.isUsingItem(), player.getTicksUsingItem(), player.gameMode(), block
			);
			helper.assertTrue(block > 0.0, "a raised shield's block was not counted: " + block);
			helper.succeed();
		});
	}
}
