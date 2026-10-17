package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

/** A mob hurt by a player calls, and what it asks for follows its health and what the player is known to do. */
public class CalloutSenderGameTest {
	private static final int LONG = 700;

	private static Monster zombie(final GameTestHelper helper, final int x, final int z) {
		Monster mob = (Monster) helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
		mob.setNoAi(true);
		return mob;
	}

	private static void record(final ServerPlayer player, final Habit habit, final double times) {
		for (int i = 0; i < times; i++) {
			Profiles.record(player, habit);
		}
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aCallerBelowHalfItsHealthAsksForAHeal(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Monster caller = zombie(helper, 20, 20);
		LearnArena.whenReady(helper, List.of(player, caller), () -> {
			caller.setHealth(caller.getMaxHealth() * 0.4F);
			Callout callout = CalloutSender.choose(caller, player);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aCallerBelowHalfItsHealthAsksForAHeal RESULT type={}", callout.type());
			helper.assertTrue(callout.type().equals(CalloutKits.HEAL), "the caller asked for " + callout.type());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aHealthyCallerFacingATurtleAsksForADebuff(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Monster caller = zombie(helper, 20, 20);
		record(player, Habit.BLOCK, LearnConfigStore.get().shieldSaturation());
		LearnArena.whenReady(helper, List.of(player, caller), () -> {
			caller.setHealth(caller.getMaxHealth());
			Callout callout = CalloutSender.choose(caller, player);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aHealthyCallerFacingATurtleAsksForADebuff RESULT type={} detail={}", callout.type(), callout.detail());
			helper.assertTrue(callout.type().equals(CalloutKits.DEBUFF) && callout.detail() != null, "the caller asked for " + callout.type());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aHealthyCallerFacingARusherAsksForABuff(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Monster caller = zombie(helper, 20, 20);
		record(player, Habit.MELEE, LearnConfigStore.get().rushSaturation());
		LearnArena.whenReady(helper, List.of(player, caller), () -> {
			caller.setHealth(caller.getMaxHealth());
			Callout callout = CalloutSender.choose(caller, player);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aHealthyCallerFacingARusherAsksForABuff RESULT type={} detail={}", callout.type(), callout.detail());
			helper.assertTrue(callout.type().equals(CalloutKits.BUFF) && callout.detail() != null, "the caller asked for " + callout.type());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aHealthyCallerFacingAnUnknownPlayerRalliesInstead(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Monster caller = zombie(helper, 20, 20);
		LearnArena.whenReady(helper, List.of(player, caller), () -> {
			caller.setHealth(caller.getMaxHealth());
			Callout callout = CalloutSender.choose(caller, player);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aHealthyCallerFacingAnUnknownPlayerRalliesInstead RESULT type={}", callout.type());
			helper.assertTrue(callout.type().equals(Rally.TYPE), "the caller asked for " + callout.type());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobHurtByAPlayerCalls(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Monster caller = zombie(helper, 20, 20);
		Monster receiver = zombie(helper, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			LearnTestInit.withCallouts(() -> caller.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F));
			int answers = LearnTestInit.answersTo(caller.getUUID());
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobHurtByAPlayerCalls RESULT answers={}", answers);
			helper.assertTrue(answers > 0, "a mob hurt by a player called no one");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobHurtByAMobDoesNotCall(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Monster caller = zombie(helper, 20, 20);
		Monster attacker = zombie(helper, 28, 20);
		Monster bystander = zombie(helper, 28, 24);
		LearnArena.whenReady(helper, List.of(player, caller, attacker, bystander), () -> {
			caller.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
			int answers = LearnTestInit.answersTo(caller.getUUID());
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobHurtByAMobDoesNotCall RESULT answers={}", answers);
			helper.assertTrue(answers == 0, "a mob hurt by a mob called " + answers + " answers");
			helper.succeed();
		});
	}

	/** A measurement, not a guess: how often a fight of one hit every ten ticks draws a call, with the shipped cooldown. */
	@GameTest(maxTicks = 1100, padding = LearnArena.PADDING)
	public void aSteadyFightDrawsACallEveryCooldown(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Monster caller = zombie(helper, 20, 20);
		Monster first = zombie(helper, 28, 20);
		Monster second = zombie(helper, 28, 24);
		Monster third = zombie(helper, 28, 28);
		int fightTicks = 300;
		// One tick hook from the body, since scheduling many timed callbacks from inside a callback corrupts the framework's map.
		long[] began = {-1L};
		helper.onEachTick(() -> {
			if (began[0] < 0L) {
				return;
			}

			long elapsed = helper.getTick() - began[0];
			if (elapsed < fightTicks && elapsed % 10 == 0) {
				caller.setInvulnerableTime(0);
				LearnTestInit.withCallouts(() -> caller.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 0.5F));
			} else if (elapsed == fightTicks + 5L) {
				int receivers = 3;
				int calls = LearnTestInit.answersTo(caller.getUUID()) / receivers;
				int most = fightTicks / LearnConfigStore.get().calloutCooldownTicks() + 1;
				StellaLearn.LOGGER.info(
					"[BEHAVIOR] test=aSteadyFightDrawsACallEveryCooldown RESULT fightTicks={} calls={} cooldown={} ceiling={}",
					fightTicks, calls, LearnConfigStore.get().calloutCooldownTicks(), most
				);
				helper.assertTrue(calls >= 2, "a " + fightTicks + " tick fight drew only " + calls + " calls");
				helper.assertTrue(calls <= most, "the fight drew " + calls + " calls, past the cooldown's ceiling of " + most);
				helper.succeed();
			}
		});
		LearnArena.whenReady(helper, List.of(player, caller, first, second, third), () -> {
			caller.setHealth(caller.getMaxHealth() * 100.0F);
			began[0] = helper.getTick();
		});
	}
}
