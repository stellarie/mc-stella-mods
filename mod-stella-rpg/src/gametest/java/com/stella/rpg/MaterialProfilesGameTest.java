package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.MaterialProfiles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The fixed bonus sets a mined material can roll. */
public final class MaterialProfilesGameTest {
	/** The typed affixes a material can carry. */
	private static final Set<String> TYPED = Set.of("damage_flat", "damage_pct", "armor_pen", "hp_flat", "hp_pct", "reduction_flat", "reduction_pct", "durability_pct");

	private static RpgConfig.MaterialRolls parse(final String json, final List<String> errors) {
		return RpgConfigStore.parse(json, errors::add).materialRolls();
	}

	@GameTest
	public void shapeAndRange(final GameTestHelper helper) {
		RpgConfig.MaterialRolls config = RpgConfig.DEFAULT_MATERIAL_ROLLS;
		RpgConfig.WeaponRolls gear = RpgConfig.DEFAULT_WEAPON_ROLLS;
		int checked = 0;
		for (Map.Entry<String, RpgConfig.MaterialTier> entry : config.tiers().entrySet()) {
			RpgConfig.MaterialTier tier = entry.getValue();
			List<MaterialProfiles.Profile> profiles = MaterialProfiles.profiles(entry.getKey());
			helper.assertTrue(profiles.size() == tier.profiles(), entry.getKey() + " has " + profiles.size() + " profiles");
			Map<Affix, RpgConfig.AffixRange> ranges = gear.tiers().get(MaterialProfiles.AFFIX_TIER.get(entry.getKey())).affixes();
			for (MaterialProfiles.Profile profile : profiles) {
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
					helper.assertTrue(affix.getValue() >= low && affix.getValue() <= high,
						entry.getKey() + " affix " + affix + " outside " + low + ".." + high);
				}
				checked++;
			}
		}
		RpgTestSupport.result("shapeAndRange", "profiles=" + checked);
		helper.assertTrue(checked > 0, "no profile was checked");
		helper.succeed();
	}

	@GameTest
	public void countFollowsWeights(final GameTestHelper helper) {
		RpgConfig.MaterialRolls config = parse("{\"material_rolls\":{\"iron\":{\"profiles\":256}}}", new ArrayList<>());
		RpgConfig.MaterialTier tier = config.tiers().get("iron");
		List<MaterialProfiles.Profile> profiles = MaterialProfiles.generate("iron", config,
			RpgConfig.DEFAULT_WEAPON_ROLLS.tiers().get(MaterialProfiles.AFFIX_TIER.get("iron")).affixes());
		int[] counts = new int[tier.countWeights().size()];
		for (MaterialProfiles.Profile profile : profiles) {
			counts[profile.stats().size() + profile.affixes().size() - 1]++;
		}
		int total = tier.countWeights().stream().mapToInt(Integer::intValue).sum();
		for (int i = 0; i < counts.length; i++) {
			double expected = tier.countWeights().get(i) / (double) total;
			double actual = counts[i] / (double) profiles.size();
			RpgTestSupport.result("countFollowsWeights", "count=" + (i + 1) + " expected=" + expected + " actual=" + actual);
			helper.assertTrue(profiles.size() == 256 && Math.abs(expected - actual) < 0.12, "count " + (i + 1) + " share " + actual + " expected " + expected);
		}
		helper.succeed();
	}

	@GameTest
	public void deterministic(final GameTestHelper helper) {
		RpgConfig.MaterialRolls config = RpgConfig.DEFAULT_MATERIAL_ROLLS;
		Map<Affix, RpgConfig.AffixRange> ranges = RpgConfig.DEFAULT_WEAPON_ROLLS.tiers().get("diamond").affixes();
		List<MaterialProfiles.Profile> first = MaterialProfiles.generate("diamond", config, ranges);
		List<MaterialProfiles.Profile> second = MaterialProfiles.generate("diamond", config, ranges);
		RpgConfig.MaterialRolls reseeded = parse("{\"material_rolls\":{\"seed\":7}}", new ArrayList<>());
		List<MaterialProfiles.Profile> other = MaterialProfiles.generate("diamond", reseeded, ranges);
		helper.assertTrue(!first.isEmpty() && first.equals(second), "the same seed gave different profiles");
		helper.assertTrue(!first.equals(other), "another seed gave the same profiles");
		helper.succeed();
	}

	@GameTest
	public void explicitEntriesWin(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig.MaterialRolls config = parse(
			"{\"material_rolls\":{\"iron\":{\"explicit\":[{\"str\":5},{\"dex\":2,\"damage_flat\":30},{\"nonsense\":1},{\"vit\":0}]}}}", errors);
		try {
			RpgConfigStore.set(RpgConfigStore.parse(
				"{\"material_rolls\":{\"iron\":{\"explicit\":[{\"str\":5},{\"dex\":2,\"damage_flat\":30},{\"nonsense\":1},{\"vit\":0}]}}}", message -> { }));
			List<MaterialProfiles.Profile> profiles = MaterialProfiles.profiles("iron");
			RpgTestSupport.result("explicitEntriesWin", "profiles=" + profiles + " errors=" + errors.size());
			helper.assertTrue(profiles.size() == 2, "profiles " + profiles);
			helper.assertTrue(profiles.get(0).stats().equals(Map.of("str", 5)) && profiles.get(0).affixes().isEmpty(), "first " + profiles.get(0));
			helper.assertTrue(profiles.get(1).stats().equals(Map.of("dex", 2)) && profiles.get(1).affixes().equals(Map.of("damage_flat", 30)), "second " + profiles.get(1));
			helper.assertTrue(errors.size() == 2, "bad entries logged " + errors.size());
			helper.assertTrue(config.tiers().get("iron").explicit().size() == 2, "parsed " + config.tiers().get("iron").explicit());
		} finally {
			RpgConfigStore.set(null);
		}
		helper.succeed();
	}

	@GameTest
	public void configDefaultsAndGuards(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		helper.assertTrue(parse("{}", errors).equals(RpgConfig.DEFAULT_MATERIAL_ROLLS) && errors.isEmpty(), "empty config is not the default");
		helper.assertTrue(!RpgConfig.DEFAULT_MATERIAL_ROLLS.craftRandomRoll(), "craft_random_roll must default to false");
		RpgConfig.MaterialRolls on = parse("{\"material_rolls\":{\"craft_random_roll\":true}}", errors);
		helper.assertTrue(on.craftRandomRoll() && errors.isEmpty(), "craft_random_roll true was not read");
		RpgConfig.MaterialRolls bad = parse("{\"material_rolls\":{\"craft_random_roll\":\"yes\",\"affix_share\":3,\"iron\":{\"min\":9,\"max\":2,\"profiles\":0}}}", errors);
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

	@GameTest
	public void depthSkewsWorth(final GameTestHelper helper) {
		List<MaterialProfiles.Profile> profiles = MaterialProfiles.profiles("diamond");
		RandomSource random = RandomSource.create(11L);
		double weight = RpgConfig.DEFAULT_MATERIAL_ROLLS.depthRankWeight();
		double shallow = 0;
		double deep = 0;
		Set<MaterialProfiles.Profile> seen = new HashSet<>();
		for (int i = 0; i < 4000; i++) {
			shallow += MaterialProfiles.pick(profiles, 0.0, weight, random).worth();
			MaterialProfiles.Profile picked = MaterialProfiles.pick(profiles, 1.0, weight, random);
			deep += picked.worth();
			seen.add(picked);
		}
		double mean = profiles.stream().mapToDouble(MaterialProfiles.Profile::worth).average().orElse(0);
		RpgTestSupport.result("depthSkewsWorth", "mean=" + mean + " shallow=" + shallow / 4000 + " deep=" + deep / 4000 + " seen=" + seen.size());
		helper.assertTrue(Math.abs(shallow / 4000 - mean) < mean * 0.08, "depth 0 should be uniform: " + shallow / 4000 + " vs " + mean);
		helper.assertTrue(deep / 4000 > shallow / 4000 * 1.1, "depth 1 mean " + deep / 4000 + " vs depth 0 " + shallow / 4000);
		helper.assertTrue(seen.size() > 1, "depth 1 picked one profile only");
		helper.succeed();
	}
}
