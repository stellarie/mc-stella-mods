package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteBounty;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** An elite that a player kills drops emeralds by its level; a kill by another cause pays nothing. */
public class EliteBountyGameTest {
	private static final int LEVEL = 40;

	@GameTest
	public void playerKillPaysByLevel(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		int paid = kill(helper, level.damageSources().playerAttack(player));
		int expected = EliteBounty.emeralds(LEVEL, false);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "eliteBounty", "paid=" + paid + " expected=" + expected);
		helper.assertTrue(expected == 6 && paid == expected, "a level-40 elite paid " + paid + ", expected " + expected);
		helper.assertTrue(EliteBounty.emeralds(1000, false) == EliteBounty.CAP && EliteBounty.emeralds(LEVEL, true) == 24,
			"the cap or the boss factor is wrong");
		helper.succeed();
	}

	@GameTest
	public void otherKillsPayNothing(final GameTestHelper helper) {
		int paid = kill(helper, helper.getLevel().damageSources().generic());
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "eliteBountyNoPlayer", "paid=" + paid);
		helper.assertTrue(paid == 0, "a kill without a player paid " + paid);
		helper.succeed();
	}

	@GameTest
	public void summonedElitesPayNothing(final GameTestHelper helper) {
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		int paid = kill(helper, helper.getLevel().damageSources().playerAttack(player), true);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "eliteBountySummoned", "paid=" + paid);
		helper.assertTrue(paid == 0, "a summoned elite paid " + paid);
		helper.succeed();
	}

	private static int kill(final GameTestHelper helper, final DamageSource source) {
		return kill(helper, source, false);
	}

	private static int kill(final GameTestHelper helper, final DamageSource source, final boolean summoned) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
		zombie.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		Elite.mark(zombie, GearTier.DIAMOND, 1.0);
		com.stella.mobs.behavior.EliteState.setSummoned(zombie, summoned);
		((AttachmentTarget) zombie).setAttached(ThreatState.TYPE, new ThreatState(0, ThreatState.MIN_VARIATION, 0, LEVEL));
		zombie.setHealth(1.0F);
		zombie.hurtServer(helper.getLevel(), source, 1000.0F);
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(zombie.blockPosition()).inflate(3.0),
			item -> item.getItem().is(Items.EMERALD));
		int count = drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
		drops.forEach(ItemEntity::discard);
		return count;
	}
}
