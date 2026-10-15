package com.stella.rpg;

import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The magic model: spell hit, magic resistance and penetration, and the cooldown from INT and DEX. */
public final class MagicModelGameTest {
	private static final double EPSILON = 1e-6;
	private static final float HIT = 10.0F;

	@GameTest
	public void spellHitFollowsTheFormula(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		// Level bonus points give LUK, and LUK gives a crit chance. The hit under test has no crit.
		RpgCombat.setRoller((attacker, random, chance) -> false);
		try {
			checkFormula(helper, player);
		} finally {
			RpgCombat.setRoller(null);
		}
		helper.succeed();
	}

	private static void checkFormula(final GameTestHelper helper, final ServerPlayer player) {
		player.experienceLevel = 1;
		RpgTestSupport.setStat(player, Stat.INT, 13);
		// MATK 3 x 13 = 39, at 150 percent, in vanilla units.
		double plain = RpgSpells.spellDamage(player, 150.0);
		double expectedPlain = 39.0 * 1.5 / RpgScale.k();
		helper.assertTrue(Math.abs(plain - expectedPlain) < EPSILON, "level 1 INT 13 dealt " + plain + " expected " + expectedPlain);

		player.experienceLevel = 100;
		// Level 100 adds 30 bonus points, so base 82 makes INT 112.
		RpgTestSupport.setStat(player, Stat.INT, 82);
		player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.BOOK, Map.of("matk_pct", 150, "matk_flat", 50)));
		// (3 x 112 + 50) x (1 + 0.15) x 1.5 / K.
		double geared = RpgSpells.spellDamage(player, 150.0);
		double again = RpgSpells.spellDamage(player, 150.0);
		RpgTestSupport.result("spellHitFollowsTheFormula", "plain=" + plain + " geared=" + geared);
		helper.assertTrue(Math.abs(geared - 386.0 * 1.15 * 1.5 / RpgScale.k()) < EPSILON, "level 100 INT 112 dealt " + geared);
		helper.assertTrue(geared == again, "two casts differ: " + geared + " " + again);
	}

	@GameTest
	public void resistanceAndPenetration(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(victim);
		ServerPlayer caster = RpgTestSupport.player(helper);
		DamageSource unattributed = level.damageSources().magic();
		DamageSource spell = level.damageSources().indirectMagic(caster, caster);

		int vit = StatBonuses.effective(victim, Stat.VIT);
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, unattributed, HIT), 10.0 * (1.0 - RpgSpells.resistance(0, vit, 0.0))), "INT 0 resistance");
		RpgTestSupport.setStat(victim, Stat.INT, 50);
		double r50 = RpgSpells.resistance(StatBonuses.effective(victim, Stat.INT), vit, 0.0);
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, unattributed, HIT), 10.0 * (1.0 - r50)), "INT 50 resistance " + r50);
		RpgTestSupport.setStat(victim, Stat.INT, 1024);
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, unattributed, HIT), 10.0 * (1.0 - RpgSpells.resistance(1024, vit, 0.0))), "INT 1024 resistance");
		helper.assertTrue(RpgSpells.resistance(1024, vit, 0.0) < 0.6 + 1e-9, "the stat part passed 60 percent");

		// The turtle helmet has no armor set, so no flat defense mixes in.
		victim.setItemSlot(EquipmentSlot.HEAD, RpgTestSupport.withAffixes(Items.TURTLE_HELMET, Map.of("magic_res_pct", 200)));
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, unattributed, HIT), 10.0 * (1.0 - RpgSpells.MRES_CAP)), "resistance passed the 80 percent cap");
		RpgTestSupport.setStat(victim, Stat.INT, 0);
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, unattributed, HIT), 10.0 * (1.0 - RpgSpells.resistance(0, vit, 0.2))), "the resistance affix did not add 20 percent");
		victim.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

		RpgTestSupport.setStat(victim, Stat.INT, 50);
		double base = 1.0 - r50;
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, spell, HIT), 10.0 * base), "an attacker with no book changed the hit");
		caster.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.BOOK, Map.of("magic_pen", 150)));
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, spell, HIT), 10.0 * (base + 0.15)), "15 percent penetration should leave 10 percent resistance");
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, unattributed, HIT), 10.0 * base), "a source with no attacker was pierced");
		caster.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.BOOK, Map.of("magic_pen", 300)));
		helper.assertTrue(near(RpgCombat.scaleIncoming(victim, spell, HIT), 10.0), "penetration above resistance went below zero");

		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0);
		victim.setHealth(victim.getMaxHealth());
		caster.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.BOOK, Map.of("magic_pen", 150)));
		float before = victim.getHealth();
		victim.hurtServer(level, spell, HIT);
		float lost = before - victim.getHealth();
		RpgTestSupport.result("resistanceAndPenetration", "lost=" + lost);
		helper.assertTrue(Math.abs(lost - 10.0F * (float) (base + 0.15) * RpgScale.kf()) < 0.01F * RpgScale.kf(), "the real hit lost " + lost);
		helper.succeed();
	}

	@GameTest
	public void cooldownFromIntAndDex(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.INT, 100);
		RpgTestSupport.setStat(player, Stat.DEX, 100);
		double both = RpgSpells.cooldown(player);
		RpgTestSupport.setStat(player, Stat.INT, 0);
		RpgTestSupport.setStat(player, Stat.DEX, 300);
		double dexOnly = RpgSpells.cooldown(player);
		RpgTestSupport.setStat(player, Stat.INT, 1024);
		RpgTestSupport.setStat(player, Stat.DEX, 1024);
		double maxed = RpgSpells.cooldown(player);
		RpgTestSupport.result("cooldownFromIntAndDex", "both=" + both + " dexOnly=" + dexOnly + " maxed=" + maxed);
		helper.assertTrue(Math.abs(both - 0.6) < EPSILON, "INT 100 DEX 100 gave " + both);
		helper.assertTrue(Math.abs(dexOnly - 0.52) < EPSILON, "DEX 300 gave " + dexOnly);
		helper.assertTrue(Math.abs(maxed - 0.271174) < EPSILON && maxed > 0.2, "INT 1024 DEX 1024 gave " + maxed);
		helper.succeed();
	}

	@GameTest
	public void spellCritUsesLukAndMultiplier(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 1;
		RpgTestSupport.setStat(player, Stat.INT, 10);
		RpgTestSupport.setStat(player, Stat.LUK, 100);
		double[] chance = {-1.0};
		double plain;
		double crit;
		try {
			RpgCombat.setRoller((attacker, random, rolled) -> {
				chance[0] = rolled;
				return false;
			});
			plain = RpgSpells.spellDamage(player, 100.0);
			RpgCombat.setRoller((attacker, random, rolled) -> true);
			crit = RpgSpells.spellDamage(player, 100.0);
		} finally {
			RpgCombat.setRoller(null);
		}
		RpgTestSupport.result("spellCritUsesLukAndMultiplier", "chance=" + chance[0] + " plain=" + plain + " crit=" + crit);
		helper.assertTrue(Math.abs(chance[0] - 0.125) < EPSILON, "the spell crit chance was " + chance[0] + " expected 0.125 for LUK 100");
		helper.assertTrue(Math.abs(plain - 30.0 / RpgScale.k()) < EPSILON, "a spell without a crit dealt " + plain);
		helper.assertTrue(Math.abs(crit - plain * RpgCurve.critMultiplier()) < EPSILON, "a spell crit dealt " + crit);
		helper.succeed();
	}

	private static boolean near(final float actual, final double expected) {
		return Math.abs(actual - expected) < 0.0005;
	}
}
