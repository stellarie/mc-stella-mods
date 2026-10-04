package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * A percent hit (instant Harming, Poison, Wither) already scales with the victim's max health, so the attacking mob's
 * INT, level gap, and crit do not multiply it again. A plain mob spell of the same amount still takes them.
 */
public class PercentHitGameTest {
	private static final float AMOUNT = 4.0F;

	@GameTest
	public void percentHitsSkipMobScaling(final GameTestHelper helper) {
		MobsBridge.Facts stub = new MobsBridge.Facts() {
			@Override
			public int threat(final Mob mob) {
				return 200;
			}

			@Override
			public String role(final Mob mob) {
				return "magic";
			}
		};
		MobsBridge.setFacts(stub);
		RpgCombat.setRoller((attacker, random, chance) -> false);
		try {
			Witch witch = helper.spawnWithNoFreeWill(EntityTypes.WITCH, 1, 2, 1);
			float plain = loss(helper, witch, false);
			float fixed = loss(helper, witch, true);
			RpgTestSupport.result("percentHitsSkipMobScaling", "plain=" + plain + " fixed=" + fixed);
			helper.assertTrue(plain > fixed * 1.5F, "a plain mob spell was not scaled above the percent hit: " + plain + " vs " + fixed);
			// Magic resistance and VIT still apply to a percent hit.
			helper.assertTrue(fixed <= AMOUNT * 0.75F * RpgScale.kf() + 0.5F,
				"the percent hit was scaled: " + fixed + ", expected " + AMOUNT * 0.75F * RpgScale.kf());
		} finally {
			RpgCombat.setRoller(null);
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	public void overkillShowsTheFullHit(final GameTestHelper helper) {
		int partial = DamageLog.shown(50.0F, 40.0F);
		int over = DamageLog.shown(50.0F, 900.0F);
		int none = DamageLog.shown(50.0F, null);
		RpgTestSupport.result("overkillShowsTheFullHit", "partial=" + partial + " over=" + over + " none=" + none);
		helper.assertTrue(partial == 50 && over == 900 && none == 50, "shown " + partial + ", " + over + ", " + none);
		helper.succeed();
	}

	private static float loss(final GameTestHelper helper, final Mob caster, final boolean percent) {
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 3, 2, 3);
		RpgTestSupport.bare(victim);
		victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000.0);
		victim.setHealth(victim.getMaxHealth());
		float before = victim.getHealth();
		var source = helper.getLevel().damageSources().indirectMagic(caster, caster);
		if (percent) {
			PercentEffects.fixedHit(() -> victim.hurtServer(helper.getLevel(), source, AMOUNT));
		} else {
			victim.hurtServer(helper.getLevel(), source, AMOUNT);
		}
		float lost = before - victim.getHealth();
		victim.discard();
		return lost;
	}
}
