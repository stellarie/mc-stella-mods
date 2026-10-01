package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** With Nausea an attack fails 70% of the time and deals no damage; without it, never. */
public final class NauseaGameTest {
	@GameTest
	public void fumbleRate(final GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RandomSource seeded = RandomSource.create(70L);
		int samples = 10_000;
		int sober = 0;
		int fumbles = 0;
		try {
			RpgCombat.setFumbleRoller((attacker, random, chance) -> chance > 0.0 && seeded.nextDouble() < chance);
			for (int i = 0; i < 100; i++) {
				sober += RpgCombat.fumbles(zombie) ? 1 : 0;
			}
			zombie.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20_000));
			for (int i = 0; i < samples; i++) {
				fumbles += RpgCombat.fumbles(zombie) ? 1 : 0;
			}
		} finally {
			RpgCombat.setFumbleRoller(null);
		}
		double sigma = Math.sqrt(samples * 0.7 * 0.3);
		RpgTestSupport.result("fumbleRate", "sober=" + sober + " fumbles=" + fumbles + " of=" + samples);
		helper.assertTrue(sober == 0, "an attacker without Nausea fumbled");
		helper.assertTrue(Math.abs(fumbles - samples * RpgCombat.NAUSEA_FUMBLE_CHANCE) <= 3.0 * sigma, "the fumble rate was " + fumbles);
		helper.succeed();
	}

	@GameTest
	public void fumbleDealsNoDamage(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		zombie.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20_000));
		float missed;
		float landed;
		try {
			RpgCombat.setFumbleRoller((attacker, random, chance) -> true);
			missed = RpgCombat.scaleOutgoing(player, helper.getLevel().damageSources().mobAttack(zombie), 5.0F);
			RpgCombat.setFumbleRoller((attacker, random, chance) -> false);
			landed = RpgCombat.scaleOutgoing(player, helper.getLevel().damageSources().mobAttack(zombie), 5.0F);
		} finally {
			RpgCombat.setFumbleRoller(null);
		}
		RpgTestSupport.result("fumbleDealsNoDamage", "missed=" + missed + " landed=" + landed);
		helper.assertTrue(missed == 0.0F && landed > 0.0F, "missed=" + missed + " landed=" + landed);
		helper.succeed();
	}
}
