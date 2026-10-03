package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.WeaponRolls;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Roll slots pick a stat or an affix, and each affix rolls inside its tier range. */
public final class AffixRollGameTest {
	private static final int ROLLS = 2000;
	private static final double SIGMAS = 4.0;
	/** Armor holds one slot, so it needs more rolls to show both ends of a wide range. */
	private static final int ARMOR_SAMPLES = 6000;
	private static final int WEAPON_SAMPLES = 2500;

	/** One tier row from the plan, in K units: damage, HP, reduction, and MATK. Percent in percent. */
	private record Row(
		String name, Item weapon, Item armor, double damageFlatMin, double damageFlatMax, double damagePctMin, double damagePctMax,
		double hpFlatMin, double hpFlatMax, double hpPctMin, double hpPctMax,
		double reductionFlatMin, double reductionFlatMax, double reductionPctMin, double reductionPctMax
	) {
	}

	private static final List<Row> ROWS = List.of(
		new Row("wood", Items.WOODEN_SWORD, Items.LEATHER_CHESTPLATE, 3, 8, 2, 4, 15, 30, 1, 2, 1, 3, 0.5, 1),
		new Row("stone", Items.STONE_SWORD, Items.CHAINMAIL_CHESTPLATE, 5, 12, 3, 5, 25, 50, 2, 3, 2, 4, 1, 1.5),
		new Row("copper", Items.COPPER_SWORD, null, 5, 12, 3, 5, 25, 50, 2, 3, 2, 4, 1, 1.5),
		new Row("gold", Items.GOLDEN_SWORD, Items.GOLDEN_CHESTPLATE, 6, 14, 3, 6, 30, 60, 2, 4, 2, 5, 1, 2),
		new Row("iron", Items.IRON_SWORD, Items.IRON_CHESTPLATE, 8, 18, 4, 7, 40, 80, 2, 4, 3, 6, 1, 2),
		new Row("ranged", Items.BOW, null, 8, 18, 4, 7, 40, 80, 2, 4, 3, 6, 1, 2),
		new Row("diamond", Items.DIAMOND_SWORD, Items.DIAMOND_CHESTPLATE, 12, 27, 5, 9, 60, 120, 3, 6, 4, 8, 1.5, 2.5),
		new Row("heavy", Items.MACE, null, 12, 27, 5, 9, 60, 120, 3, 6, 4, 8, 1.5, 2.5),
		new Row("netherite", Items.NETHERITE_SWORD, Items.NETHERITE_CHESTPLATE, 18, 40, 6, 12, 90, 180, 4, 8, 6, 12, 2, 3)
	);

