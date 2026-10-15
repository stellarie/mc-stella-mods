package com.stella.mobs;

import com.stella.mobs.behavior.EliteState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** A boss rejects every harmful effect. An elite accepts about one in four. */
public class StatusResistGameTest {
	@GameTest
	public void bossRejectsSlownessAndEliteAcceptsAboutAQuarter(final GameTestHelper helper) {
		Mob boss = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		EliteState.setBoss(boss, true);
		boolean bossHit = boss.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 6));
		helper.assertTrue(!bossHit && !boss.hasEffect(MobEffects.SLOWNESS), "the boss took Slowness");

		Mob elite = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 3, 2, 3);
		EliteState.markElite(elite, com.stella.mobs.gear.GearTier.LEATHER);
		int landed = 0;
		for (int i = 0; i < 200; i++) {
			elite.removeAllEffects();
			if (elite.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 6))) {
				landed++;
			}
		}
		helper.assertTrue(landed >= 25 && landed <= 80, "the elite took " + landed + " of 200");
		elite.removeAllEffects();
		helper.assertTrue(elite.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1)), "the elite refused Speed");
		helper.succeed();
	}
}
