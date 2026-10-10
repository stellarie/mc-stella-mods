package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.MaterialProfiles;
import com.stella.rpg.roll.WeaponRolls;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The durability affix skips use damage, and only materials and grants carry it. */
public final class DurabilityAffixGameTest {
	private static final String KEY = "durability_pct";
	private static final int HITS = 20000;

	private static ItemStack sword(final int storedTenths) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		if (storedTenths > 0) {
			BonusTestSupport.withAffixes(sword, Map.of(KEY, storedTenths));
		}
		return sword;
	}

	/** Total damage taken by one-point hits. Each hit starts from a fresh stack so the sword never breaks. */
	private static long damageOf(final GameTestHelper helper, final ItemStack template, final int points, final int hits) {
		long total = 0;
		for (int i = 0; i < hits; i++) {
			ItemStack stack = template.copy();
			stack.hurtAndBreak(points, helper.getLevel(), null, item -> { });
			total += stack.getDamageValue();
		}
		return total;
	}

	@GameTest
	public void durabilityAffixSkipsDamage(final GameTestHelper helper) {
		// 100 percent more life means each point lands with chance 100 / 200.
		long damage = damageOf(helper, sword(1000), 1, HITS);
		double rate = damage / (double) HITS;
		double bound = 4.5 * Math.sqrt(0.25 / HITS);
		RpgTestSupport.result("durabilityAffixSkipsDamage", "rate=" + rate + " expected=0.5 bound=" + bound);
		helper.assertTrue(Math.abs(rate - 0.5) <= bound, "damage rate " + rate);
		helper.succeed();
	}

	@GameTest
	public void plainSwordTakesFullDamage(final GameTestHelper helper) {
		long damage = damageOf(helper, sword(0), 1, 500);
		helper.assertTrue(damage == 500, "a sword without the affix took " + damage + " of 500");
		helper.succeed();
	}

	@GameTest
	public void eachPointIsReducedAlone(final GameTestHelper helper) {
		long damage = damageOf(helper, sword(1000), 10, 4000);
		double mean = damage / 4000.0;
		double bound = 4.5 * Math.sqrt(10 * 0.25 / 4000);
		RpgTestSupport.result("eachPointIsReducedAlone", "mean=" + mean + " expected=5 bound=" + bound);
		helper.assertTrue(Math.abs(mean - 5.0) <= bound, "mean damage of a 10 point hit " + mean);
		helper.succeed();
	}

	@GameTest
	public void everyTierHasADurabilityRange(final GameTestHelper helper) {
		Affix affix = Affix.byKey(KEY).orElseThrow();
		for (Map.Entry<String, RpgConfig.RollTier> tier : RpgConfig.DEFAULT_WEAPON_ROLLS.tiers().entrySet()) {
			RpgConfig.AffixRange range = tier.getValue().affixes().get(affix);
			helper.assertTrue(range != null && range.min() > 0 && range.max() >= range.min(), tier.getKey() + " range " + range);
		}
		helper.assertTrue(affix.percent() && affix.rollsOn(Affix.Kind.WEAPON) && affix.rollsOn(Affix.Kind.ARMOR), "kinds or percent flag");
		helper.succeed();
	}

	@GameTest
	public void materialsCanCarryDurability(final GameTestHelper helper) {
		RpgConfig.MaterialRolls config = RpgConfig.DEFAULT_MATERIAL_ROLLS;
		Map<Affix, RpgConfig.AffixRange> gear = RpgConfig.DEFAULT_WEAPON_ROLLS.tiers().get("diamond").affixes();
		RpgConfig.AffixRange range = gear.get(Affix.byKey(KEY).orElseThrow());
		RandomSource random = RandomSource.create(77L);
		int carrying = 0;
		for (int i = 0; i < 4000; i++) {
			MaterialProfiles.Profile profile = MaterialProfiles.roll("diamond", config, gear, 0.0, random);
			Integer stored = profile.affixes().get(KEY);
			if (stored != null) {
				carrying++;
				long low = Math.max(1, Math.round(range.min() * Affix.TENTHS * config.affixScale()));
				long high = Math.max(low, Math.round(range.max() * Affix.TENTHS * config.affixScale()));
				helper.assertTrue(stored >= low && stored <= high, "stored " + stored + " outside " + low + ".." + high);
			}
		}
		RpgTestSupport.result("materialsCanCarryDurability", "carrying=" + carrying + " of 4000");
		helper.assertTrue(carrying > 0, "no diamond roll carries durability");
		helper.succeed();
	}

	@GameTest
	public void craftedGearKeepsDurability(final GameTestHelper helper) {
		ItemStack ingot = BonusTestSupport.withAffixes(new ItemStack(Items.DIAMOND), Map.of(KEY, 60));
		ItemStack made = BonusTestSupport.craft(helper, 1, 3, ingot, new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK));
		RpgTestSupport.result("craftedGearKeepsDurability", "affixes=" + made.get(WeaponRolls.AFFIX_ROLLS));
		helper.assertTrue(Map.of(KEY, 60).equals(made.get(WeaponRolls.AFFIX_ROLLS)), "sword affixes " + made.get(WeaponRolls.AFFIX_ROLLS));
		helper.succeed();
	}

	@GameTest
	public void randomGearRollNeverPicksDurability(final GameTestHelper helper) {
		RpgTestSupport.withAffixChance(1.0, () -> {
			RandomSource random = RandomSource.create(20261010L);
			List<ItemStack> rolled = new ArrayList<>();
			for (int i = 0; i < 1500; i++) {
				rolled.add(new ItemStack(i % 2 == 0 ? Items.IRON_SWORD : Items.IRON_CHESTPLATE));
				WeaponRolls.roll(rolled.get(i), random);
			}
			long affixed = rolled.stream().filter(stack -> stack.has(WeaponRolls.AFFIX_ROLLS)).count();
			boolean leaked = rolled.stream().anyMatch(stack -> stack.getOrDefault(WeaponRolls.AFFIX_ROLLS, Map.of()).containsKey(KEY));
			helper.assertTrue(affixed > 1000, "affix rolls were rare: " + affixed);
			helper.assertTrue(!leaked, "the random gear roll picked durability");
		});
		helper.succeed();
	}
}
