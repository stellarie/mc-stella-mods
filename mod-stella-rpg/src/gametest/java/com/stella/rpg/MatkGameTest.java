package com.stella.rpg;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** MATK, the spell damage and heal calls, and the mob spell rule. */
public final class MatkGameTest {
	private static final double EPSILON = 1e-9;

	private static final class Stub implements MobsBridge.Facts {
		private final Map<Mob, Integer> threats = new IdentityHashMap<>();

		@Override
		public int threat(final Mob mob) {
			return this.threats.getOrDefault(mob, 0);
		}

		@Override
		public String role(final Mob mob) {
			return "magic";
		}
	}

	@GameTest
	public void matkPartsFromGear(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.INT, 100);
		helper.assertTrue(Math.abs(RpgSpells.matkPercent(player) - 1.0) < EPSILON, "bare MATK percent " + RpgSpells.matkPercent(player));
		ItemStack book = RpgTestSupport.withAffixes(Items.BOOK, Map.of("matk_flat", 20, "matk_pct", 50));
		CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> tag.putInt("stella_refine", 3));
		player.setItemSlot(EquipmentSlot.MAINHAND, book);
		double percent = RpgSpells.matkPercent(player);
		double flat = RpgSpells.matkFlat(player);
		helper.assertTrue(Math.abs(percent - 1.05) < EPSILON, "book MATK percent " + percent);
		helper.assertTrue(Math.abs(flat - (20.0 + 12.0 * 3)) < EPSILON, "book flat MATK " + flat);

		ItemStack sword = RpgTestSupport.withAffixes(Items.IRON_SWORD, Map.of("matk_flat", 10));
		CustomData.update(DataComponents.CUSTOM_DATA, sword, tag -> tag.putInt("stella_refine", 9));
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		double swordFlat = RpgSpells.matkFlat(player);
		helper.assertTrue(Math.abs(swordFlat - 10.0) < EPSILON, "a refined sword gave book refine MATK: " + swordFlat);
		player.setItemSlot(EquipmentSlot.MAINHAND, book);
		double hundred = RpgSpells.spellDamage(player, 100.0);
		helper.assertTrue(Math.abs(RpgSpells.matkMin(player) - hundred * RpgScale.k()) < EPSILON
			&& RpgSpells.matkMax(player) == RpgSpells.matkMin(player), "the compatibility helpers drifted");
		player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		RpgTestSupport.result("matkPartsFromGear", "percent=" + percent + " flat=" + flat + " swordFlat=" + swordFlat);
		helper.succeed();
	}

	@GameTest
	public void spellDamageAndHeal(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		float k = RpgScale.kf();
		player.experienceLevel = 10;
		// Level 10 adds 3 bonus points, so base 97 makes INT 100.
		RpgTestSupport.setStat(player, Stat.INT, 97);
		// base(10) = 12 x 1.45 = 17.4. No roll, so every cast is the same.
		double expected = 17.4 * 1.5 * 2.0;
		RpgCombat.setRoller((attacker, random, chance) -> false);
		try {
			for (int i = 0; i < 50; i++) {
				double damage = RpgSpells.spellDamage(player, 150.0);
				helper.assertTrue(Math.abs(damage - expected) < EPSILON, "spellDamage " + damage + " expected " + expected);
			}
		} finally {
			RpgCombat.setRoller(null);
		}
		RpgTestSupport.setStat(player, Stat.INT, 17);
		double heal = RpgSpells.spellHeal(player, 2.0);
		RpgTestSupport.result("spellDamageAndHeal", "damage=" + expected + " heal=" + heal);
		helper.assertTrue(Math.abs(heal - (10 + 20) * 2.0 / k) < EPSILON && Math.abs(heal * k - 60.0) < 1e-6, "spellHeal was " + heal);
		helper.succeed();
	}

	@GameTest
	public void mobSpellsUseThreatAndInt(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.INT, 100);
		helper.assertTrue(RpgSpells.power(player) == 1.0, "power is " + RpgSpells.power(player));
		helper.assertTrue(Math.abs(RpgSpells.cooldown(player) - 0.9) < 1e-4, "the cooldown curve changed: " + RpgSpells.cooldown(player));

		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		// Mob LUK gives a crit chance, so the no-crit case needs a fixed roller.
		RpgCombat.setRoller((attacker, random, chance) -> false);
		try {
			Zombie caster = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
			RpgTestSupport.bare(caster);
			int intel = StatBonuses.effective(caster, Stat.INT);
			helper.assertTrue(intel == 14, "the caster has INT " + intel);
			float one = spellLoss(helper, caster, 1.0F);
			float three = spellLoss(helper, caster, 3.0F);
			stub.threats.put(caster, 41);
			float strong = spellLoss(helper, caster, 1.0F);
			// Threat 0 counts as level 1. A point is 50 percent. The bare victim resists 25 percent.
			double level1 = 12.0 * 0.5 * 1.14 * 0.75;
			int strongInt = MobStats.base(RpgConfig.defaults(), "magic", Stat.INT, 41);
			// The level 0 victim takes 10 percent more for each 10 threat levels, so 40 levels give 1.4.
			double level41 = 12.0 * (1.0 + 0.05 * 40) * 0.5 * (1.0 + strongInt / 100.0) * 0.75 * 1.4;
			RpgTestSupport.result("mobSpellsUseThreatAndInt", "one=" + one + " three=" + three + " strong=" + strong);
			helper.assertTrue(Math.abs(one - level1) < 0.01F, "a 1 point mob spell dealt " + one + " expected " + level1);
			helper.assertTrue(Math.abs(three - 3.0 * level1) < 0.01F, "a 3 point mob spell dealt " + three);
			helper.assertTrue(Math.abs(strong - level41) < 0.01F, "a threat 41 spell dealt " + strong + " expected " + level41);
		} finally {
			RpgCombat.setRoller(null);
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	public void mobSpellCrits(final GameTestHelper helper) {
		Zombie caster = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.bare(caster);
		float plain;
		float crit;
		try {
			RpgCombat.setRoller((attacker, random, chance) -> false);
			plain = spellLoss(helper, caster, 1.0F);
			RpgCombat.setRoller((attacker, random, chance) -> true);
			crit = spellLoss(helper, caster, 1.0F);
		} finally {
			RpgCombat.setRoller(null);
		}
		RpgTestSupport.result("mobSpellCrits", "plain=" + plain + " crit=" + crit);
		// A crit ignores the 25 percent magic resistance of the bare victim.
		helper.assertTrue(Math.abs(crit - plain / 0.75F * RpgCurve.critMultiplier()) < 0.01F, "a mob spell crit dealt " + crit + " against " + plain);
		float strongPlain;
		float strongCrit;
		try {
			RpgCombat.setRoller((attacker, random, chance) -> false);
			strongPlain = spellLoss(helper, caster, 1.0F, 1024);
			RpgCombat.setRoller((attacker, random, chance) -> true);
			strongCrit = spellLoss(helper, caster, 1.0F, 1024);
		} finally {
			RpgCombat.setRoller(null);
		}
		helper.assertTrue(Math.abs(strongCrit - strongPlain / 0.25F * RpgCurve.critMultiplier()) < 0.01F,
			"a crit on INT 1024 dealt " + strongCrit + " against " + strongPlain);
		helper.succeed();
	}

	@GameTest
	public void summonedSpellPassesThrough(final GameTestHelper helper) {
		Zombie servant = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.bare(servant);
		servant.addTag("mod-stella-spells.summoned");
		float loss;
		try {
			RpgCombat.setRoller((attacker, random, chance) -> true);
			loss = spellLoss(helper, servant, 4.0F);
		} finally {
			RpgCombat.setRoller(null);
		}
		RpgTestSupport.result("summonedSpellPassesThrough", "loss=" + loss);
		// The amount stays 4, and only the 25 percent magic resistance of the bare victim applies.
		helper.assertTrue(Math.abs(loss - 3.0F) < 0.01F, "a servant spell dealt " + loss + " expected 3.0");
		helper.succeed();
	}

	/** The health lost by a bare player, in vanilla units. The victim's max health is large, so nothing clamps. */
	private static float spellLoss(final GameTestHelper helper, final Mob caster, final float amount) {
		return spellLoss(helper, caster, amount, 0);
	}

	private static float spellLoss(final GameTestHelper helper, final Mob caster, final float amount, final int victimInt) {
		ServerPlayer victim = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(victim);
		RpgTestSupport.setStat(victim, Stat.INT, victimInt);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0);
		victim.setHealth(victim.getMaxHealth());
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().indirectMagic(caster, caster), amount);
		return (before - victim.getHealth()) / RpgScale.kf();
	}
}
