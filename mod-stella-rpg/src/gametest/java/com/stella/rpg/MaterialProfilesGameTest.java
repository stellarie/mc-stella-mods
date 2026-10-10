package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.MaterialProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The free roll of a rare mined drop: its count, its stat range, its typed affixes, and what depth does to them. */
public final class MaterialProfilesGameTest {
	/** The typed affixes a material can carry. */
	private static final Set<String> TYPED = Set.of("damage_flat", "damage_pct", "armor_pen", "hp_flat", "hp_pct", "reduction_flat", "reduction_pct", "durability_pct");
	private static final int ROLLS = 20000;

	private static RpgConfig.MaterialRolls parse(final String json, final List<String> errors) {
		return RpgConfigStore.parse(json, errors::add).materialRolls();
	}

	private static MaterialProfiles.Profile roll(final String tier, final double depth, final RandomSource random) {
		RpgConfig.MaterialRolls config = RpgConfig.DEFAULT_MATERIAL_ROLLS;
		return MaterialProfiles.roll(tier, config, RpgConfig.DEFAULT_WEAPON_ROLLS.tiers().get(MaterialProfiles.AFFIX_TIER.get(tier)).affixes(), depth, random);
	}

	private static int points(final MaterialProfiles.Profile profile) {
		return profile.stats().values().stream().mapToInt(Integer::intValue).sum();
	}

	@GameTest
	public void shapeAndRange(final GameTestHelper helper) {
		RpgConfig.MaterialRolls config = RpgConfig.DEFAULT_MATERIAL_ROLLS;
		RpgConfig.WeaponRolls gear = RpgConfig.DEFAULT_WEAPON_ROLLS;
		RandomSource random = RandomSource.create(20261010L);
		int checked = 0;
		for (Map.Entry<String, RpgConfig.MaterialTier> entry : config.tiers().entrySet()) {
			RpgConfig.MaterialTier tier = entry.getValue();
			Map<Affix, RpgConfig.AffixRange> ranges = gear.tiers().get(MaterialProfiles.AFFIX_TIER.get(entry.getKey())).affixes();
			for (int i = 0; i < 3000; i++) {
				MaterialProfiles.Profile profile = roll(entry.getKey(), (i % 3) / 2.0, random);
				helper.assertTrue(profile != null, entry.getKey() + " rolled nothing");
				int keys = profile.stats().size() + profile.affixes().size();
				helper.assertTrue(keys >= 1 && keys <= tier.countWeights().size(), entry.getKey() + " profile keys " + profile);
				for (Map.Entry<String, Integer> stat : profile.stats().entrySet()) {
					helper.assertTrue(Stat.byKey(stat.getKey()).isPresent(), "unknown stat " + stat);
					helper.assertTrue(stat.getValue() >= tier.min() && stat.getValue() <= tier.max(), entry.getKey() + " stat " + stat);
				}
				for (Map.Entry<String, Integer> affix : profile.affixes().entrySet()) {
					Affix key = Affix.byKey(affix.getKey()).orElseThrow();
					helper.assertTrue(TYPED.contains(affix.getKey()), entry.getKey() + " affix " + affix);
					double scale = key.percent() ? Affix.TENTHS : 1;
					double low = Math.max(1, Math.round(ranges.get(key).min() * scale * config.affixScale()));
					double high = Math.max(low, Math.round(ranges.get(key).max() * scale * config.affixScale()));
					helper.assertTrue(affix.getValue() >= low && affix.getValue() <= high, entry.getKey() + " affix " + affix + " outside " + low + ".." + high);
				}
				checked++;
			}
		}
		RpgTestSupport.result("shapeAndRange", "rolls=" + checked);
		helper.succeed();
	}

	@GameTest
	public void everyStatValueAppears(final GameTestHelper helper) {
		// Fixed profiles hid most of the range. A free roll reaches both ends of it.
		RandomSource random = RandomSource.create(5L);
		RpgConfig.MaterialTier tier = RpgConfig.DEFAULT_MATERIAL_ROLLS.tiers().get("diamond");
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		java.util.Set<List<Map<String, Integer>>> distinct = new java.util.HashSet<>();
		for (int i = 0; i < 4000; i++) {
			MaterialProfiles.Profile profile = roll("diamond", 0.0, random);
			seen.addAll(profile.stats().values());
			distinct.add(List.of(profile.stats(), profile.affixes()));
		}
		RpgTestSupport.result("everyStatValueAppears", "values=" + seen + " distinct=" + distinct.size());
		for (int value = tier.min(); value <= tier.max(); value++) {
			helper.assertTrue(seen.contains(value), "the value " + value + " never rolled");
		}
		helper.assertTrue(distinct.size() > 100, "only " + distinct.size() + " distinct rolls, which looks like a fixed list");
		helper.succeed();
	}

