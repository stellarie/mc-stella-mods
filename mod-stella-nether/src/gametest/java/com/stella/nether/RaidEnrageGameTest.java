package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/** Thirty minutes in, every raid mob, zombified piglins too, turns fast, strong and hunting. */
public class RaidEnrageGameTest {
	static boolean enraged(final LivingEntity mob) {
		MobEffectInstance speed = mob.getEffect(MobEffects.SPEED);
		MobEffectInstance strength = mob.getEffect(MobEffects.STRENGTH);
		return speed != null && speed.isInfiniteDuration() && speed.getAmplifier() == RaidInstance.ENRAGE_AMPLIFIER
			&& strength != null && strength.isInfiniteDuration() && strength.getAmplifier() == RaidInstance.ENRAGE_AMPLIFIER;
	}

	static void setElapsed(final MinecraftServer server, final long elapsed) {
		RaidState state = RaidInstance.state(server);
		RaidInstance.set(server, state.withElapsed(elapsed));
	}

	@GameTest(environment = "mod-stella-nether-test:enrage", maxTicks = 400)
	public void atThirtyMinutesRaidMobsTurnFastAndStrong(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		ZombifiedPiglin mob = RaidModeGameTest.spawnInRaid((ServerLevel) player.level(), player.position().add(3.0, 0.0, 0.0));
		setElapsed(server, RaidInstance.ENRAGE_TICKS - 2);
		helper.succeedWhen(() -> {
			helper.assertTrue(enraged(mob), "the mob is not enraged yet");
			NetherTestSupport.result("enrageAtThirty", "elapsed=" + RaidInstance.state(server).elapsed() + " speed=" + mob.getEffect(MobEffects.SPEED));
			mob.discard();
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:calm", maxTicks = 400)
	public void beforeThirtyMinutesRaidMobsStayCalm(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		ZombifiedPiglin mob = RaidModeGameTest.spawnInRaid((ServerLevel) player.level(), player.position().add(3.0, 0.0, 0.0));
		setElapsed(server, RaidInstance.ENRAGE_TICKS - 1000);
		// Once loaded, the mob is watched through several enrage checks before its calm is read.
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(mob.getUUID()), "the mob has not loaded yet"))
			.thenIdle(3 * RaidInstance.CHASE_EVERY_TICKS)
			.thenExecute(() -> {
				boolean enraged = enraged(mob);
				NetherTestSupport.result("calmBefore", "loaded=true enraged=" + enraged + " elapsed=" + RaidInstance.state(server).elapsed());
				mob.discard();
				NetherTestSupport.leave(helper, player);
				helper.assertTrue(!enraged, "before thirty minutes the mob is enraged");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:lateload", maxTicks = 400)
	public void aMobThatLoadsAfterTheEnrageIsEnraged(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		setElapsed(server, RaidInstance.ENRAGE_TICKS + 100);
		ZombifiedPiglin mob = RaidModeGameTest.spawnInRaid((ServerLevel) player.level(), player.position().add(3.0, 0.0, 0.0));
		helper.succeedWhen(() -> {
			helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(mob.getUUID()), "the mob has not loaded yet");
			NetherTestSupport.result("lateLoadEnraged", "enraged=" + enraged(mob));
			helper.assertTrue(enraged(mob), "a mob loaded after the enrage is calm");
			mob.discard();
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:hunt", maxTicks = 400)
	public void anEnragedZombifiedPiglinHuntsThePlayer(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		NetherTestSupport.vulnerable(player);
		ZombifiedPiglin mob = RaidModeGameTest.spawnInRaid((ServerLevel) player.level(), player.position().add(3.0, 0.0, 0.0));
		setElapsed(server, RaidInstance.ENRAGE_TICKS + 100);
		helper.succeedWhen(() -> {
			helper.assertTrue(mob.getTarget() == player, "the mob targets " + mob.getTarget());
			NetherTestSupport.result("enragedHunts", "target=" + mob.getTarget().getScoreboardName());
			mob.discard();
			NetherTestSupport.leave(helper, player);
		});
	}
}
