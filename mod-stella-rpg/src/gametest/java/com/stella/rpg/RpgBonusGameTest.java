package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class RpgBonusGameTest {
	private static final float HIT = 4.0F;

	@GameTest
	public void bonusAddsToEffective(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.STR, 95);
		StatBonuses.Provider provider = (entity, stat) -> entity == player && stat == Stat.STR ? 5 : 0;
		StatBonuses.register(provider);
		try {
			helper.assertTrue(StatBonuses.effective(player, Stat.STR) == 100, "effective=" + StatBonuses.effective(player, Stat.STR));
			String line = RpgCommand.statusLine(player, Stat.STR);
			helper.assertTrue(line.equals("STR 95 + 5"), "line=" + line);

			RpgCombat.setRoller((attacker, random, chance) -> false);
			RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
			Zombie victim = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
			RpgTestSupport.bare(victim);
			victim.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
			victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
			float loss = victim.getMaxHealth() - victim.getHealth();
			float expected = HIT + RpgDice.of(100).max();
			float atBase = HIT + RpgDice.of(95).max();
			RpgTestSupport.result("bonusAddsToEffective", "line=" + line + " loss=" + loss + " expected=" + expected);
			helper.assertTrue(Math.abs(loss - expected) < 0.0001F, "loss=" + loss + " expected " + expected);
			helper.assertTrue(Math.abs(loss - atBase) > 0.0001F, "damage ignored the bonus");
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
			StatBonuses.unregister(provider);
		}
		helper.succeed();
	}

	@GameTest
	public void statusLineOmitsZeroBonus(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.STR, 25);
		String line = RpgCommand.statusLine(player, Stat.STR);
		helper.assertTrue(line.equals("STR 25"), "line=" + line);
		helper.succeed();
	}
}