	@GameTest
	public void countFollowsWeights(final GameTestHelper helper) {
		RpgConfig.MaterialTier tier = RpgConfig.DEFAULT_MATERIAL_ROLLS.tiers().get("iron");
		RandomSource random = RandomSource.create(7L);
		int[] counts = new int[tier.countWeights().size()];
		for (int i = 0; i < ROLLS; i++) {
			MaterialProfiles.Profile profile = roll("iron", 0.0, random);
			counts[profile.stats().size() + profile.affixes().size() - 1]++;
		}
		int total = tier.countWeights().stream().mapToInt(Integer::intValue).sum();
		for (int i = 0; i < counts.length; i++) {
			double expected = tier.countWeights().get(i) / (double) total;
			double actual = counts[i] / (double) ROLLS;
			double bound = 4.5 * Math.sqrt(expected * (1 - expected) / ROLLS);
			RpgTestSupport.result("countFollowsWeights", "count=" + (i + 1) + " expected=" + expected + " actual=" + actual);
			helper.assertTrue(Math.abs(expected - actual) <= bound, "count " + (i + 1) + " share " + actual + " expected " + expected + " +- " + bound);
		}
		helper.succeed();
	}

	@GameTest
	public void depthRaisesQuality(final GameTestHelper helper) {
		double[] stat = new double[2];
		double[] size = new double[2];
		for (int depth = 0; depth < 2; depth++) {
			RandomSource random = RandomSource.create(11L);
			for (int i = 0; i < ROLLS; i++) {
				MaterialProfiles.Profile profile = roll("diamond", depth, random);
				stat[depth] += points(profile) / (double) Math.max(1, profile.stats().size());
				size[depth] += profile.stats().size() + profile.affixes().size();
			}
		}
		RpgTestSupport.result("depthRaisesQuality", "valueSurface=" + stat[0] / ROLLS + " valueDeep=" + stat[1] / ROLLS
			+ " keysSurface=" + size[0] / ROLLS + " keysDeep=" + size[1] / ROLLS);
		helper.assertTrue(stat[1] > stat[0] * 1.03, "a deep roll has no better values: " + stat[1] / ROLLS + " vs " + stat[0] / ROLLS);
		helper.assertTrue(size[1] > size[0] * 1.1, "a deep roll has no more bonuses: " + size[1] / ROLLS + " vs " + size[0] / ROLLS);
		helper.succeed();
	}

	@GameTest
	public void sameRandomGivesTheSameRoll(final GameTestHelper helper) {
		MaterialProfiles.Profile first = roll("gold", 0.4, RandomSource.create(99L));
		MaterialProfiles.Profile second = roll("gold", 0.4, RandomSource.create(99L));
		helper.assertTrue(first != null && first.equals(second), "the same random gave " + first + " and " + second);
		helper.succeed();
	}

	@GameTest
	public void configDefaultsAndGuards(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		helper.assertTrue(parse("{}", errors).equals(RpgConfig.DEFAULT_MATERIAL_ROLLS) && errors.isEmpty(), "empty config is not the default");
		helper.assertTrue(RpgConfig.DEFAULT_MATERIAL_ROLLS.rollChance() == 0.01, "roll_chance must default to 0.01");
		helper.assertTrue(!RpgConfig.DEFAULT_MATERIAL_ROLLS.craftRandomRoll(), "craft_random_roll must default to false");
		RpgConfig.MaterialRolls on = parse("{\"material_rolls\":{\"craft_random_roll\":true,\"roll_chance\":0.5}}", errors);
		helper.assertTrue(on.craftRandomRoll() && on.rollChance() == 0.5 && errors.isEmpty(), "hand edited values were not read");
		parse("{\"material_rolls\":{\"seed\":5,\"iron\":{\"profiles\":12,\"explicit\":[{\"str\":5}]}}}", errors);
		helper.assertTrue(errors.isEmpty(), "the retired keys must be skipped without an error: " + errors);
		RpgConfig.MaterialRolls bad = parse("{\"material_rolls\":{\"craft_random_roll\":\"yes\",\"roll_chance\":2,\"affix_share\":3,\"iron\":{\"min\":9,\"max\":2}}}", errors);
		RpgTestSupport.result("configDefaultsAndGuards", "errors=" + errors);
		helper.assertTrue(errors.size() == 4, "errors " + errors);
		helper.assertTrue(bad.equals(RpgConfig.DEFAULT_MATERIAL_ROLLS), "a bad value did not fall back to the default");
		helper.succeed();
	}

	@GameTest
	public void tierOfFollowsConfig(final GameTestHelper helper) {
		helper.assertTrue("iron".equals(MaterialProfiles.tierOf(new ItemStack(Items.RAW_IRON))), "raw iron");
		helper.assertTrue("copper".equals(MaterialProfiles.tierOf(new ItemStack(Items.RAW_COPPER))), "raw copper");
		helper.assertTrue("gold".equals(MaterialProfiles.tierOf(new ItemStack(Items.RAW_GOLD))), "raw gold");
		helper.assertTrue("diamond".equals(MaterialProfiles.tierOf(new ItemStack(Items.DIAMOND))), "diamond");
		helper.assertTrue("debris".equals(MaterialProfiles.tierOf(new ItemStack(Items.ANCIENT_DEBRIS))), "ancient debris");
		helper.assertTrue(MaterialProfiles.tierOf(new ItemStack(Items.IRON_INGOT)) == null, "an ingot is not mined");
		helper.assertTrue(MaterialProfiles.tierOf(new ItemStack(Items.IRON_NUGGET)) == null, "a nugget is not mined");
		helper.assertTrue(MaterialProfiles.tierOf(new ItemStack(Items.COAL)) == null, "coal");
		helper.succeed();
	}
}