	@GameTest
	public void affixShare(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20261001L);
		int armorAffix = 0;
		long weaponSlots = 0;
		long weaponAffixSlots = 0;
		for (int i = 0; i < ROLLS; i++) {
			ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
			helper.assertTrue(WeaponRolls.roll(chest, random), "armor roll " + i + " did not apply");
			Map<String, Integer> affixes = chest.get(WeaponRolls.AFFIX_ROLLS);
			armorAffix += affixes == null ? 0 : 1;
			helper.assertTrue(chest.get(WeaponRolls.STAT_ROLLS).size() + (affixes == null ? 0 : affixes.size()) == 1,
				"armor holds one slot: " + chest.get(WeaponRolls.STAT_ROLLS) + affixes);
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			WeaponRolls.roll(sword, random);
			Map<String, Integer> swordAffixes = sword.get(WeaponRolls.AFFIX_ROLLS);
			int affixCount = swordAffixes == null ? 0 : swordAffixes.size();
			weaponAffixSlots += affixCount;
			weaponSlots += affixCount + sword.get(WeaponRolls.STAT_ROLLS).size();
		}
		double chance = RpgConfig.DEFAULT_AFFIX_CHANCE;
		double armorSigma = Math.sqrt(ROLLS * chance * (1.0 - chance));
		double slotSigma = Math.sqrt(weaponSlots * chance * (1.0 - chance));
		RpgTestSupport.result("affixShare", "armor=" + armorAffix + "/" + ROLLS + " weaponSlots=" + weaponAffixSlots + "/" + weaponSlots);
		helper.assertTrue(chance == 0.30, "the default affix chance is " + chance);
		helper.assertTrue(Math.abs(armorAffix - ROLLS * chance) <= SIGMAS * armorSigma, "armor affix count " + armorAffix);
		helper.assertTrue(Math.abs(weaponAffixSlots - weaponSlots * chance) <= SIGMAS * slotSigma,
			"weapon affix slots " + weaponAffixSlots + " of " + weaponSlots);
		helper.succeed();
	}

	@GameTest
	public void countWeightsKept(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(9L);
		int[] counts = new int[4];
		for (int i = 0; i < ROLLS; i++) {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			WeaponRolls.roll(sword, random);
			Map<String, Integer> affixes = sword.get(WeaponRolls.AFFIX_ROLLS);
			counts[sword.get(WeaponRolls.STAT_ROLLS).size() + (affixes == null ? 0 : affixes.size())]++;
		}
		RpgTestSupport.result("countWeightsKept", "counts=" + counts[1] + "/" + counts[2] + "/" + counts[3]);
		helper.assertTrue(counts[0] == 0, "an empty roll appeared");
		helper.assertTrue(near(counts[1], 0.50), "count 1 share " + counts[1]);
		helper.assertTrue(near(counts[2], 0.35), "count 2 share " + counts[2]);
		helper.assertTrue(near(counts[3], 0.15), "count 3 share " + counts[3]);
		helper.succeed();
	}

	@GameTest
	public void rangesPerTier(final GameTestHelper helper) {
		RpgTestSupport.withAffixChance(1.0, () -> {
			RandomSource random = RandomSource.create(4242L);
			StringBuilder log = new StringBuilder();
			for (Row row : ROWS) {
				Map<Affix, double[]> weapon = observe(helper, row.weapon(), random);
				expect(helper, row.name() + " weapon", weapon, Affix.DAMAGE_FLAT, row.damageFlatMin(), row.damageFlatMax());
				expect(helper, row.name() + " weapon", weapon, Affix.DAMAGE_PCT, row.damagePctMin(), row.damagePctMax());
				expect(helper, row.name() + " weapon", weapon, Affix.ARMOR_PEN, row.damagePctMin(), row.damagePctMax());
				helper.assertTrue(weapon.size() == 3, row.name() + " weapon affix keys " + weapon.keySet());
				log.append(row.name()).append(" dmg=").append(weapon.get(Affix.DAMAGE_FLAT)[0]).append("..")
					.append(weapon.get(Affix.DAMAGE_FLAT)[1]).append(' ');
				if (row.armor() != null) {
					Map<Affix, double[]> armor = observe(helper, row.armor(), random);
					expect(helper, row.name() + " armor", armor, Affix.HP_FLAT, row.hpFlatMin(), row.hpFlatMax());
					expect(helper, row.name() + " armor", armor, Affix.HP_PCT, row.hpPctMin(), row.hpPctMax());
					expect(helper, row.name() + " armor", armor, Affix.REDUCTION_FLAT, row.reductionFlatMin(), row.reductionFlatMax());
					expect(helper, row.name() + " armor", armor, Affix.REDUCTION_PCT, row.reductionPctMin(), row.reductionPctMax());
					expect(helper, row.name() + " armor", armor, Affix.MAGIC_RES_PCT, row.hpPctMin(), row.hpPctMax());
					helper.assertTrue(armor.size() == 5, row.name() + " armor affix keys " + armor.keySet());
				}
			}
			Map<Affix, double[]> book = observe(helper, Items.BOOK, random);
			expect(helper, "spellbook", book, Affix.MATK_FLAT, 12, 27);
			expect(helper, "spellbook", book, Affix.MATK_PCT, 5, 9);
			expect(helper, "spellbook", book, Affix.MAGIC_PEN, 5, 9);
			helper.assertTrue(book.size() == 3 && book.containsKey(Affix.MATK_FLAT), "spellbook affix keys " + book.keySet());
			ItemStack bookStack = new ItemStack(Items.BOOK);
			WeaponRolls.roll(bookStack, random);
			helper.assertTrue(Set.of("int").containsAll(bookStack.get(WeaponRolls.STAT_ROLLS).keySet()), "a spellbook rolled a stat other than INT");
			RpgTestSupport.result("rangesPerTier", log.toString().trim() + " book=" + book.keySet());
		});
		helper.succeed();
	}

	@GameTest
	public void oldItemsKeepRolls(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(WeaponRolls.STAT_ROLLS, Map.of("str", 4));
		RandomSource random = RandomSource.create(1L);
		boolean rolled = WeaponRolls.roll(sword, random);
		RpgTestSupport.result("oldItemsKeepRolls", "rolled=" + rolled + " stats=" + sword.get(WeaponRolls.STAT_ROLLS)
			+ " affixes=" + sword.get(WeaponRolls.AFFIX_ROLLS));
		helper.assertTrue(!rolled, "an item with a roll rolled again");
		helper.assertTrue(sword.get(WeaponRolls.STAT_ROLLS).equals(Map.of("str", 4)), "the old roll changed");
		helper.assertTrue(!sword.has(WeaponRolls.AFFIX_ROLLS), "an old item gained an affix");
		helper.succeed();
	}

	@GameTest
	public void configParsesAffixes(final GameTestHelper helper) {
		List<String> errors = new java.util.ArrayList<>();
		RpgConfig config = RpgConfigStore.parse("{\"weapon_rolls\":{\"affix_chance\":2,\"iron\":{\"affixes\":"
			+ "{\"damage_flat\":[1,2],\"hp_pct\":[3,1],\"reduction_pct\":[0.5,1.5]}}}}", errors::add);
		RpgConfig.RollTier iron = config.weaponRolls().tiers().get("iron");
		Map<Affix, RpgConfig.AffixRange> defaults = RpgConfig.DEFAULT_WEAPON_ROLLS.tiers().get("iron").affixes();
		RpgTestSupport.result("configParsesAffixes", "errors=" + errors + " damage=" + iron.affixes().get(Affix.DAMAGE_FLAT));
		helper.assertTrue(errors.size() == 2, "errors=" + errors);
		helper.assertTrue(config.weaponRolls().affixChance() == RpgConfig.DEFAULT_AFFIX_CHANCE, "a bad chance was kept");
		helper.assertTrue(iron.affixes().get(Affix.DAMAGE_FLAT).equals(new RpgConfig.AffixRange(1, 2)), "damage_flat override ignored");
		helper.assertTrue(iron.affixes().get(Affix.HP_PCT).equals(defaults.get(Affix.HP_PCT)), "a bad range replaced the default");
		helper.assertTrue(iron.affixes().get(Affix.REDUCTION_PCT).equals(new RpgConfig.AffixRange(0.5, 1.5)), "reduction_pct override ignored");
		helper.assertTrue(new EnumMap<>(iron.affixes()).size() == Affix.values().length, "an affix range is missing");
		helper.succeed();
	}

	private static boolean near(final int count, final double share) {
		double sigma = Math.sqrt(ROLLS * share * (1.0 - share));
		return Math.abs(count - ROLLS * share) <= SIGMAS * sigma;
	}

	/** The lowest and highest value each affix showed over many rolls, as shown on a tooltip. */
	private static Map<Affix, double[]> observe(final GameTestHelper helper, final Item item, final RandomSource random) {
		Map<Affix, double[]> seen = new EnumMap<>(Affix.class);
		int samples = WeaponRolls.kindOf(new ItemStack(item)) == Affix.Kind.ARMOR ? ARMOR_SAMPLES : WEAPON_SAMPLES;
		for (int i = 0; i < samples; i++) {
			ItemStack stack = new ItemStack(item);
			helper.assertTrue(WeaponRolls.roll(stack, random), item + " did not roll");
			Map<String, Integer> affixes = stack.get(WeaponRolls.AFFIX_ROLLS);
			helper.assertTrue(affixes != null, item + " rolled no affix at chance 1");
			affixes.forEach((key, stored) -> {
				Affix affix = Affix.byKey(key).orElse(null);
				helper.assertTrue(affix != null, "unknown affix key " + key);
				double shown = affix.shown(stored);
				double[] range = seen.computeIfAbsent(affix, unused -> new double[] {Double.MAX_VALUE, -Double.MAX_VALUE});
				range[0] = Math.min(range[0], shown);
				range[1] = Math.max(range[1], shown);
			});
		}
		return seen;
	}

	private static void expect(
		final GameTestHelper helper, final String name, final Map<Affix, double[]> seen, final Affix affix,
		final double min, final double max
	) {
		double[] range = seen.get(affix);
		helper.assertTrue(range != null, name + " never rolled " + affix);
		helper.assertTrue(Math.abs(range[0] - min) < 1e-9 && Math.abs(range[1] - max) < 1e-9,
			name + " " + affix + " observed " + range[0] + ".." + range[1] + " expected " + min + ".." + max);
	}
}
