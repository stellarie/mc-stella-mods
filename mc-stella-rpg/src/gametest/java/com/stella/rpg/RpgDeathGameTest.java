package com.stella.rpg;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;

public final class RpgDeathGameTest {
	private static final float LETHAL_DAMAGE = 1000.0F;

	@GameTest(maxTicks = 40)
	public void levelAndProgressSurviveDeath(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 3;
		player.experienceProgress = 0.4F;
		player.totalExperience = 27;
		ServerPlayer reborn = dieAndRespawn(helper, player);
		helper.assertTrue(reborn.experienceLevel == 3, "level=" + reborn.experienceLevel);
		helper.assertTrue(reborn.experienceProgress == 0.4F, "progress=" + reborn.experienceProgress);
		RpgTestSupport.result("levelAndProgressSurviveDeath", "level=" + reborn.experienceLevel + " progress=" + reborn.experienceProgress);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void deathDropsNoExperience(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 30;
		AABB around = player.getBoundingBox().inflate(8.0);
		kill(helper, player);
		List<ExperienceOrb> orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around);
		helper.assertTrue(orbs.isEmpty(), "orbs=" + orbs.size());
		RpgTestSupport.result("deathDropsNoExperience", "orbs=" + orbs.size());
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void allocationSurvivesDeath(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.experienceLevel = 8;
		helper.assertTrue(StatPoints.allocate(player, Stat.DEX, 7) == StatPoints.Result.OK, "allocation refused");
		helper.assertTrue(StatPoints.allocate(player, Stat.LUK, 3) == StatPoints.Result.OK, "allocation refused");
		StatSheet sheet = StatPoints.sheet(player);
		int unspent = StatPoints.unspent(player.experienceLevel, sheet);
		ServerPlayer reborn = dieAndRespawn(helper, player);
		StatSheet after = StatPoints.sheet(reborn);
		int unspentAfter = StatPoints.unspent(reborn.experienceLevel, after);
		helper.assertTrue(after.equals(sheet), "sheet " + sheet + " became " + after);
		helper.assertTrue(unspent == unspentAfter, "unspent " + unspent + " became " + unspentAfter);
		RpgTestSupport.result("allocationSurvivesDeath", "sheet=" + after + " unspent=" + unspentAfter);
		helper.succeed();
	}

	private static void kill(final GameTestHelper helper, final ServerPlayer player) {
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), LETHAL_DAMAGE);
		helper.assertTrue(player.isDeadOrDying(), "the player survived, so the case proves nothing");
	}

	private static ServerPlayer dieAndRespawn(final GameTestHelper helper, final ServerPlayer player) {
		kill(helper, player);
		return helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
	}
}
