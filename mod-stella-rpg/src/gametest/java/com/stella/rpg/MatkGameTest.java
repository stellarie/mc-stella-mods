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
	private static final int[] INT = {20, 50, 100, 200};
	private static final int[] MIN = {24, 99, 296, 984};
	private static final int[] MAX = {36, 150, 500, 1800};
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
	public void matkTable(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < INT.length; i++) {
			RpgTestSupport.setStat(player, Stat.INT, INT[i]);
			double min = RpgSpells.matkMin(player);
			double max = RpgSpells.matkMax(player);
			log.append(INT[i]).append(':').append(min).append('-').append(max).append(' ');
			helper.assertTrue(Math.abs(min - MIN[i]) < EPSILON, "matkMin at INT " + INT[i] + " was " + min + " expected " + MIN[i]);
			helper.assertTrue(Math.abs(max - MAX[i]) < EPSILON, "matkMax at INT " + INT[i] + " was " + max + " expected " + MAX[i]);
		}
		RpgTestSupport.setStat(player, Stat.INT, 100);
		ItemStack book = RpgTestSupport.withAffixes(Items.BOOK, Map.of("matk_flat", 20, "matk_pct", 50));
		CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> tag.putInt("stella_refine", 3));
		player.setItemSlot(EquipmentSlot.MAINHAND, book);
		double flat = 20.0 + 12.0 * 3;
		double bookMin = RpgSpells.matkMin(player);
		double bookMax = RpgSpells.matkMax(player);
		helper.assertTrue(Math.abs(bookMin - (296 + flat) * 1.05) < EPSILON, "book matkMin " + bookMin);
		helper.assertTrue(Math.abs(bookMax - (500 + flat) * 1.05) < EPSILON, "book matkMax " + bookMax);

		ItemStack sword = RpgTestSupport.withAffixes(Items.IRON_SWORD, Map.of("matk_flat", 10));
		CustomData.update(DataComponents.CUSTOM_DATA, sword, tag -> tag.putInt("stella_refine", 9));
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		double swordMin = RpgSpells.matkMin(player);
		helper.assertTrue(Math.abs(swordMin - 306) < EPSILON, "a refined sword gave book refine MATK: " + swordMin);
		player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		RpgTestSupport.result("matkTable", log.toString().trim() + " book=" + bookMin + "-" + bookMax + " sword=" + swordMin);
		helper.succeed();
	}

	@GameTest
	public void spellDamageAndHeal(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		float k = RpgScale.kf();
		RpgTestSupport.setStat(player, Stat.INT, 100);
		double low = Double.MAX_VALUE;
		double high = 0.0;
		for (int i = 0; i < 600; i++) {
			double damage = RpgSpells.spellDamage(player, 150.0);
			low = Math.min(low, damage);
			high = Math.max(high, damage);
			helper.assertTrue(damage >= 296 * 1.5 / k - EPSILON && damage <= 500 * 1.5 / k + EPSILON, "spellDamage " + damage + " left the range");
		}
		helper.assertTrue(low < 296 * 1.5 / k + 1.0 && high > 500 * 1.5 / k - 1.0, "spellDamage never spread: " + low + ".." + high);

		RpgTestSupport.setStat(player, Stat.INT, 14);
		double exact = RpgSpells.spellDamage(player, 200.0);
		helper.assertTrue(Math.abs(exact - 18 * 2.0 / k) < EPSILON, "spellDamage at INT 14 was " + exact);

		RpgTestSupport.setStat(player, Stat.INT, 20);
		player.experienceLevel = 10;
		double heal = RpgSpells.spellHeal(player, 2.0);
		RpgTestSupport.result("spellDamageAndHeal", "low=" + low + " high=" + high + " exact=" + exact + " heal=" + heal);
		helper.assertTrue(Math.abs(heal - (10 + 20) * 2.0 / k) < EPSILON && Math.abs(heal * k - 60.0) < 1e-6, "spellHeal was " + heal);
		helper.succeed();
	}

	@GameTest
	public void mobSpellsUseMatk(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.INT, 100);
		helper.assertTrue(RpgSpells.power(player) == 1.0, "power is " + RpgSpells.power(player));
		helper.assertTrue(Math.abs(RpgSpells.cooldown(player) - 0.9) < 1e-4, "the cooldown curve changed: " + RpgSpells.cooldown(player));

		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			Zombie caster = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
			RpgTestSupport.bare(caster);
			float k = RpgScale.kf();
			int intel = StatBonuses.effective(caster, Stat.INT);
			helper.assertTrue(intel == 14 && RpgSpells.matkMin(caster) == RpgSpells.matkMax(caster), "the caster has INT " + intel);
			float three = spellLoss(helper, caster, 3.0F);
			float six = spellLoss(helper, caster, 6.0F);
			double matk = RpgSpells.matkMin(caster);
			stub.threats.put(caster, 200);
			double lowMatk = RpgSpells.matkMin(caster);
			double highMatk = RpgSpells.matkMax(caster);
			float strong = spellLoss(helper, caster, 3.0F);
			RpgTestSupport.result("mobSpellsUseMatk", "matk=" + matk + " three=" + three + " six=" + six + " strong=" + strong
				+ " range=" + lowMatk + "-" + highMatk);
			helper.assertTrue(Math.abs(three - matk) < 0.01F, "a 3 point mob spell dealt " + three + " expected " + matk);
			helper.assertTrue(Math.abs(six - 2.0 * matk) < 0.01F, "a 6 point mob spell dealt " + six + " expected " + 2.0 * matk);
			helper.assertTrue(lowMatk < highMatk && strong >= lowMatk - 0.01F && strong <= highMatk + 0.01F,
				"a threat 200 spell dealt " + strong + " outside " + lowMatk + ".." + highMatk);
			helper.assertTrue(k > 1.0F, "K is " + k);
		} finally {
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	/** The health lost by a bare player without stats, in K units, so it equals the MATK roll times the skill share. */
	private static float spellLoss(final GameTestHelper helper, final Mob caster, final float amount) {
		ServerPlayer victim = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(victim);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0);
		victim.setHealth(victim.getMaxHealth());
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().indirectMagic(caster, caster), amount);
		return before - victim.getHealth();
	}
}
