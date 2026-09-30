package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

public final class RpgLevelUpGameTest {
	private static final float LOW_HEALTH = 6.0F;
	private static final int SETTLE_TICKS = 3;

	@GameTest(maxTicks = 60)
	public void gainHealsAndHastes(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 4;
		player.setAttached(RpgLevelUp.LAST_LEVEL, 4);
		player.setHealth(LOW_HEALTH);
		player.experienceLevel = 5;
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), "health=" + player.getHealth());
			MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
			helper.assertTrue(speed != null, "no speed effect");
			helper.assertTrue(speed.getAmplifier() == 1, "amplifier=" + speed.getAmplifier());
			helper.assertTrue(speed.getDuration() <= 600 && speed.getDuration() > 0, "duration=" + speed.getDuration());
			RpgTestSupport.result("gainHealsAndHastes", "health=" + player.getHealth() + " amplifier=" + speed.getAmplifier() + " duration=" + speed.getDuration());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void multiLevelOnce(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 4;
		player.setAttached(RpgLevelUp.LAST_LEVEL, 4);
		player.setHealth(LOW_HEALTH);
		player.experienceLevel = 7;
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), "health=" + player.getHealth());
			MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
			helper.assertTrue(speed != null && speed.getAmplifier() == 1, "speed=" + speed);
			player.setHealth(LOW_HEALTH);
			helper.runAfterDelay(SETTLE_TICKS, () -> {
				helper.assertTrue(player.getHealth() < player.getMaxHealth(), "a second refresh healed the player");
				helper.assertTrue(player.getAttached(RpgLevelUp.LAST_LEVEL) == 7, "stored=" + player.getAttached(RpgLevelUp.LAST_LEVEL));
				RpgTestSupport.result("multiLevelOnce", "health=" + player.getHealth() + " stored=" + player.getAttached(RpgLevelUp.LAST_LEVEL));
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 60)
	public void noTriggerOnRespawn(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 5;
		player.setAttached(RpgLevelUp.LAST_LEVEL, 5);
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		helper.assertTrue(player.isDeadOrDying(), "the player survived, so the case proves nothing");
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		helper.assertTrue(reborn.experienceLevel == 5, "level=" + reborn.experienceLevel);
		reborn.setHealth(LOW_HEALTH);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			helper.assertTrue(reborn.getHealth() < reborn.getMaxHealth(), "health=" + reborn.getHealth());
			helper.assertFalse(reborn.hasEffect(MobEffects.SPEED), "speed applied on respawn");
			RpgTestSupport.result("noTriggerOnRespawn", "health=" + reborn.getHealth() + " speed=" + reborn.hasEffect(MobEffects.SPEED));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void noTriggerFirstSeen(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.removeAttached(RpgLevelUp.LAST_LEVEL);
		player.experienceLevel = 10;
		player.setHealth(LOW_HEALTH);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			helper.assertTrue(player.getHealth() < player.getMaxHealth(), "health=" + player.getHealth());
			helper.assertFalse(player.hasEffect(MobEffects.SPEED), "speed applied on first check");
			helper.assertTrue(player.getAttached(RpgLevelUp.LAST_LEVEL) == 10, "stored=" + player.getAttached(RpgLevelUp.LAST_LEVEL));
			RpgTestSupport.result("noTriggerFirstSeen", "health=" + player.getHealth() + " stored=" + player.getAttached(RpgLevelUp.LAST_LEVEL));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void levelLossNoTrigger(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 9;
		player.setAttached(RpgLevelUp.LAST_LEVEL, 9);
		player.setHealth(LOW_HEALTH);
		player.experienceLevel = 7;
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			helper.assertTrue(player.getHealth() < player.getMaxHealth(), "health=" + player.getHealth());
			helper.assertFalse(player.hasEffect(MobEffects.SPEED), "speed applied on level loss");
			helper.assertTrue(player.getAttached(RpgLevelUp.LAST_LEVEL) == 7, "stored=" + player.getAttached(RpgLevelUp.LAST_LEVEL));
			RpgTestSupport.result("levelLossNoTrigger", "health=" + player.getHealth() + " stored=" + player.getAttached(RpgLevelUp.LAST_LEVEL));
			helper.succeed();
		});
	}
}
