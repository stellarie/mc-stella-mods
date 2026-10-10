package com.stella.rpg;

import com.stella.rpg.roll.MaterialProfiles;
import com.stella.rpg.roll.WeaponRolls;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Rare rolled drops from mined ore, plain drops that stack, and chest loot that carries nothing. */
public final class MiningRollGameTest {
	/** The vanilla block loot tables of the covered ores, with the block that owns each. */
	private static final Map<String, Block> ORES = ores();
	private static final Map<String, String> TIER_TABLE = Map.of(
		"copper", "copper_ore", "iron", "iron_ore", "gold", "gold_ore", "diamond", "diamond_ore", "debris", "ancient_debris"
	);
	private static final Map<String, Item> TIER_DROP = Map.of(
		"copper", Items.RAW_COPPER, "iron", Items.RAW_IRON, "gold", Items.RAW_GOLD, "diamond", Items.DIAMOND, "debris", Items.ANCIENT_DEBRIS
	);

	private static Map<String, Block> ores() {
		Map<String, Block> map = new LinkedHashMap<>();
		map.put("copper_ore", Blocks.COPPER_ORE);
		map.put("iron_ore", Blocks.IRON_ORE);
		map.put("gold_ore", Blocks.GOLD_ORE);
		map.put("diamond_ore", Blocks.DIAMOND_ORE);
		map.put("ancient_debris", Blocks.ANCIENT_DEBRIS);
		map.put("nether_gold_ore", Blocks.NETHER_GOLD_ORE);
		return map;
	}

	private static LootTable table(final GameTestHelper helper, final String path) {
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(path));
		return helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
	}

	/** One block break: the real block loot table, the block state, a depth, a tool, and an optional player. */
	private static List<ItemStack> mine(
		final GameTestHelper helper, final String ore, final Block block, final double depth, final ItemStack tool,
		final ServerPlayer player, final long seed
	) {
		double minY = helper.getLevel().getMinY();
		double y = 64.0 - depth * (64.0 - minY);
		LootParams.Builder builder = new LootParams.Builder(helper.getLevel())
			.withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
			.withParameter(LootContextParams.ORIGIN, new Vec3(0.5, y, 0.5))
			.withParameter(LootContextParams.TOOL, tool);
		if (player != null) {
			builder.withParameter(LootContextParams.THIS_ENTITY, player);
		}
		// Consecutive seeds give correlated first draws in the legacy random, so spread them.
		long spread = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
		return table(helper, "blocks/" + ore).getRandomItems(builder.create(LootContextParamSets.BLOCK), spread);
	}

	private static ItemStack tool(final GameTestHelper helper) {
		return new ItemStack(Items.IRON_PICKAXE);
	}

	/** Every item carries an empty enchantment component, so presence says nothing. */
	private static boolean enchanted(final ItemStack stack) {
		return !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
	}

	private static boolean plain(final ItemStack stack) {
		return !stack.has(WeaponRolls.STAT_ROLLS) && !stack.has(WeaponRolls.AFFIX_ROLLS) && !enchanted(stack);
	}

	private static boolean rolled(final ItemStack stack) {
		return stack.has(WeaponRolls.STAT_ROLLS) || stack.has(WeaponRolls.AFFIX_ROLLS);
	}

	/** Runs the body with a hand edited material_rolls object, then restores the default config. */
	private static void withMaterials(final String json, final Runnable body) {
		try {
			RpgConfigStore.set(RpgConfigStore.parse("{\"material_rolls\":" + json + "}", message -> { }));
			body.run();
		} finally {
			RpgConfigStore.set(null);
		}
	}

	private static final String ALL_ROLL = "{\"roll_chance\":1}";

	/** Counts the tier drops of many breaks, and how many of them rolled. */
	private static int[] rolledShare(final GameTestHelper helper, final String tier, final double depth, final int breaks, final long seed) {
		ServerPlayer player = RpgTestSupport.player(helper);
		String ore = TIER_TABLE.get(tier);
		int drops = 0;
		int rolled = 0;
		for (int i = 0; i < breaks; i++) {
			for (ItemStack stack : mine(helper, ore, ORES.get(ore), depth, new ItemStack(Items.IRON_PICKAXE), player, seed + i)) {
				if (stack.is(TIER_DROP.get(tier))) {
					drops++;
					rolled += rolled(stack) ? 1 : 0;
				}
			}
		}
		return new int[] {drops, rolled};
	}

	@GameTest
	public void rolledShareIsOnePercent(final GameTestHelper helper) {
		int breaks = 20000;
		for (String tier : TIER_TABLE.keySet()) {
			int[] share = rolledShare(helper, tier, 0.5, breaks, 100000L);
			double p = RpgConfig.DEFAULT_MATERIAL_ROLLS.rollChance();
			double rate = share[1] / (double) share[0];
			double bound = 3.0 * Math.sqrt(p * (1 - p) / share[0]);
			RpgTestSupport.result("rolledShareIsOnePercent", tier + " drops=" + share[0] + " rolled=" + share[1] + " rate=" + rate + " bound=" + bound);
			helper.assertTrue(Math.abs(rate - p) <= bound, tier + " rolled share " + rate + " expected " + p + " +- " + bound);
		}
		helper.succeed();
	}

	@GameTest
	public void depthDoesNotChangeTheRolledShare(final GameTestHelper helper) {
		int breaks = 20000;
		double p = RpgConfig.DEFAULT_MATERIAL_ROLLS.rollChance();
		for (double depth : new double[] {0.0, 1.0}) {
			int[] share = rolledShare(helper, "diamond", depth, breaks, 200000L);
			double rate = share[1] / (double) share[0];
			double bound = 3.0 * Math.sqrt(p * (1 - p) / share[0]);
			RpgTestSupport.result("depthDoesNotChangeTheRolledShare", "depth=" + depth + " rate=" + rate);
			helper.assertTrue(Math.abs(rate - p) <= bound, "depth " + depth + " rolled share " + rate + " expected " + p);
		}
		helper.succeed();
	}

	@GameTest
	public void plainDropsStackWithEachOther(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack first = null;
		int plainDrops = 0;
		int merged = 0;
		for (int i = 0; i < 400; i++) {
			for (ItemStack stack : mine(helper, "iron_ore", Blocks.IRON_ORE, 0.5, tool(helper), player, 300000L + i)) {
				if (!stack.is(Items.RAW_IRON) || rolled(stack)) {
					continue;
				}
				plainDrops++;
				if (first == null) {
					first = stack.copy();
				}
				helper.assertTrue(stack.getComponentsPatch().isEmpty(), "a plain drop carries " + stack.getComponentsPatch());
				if (ItemStack.isSameItemSameComponents(first, stack)) {
					merged++;
				}
			}
		}
		RpgTestSupport.result("plainDropsStackWithEachOther", "plain=" + plainDrops + " merged=" + merged);
		helper.assertTrue(plainDrops > 300 && merged == plainDrops, "plain drops " + plainDrops + " merged " + merged);
		helper.succeed();
	}

	@GameTest
	public void everyTierRolls(final GameTestHelper helper) {
		withMaterials(ALL_ROLL, () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			for (Map.Entry<String, String> entry : TIER_TABLE.entrySet()) {
				int rolled = 0;
				RpgConfig.MaterialTier tier = RpgConfigStore.current().materialRolls().tiers().get(entry.getKey());
				for (int i = 0; i < 100; i++) {
					for (ItemStack stack : mine(helper, entry.getValue(), ORES.get(entry.getValue()), 0.5, tool(helper), player, 7L + i)) {
						if (stack.is(TIER_DROP.get(entry.getKey()))) {
							helper.assertTrue(rolled(stack), entry.getKey() + " drop did not roll");
							Map<String, Integer> stats = stack.getOrDefault(WeaponRolls.STAT_ROLLS, Map.of());
							int keys = stats.size() + stack.getOrDefault(WeaponRolls.AFFIX_ROLLS, Map.of()).size();
							helper.assertTrue(keys >= 1 && keys <= tier.countWeights().size(), entry.getKey() + " keys " + keys);
							helper.assertTrue(stats.values().stream().allMatch(value -> value >= tier.min() && value <= tier.max()), entry.getKey() + " stats " + stats);
							rolled++;
						}
					}
				}
				helper.assertTrue(rolled == 100, entry.getKey() + " rolled " + rolled + " of 100");
			}
		});
		helper.succeed();
	}

	@GameTest
	public void fortuneOneRollPerBreak(final GameTestHelper helper) {
		withMaterials(ALL_ROLL, () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			Holder<Enchantment> fortune = BonusTestSupport.enchantment(helper, Enchantments.FORTUNE);
			ItemStack pick = BonusTestSupport.enchanted(new ItemStack(Items.DIAMOND_PICKAXE), fortune, 3);
			int bigStacks = 0;
			for (int i = 0; i < 200; i++) {
				List<ItemStack> drops = mine(helper, "iron_ore", Blocks.IRON_ORE, 0.5, pick, player, 500L + i);
				List<ItemStack> raw = drops.stream().filter(stack -> stack.is(Items.RAW_IRON)).toList();
				helper.assertTrue(raw.size() == 1 && rolled(raw.get(0)), "break " + i + " gave " + raw.size() + " raw iron stacks");
				bigStacks += raw.get(0).getCount() > 1 ? 1 : 0;
			}
			RpgTestSupport.result("fortuneOneRollPerBreak", "bigStacks=" + bigStacks);
			helper.assertTrue(bigStacks > 0, "Fortune never raised the count");
		});
		helper.succeed();
	}

	@GameTest
	public void silkTouchDropsNothingExtra(final GameTestHelper helper) {
		withMaterials(ALL_ROLL, () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			Holder<Enchantment> silk = BonusTestSupport.enchantment(helper, Enchantments.SILK_TOUCH);
			ItemStack pick = BonusTestSupport.enchanted(new ItemStack(Items.DIAMOND_PICKAXE), silk, 1);
			List<ItemStack> drops = mine(helper, "iron_ore", Blocks.IRON_ORE, 0.5, pick, player, 3L);
			helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.IRON_ORE) && plain(drops.get(0)), "silk drops " + drops);
		});
		helper.succeed();
	}

	@GameTest
	public void nonPlayerBreakRollsNothing(final GameTestHelper helper) {
		withMaterials(ALL_ROLL, () -> {
			List<ItemStack> drops = mine(helper, "iron_ore", Blocks.IRON_ORE, 0.5, tool(helper), null, 9L);
			helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.RAW_IRON)), "no raw iron dropped: " + drops);
			helper.assertTrue(drops.stream().allMatch(MiningRollGameTest::plain), "a block break without a player rolled: " + drops);
		});
		helper.succeed();
	}

	@GameTest
	public void otherBlockRollsNothing(final GameTestHelper helper) {
		withMaterials(ALL_ROLL, () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			// The iron ore table with a stone state: the drop is raw iron, the block is not an ore of the tag.
			List<ItemStack> drops = mine(helper, "iron_ore", Blocks.STONE, 0.5, tool(helper), player, 9L);
			helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.RAW_IRON)), "no raw iron dropped: " + drops);
			helper.assertTrue(drops.stream().allMatch(MiningRollGameTest::plain), "a block outside the ore tag rolled: " + drops);
		});
		helper.succeed();
	}

	@GameTest
	public void netherGoldNuggetRollsNothing(final GameTestHelper helper) {
		withMaterials(ALL_ROLL, () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			int nuggets = 0;
			for (int i = 0; i < 30; i++) {
				for (ItemStack stack : mine(helper, "nether_gold_ore", Blocks.NETHER_GOLD_ORE, 0.5, tool(helper), player, i)) {
					if (stack.is(Items.GOLD_NUGGET)) {
						nuggets++;
						helper.assertTrue(plain(stack), "a nugget rolled: " + stack.getComponentsPatch());
					}
				}
			}
			helper.assertTrue(nuggets > 0, "no nugget dropped");
		});
		helper.succeed();
	}

	@GameTest
	public void qualityRisesWithDepth(final GameTestHelper helper) {
		withMaterials(ALL_ROLL, () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			double[] mean = new double[2];
			for (int depth = 0; depth < 2; depth++) {
				double total = 0;
				int count = 0;
				for (int i = 0; i < 3000; i++) {
					for (ItemStack stack : mine(helper, "diamond_ore", Blocks.DIAMOND_ORE, depth, tool(helper), player, 70000L + i)) {
						if (stack.is(Items.DIAMOND)) {
							Map<String, Integer> stats = stack.getOrDefault(WeaponRolls.STAT_ROLLS, Map.of());
							total += stats.values().stream().mapToInt(Integer::intValue).sum();
							count++;
						}
					}
				}
				mean[depth] = total / count;
			}
			RpgTestSupport.result("qualityRisesWithDepth", "surface=" + mean[0] + " deep=" + mean[1]);
			helper.assertTrue(mean[1] > mean[0] * 1.1, "deep stat points " + mean[1] + " surface " + mean[0]);
		});
		helper.succeed();
	}

	/** Enchantment rate among rolled drops. The config rolls every drop so the sample is the rolled drops. */
	private static double enchantRate(final GameTestHelper helper, final String tier, final double depth, final int breaks, final long seed) {
		int[] counts = new int[2];
		withMaterials(ALL_ROLL, () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			String ore = TIER_TABLE.get(tier);
			for (int i = 0; i < breaks; i++) {
				for (ItemStack stack : mine(helper, ore, ORES.get(ore), depth, new ItemStack(Items.IRON_PICKAXE), player, seed + i)) {
					if (stack.is(TIER_DROP.get(tier))) {
						counts[0]++;
						counts[1] += enchanted(stack) ? 1 : 0;
					}
				}
			}
		});
		return counts[1] / (double) counts[0];
	}

	@GameTest
	public void enchantRateByMaterial(final GameTestHelper helper) {
		int breaks = 20000;
		Map<String, Double> rates = new LinkedHashMap<>();
		for (Map.Entry<String, RpgConfig.MaterialTier> entry : RpgConfig.DEFAULT_MATERIAL_ROLLS.tiers().entrySet()) {
			double expected = entry.getValue().enchantChance();
			double rate = enchantRate(helper, entry.getKey(), 0.0, breaks, 900000L);
			double bound = 4.5 * Math.sqrt(expected * (1 - expected) / breaks);
			rates.put(entry.getKey(), rate);
			RpgTestSupport.result("enchantRateByMaterial", entry.getKey() + " rate=" + rate + " expected=" + expected + " bound=" + bound);
			helper.assertTrue(Math.abs(rate - expected) <= bound, entry.getKey() + " rate " + rate + " expected " + expected + " +- " + bound);
		}
		helper.assertTrue(rates.get("diamond") > rates.get("gold") && rates.get("debris") > rates.get("diamond") && rates.get("gold") > rates.get("iron"),
			"diamond and debris must be the likeliest: " + rates);
		helper.succeed();
	}

	@GameTest
	public void depthRaisesEnchantRate(final GameTestHelper helper) {
		int breaks = 20000;
		RpgConfig.MaterialRolls config = RpgConfig.DEFAULT_MATERIAL_ROLLS;
		double expected = Math.min(1.0, config.tiers().get("diamond").enchantChance() * (1 + config.depthEnchantBonus()));
		double rate = enchantRate(helper, "diamond", 1.0, breaks, 1500000L);
		double bound = 4.5 * Math.sqrt(expected * (1 - expected) / breaks);
		RpgTestSupport.result("depthRaisesEnchantRate", "rate=" + rate + " expected=" + expected);
		helper.assertTrue(Math.abs(rate - expected) <= bound, "deep diamond rate " + rate + " expected " + expected + " +- " + bound);
		helper.succeed();
	}

	@GameTest
	public void enchantOnlyOnRolledDrops(final GameTestHelper helper) {
		// Half the drops roll and every rolled drop enchants. A plain drop must never carry an enchantment.
		withMaterials("{\"roll_chance\":0.5,\"diamond\":{\"enchant_chance\":1}}", () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			int rolledDrops = 0;
			int plainDrops = 0;
			for (int i = 0; i < 4000; i++) {
				for (ItemStack stack : mine(helper, "diamond_ore", Blocks.DIAMOND_ORE, 0.0, tool(helper), player, 4000000L + i)) {
					if (!stack.is(Items.DIAMOND)) {
						continue;
					}
					if (rolled(stack)) {
						rolledDrops++;
						helper.assertTrue(enchanted(stack), "a rolled drop with enchant chance 1 is not enchanted");
					} else {
						plainDrops++;
						helper.assertTrue(!enchanted(stack), "a plain drop carries an enchantment: " + stack.get(DataComponents.ENCHANTMENTS));
					}
				}
			}
			RpgTestSupport.result("enchantOnlyOnRolledDrops", "rolled=" + rolledDrops + " plain=" + plainDrops);
			helper.assertTrue(rolledDrops > 1500 && plainDrops > 1500, "rolled " + rolledDrops + " plain " + plainDrops);
		});
		helper.succeed();
	}

	@GameTest
	public void enchantedDropsAreValid(final GameTestHelper helper) {
		withMaterials("{\"roll_chance\":1,\"diamond\":{\"enchant_chance\":1}}", () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			int two = 0;
			int drops = 0;
			for (int i = 0; i < 2000; i++) {
				for (ItemStack stack : mine(helper, "diamond_ore", Blocks.DIAMOND_ORE, 0.5, tool(helper), player, 3000000L + i)) {
					if (!stack.is(Items.DIAMOND)) {
						continue;
					}
					drops++;
					ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
					helper.assertTrue(enchantments.size() >= 1 && enchantments.size() <= 2, "enchantment count " + enchantments.size());
					List<Holder<Enchantment>> held = new ArrayList<>(enchantments.keySet());
					for (Holder<Enchantment> enchantment : held) {
						helper.assertTrue(!enchantment.is(EnchantmentTags.CURSE), "a curse rolled: " + enchantment);
						helper.assertTrue(enchantment.is(EnchantmentTags.ON_RANDOM_LOOT), "outside the loot tag: " + enchantment);
						int level = enchantments.getLevel(enchantment);
						helper.assertTrue(level >= 1 && level <= enchantment.value().getMaxLevel(), "level " + level + " of " + enchantment);
					}
					if (held.size() == 2) {
						two++;
						helper.assertTrue(Enchantment.areCompatible(held.get(0), held.get(1)), "conflicting pair " + held);
					}
				}
			}
			double share = two / (double) drops;
			RpgTestSupport.result("enchantedDropsAreValid", "drops=" + drops + " second=" + share);
			helper.assertTrue(share > 0.08 && share < 0.22, "second enchantment share " + share + " expected 0.15");
		});
		helper.succeed();
	}

	private static LootParams chestParams(final GameTestHelper helper) {
		return new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
	}

	private static LootParams giftParams(final GameTestHelper helper) {
		return new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
			.withParameter(LootContextParams.THIS_ENTITY, RpgTestSupport.player(helper)).create(LootContextParamSets.GIFT);
	}

	private static LootTable inlineTable(final ContextKeySet params, final Item... items) {
		LootPool.Builder pool = LootPool.lootPool();
		for (Item item : items) {
			pool.add(LootItem.lootTableItem(item));
		}
		return LootTable.lootTable().setParamSet(params).withPool(pool).build();
	}

	@GameTest
	public void chestMaterialsCarryNothing(final GameTestHelper helper) {
		LootTable table = inlineTable(LootContextParamSets.CHEST, Items.IRON_INGOT, Items.DIAMOND, Items.RAW_IRON, Items.NETHERITE_INGOT);
		int seen = 0;
		for (int i = 0; i < 100; i++) {
			for (ItemStack stack : table.getRandomItems(chestParams(helper), 20L + i)) {
				seen++;
				helper.assertTrue(plain(stack), "a chest material carries " + stack.getComponentsPatch());
			}
		}
		helper.assertTrue(seen > 0, "the chest table dropped nothing");
		helper.succeed();
	}

	@GameTest
	public void chestGearStaysEmpty(final GameTestHelper helper) {
		LootTable table = inlineTable(LootContextParamSets.CHEST, Items.IRON_SWORD, Items.DIAMOND_CHESTPLATE);
		ServerPlayer player = RpgTestSupport.player(helper);
		int gear = 0;
		for (int seed = 0; seed < 40; seed++) {
			for (ItemStack stack : table.getRandomItems(chestParams(helper), seed)) {
				gear++;
				// An empty map with no affix is the mark. A roll that picked only affixes also leaves an empty map.
				helper.assertTrue(stack.has(WeaponRolls.STAT_ROLLS) && stack.get(WeaponRolls.STAT_ROLLS).isEmpty() && !stack.has(WeaponRolls.AFFIX_ROLLS),
					"chest gear rolls " + stack.get(WeaponRolls.STAT_ROLLS) + " " + stack.get(WeaponRolls.AFFIX_ROLLS));
				player.getInventory().setItem(9, stack);
				WeaponRolls.rollPossessed(player);
				ItemStack kept = player.getInventory().getItem(9);
				helper.assertTrue(kept.get(WeaponRolls.STAT_ROLLS).isEmpty() && !kept.has(WeaponRolls.AFFIX_ROLLS), "the scan rolled chest gear: " + kept.get(WeaponRolls.STAT_ROLLS));
			}
		}
		RpgTestSupport.result("chestGearStaysEmpty", "gear=" + gear);
		helper.assertTrue(gear > 0, "no gear dropped");
		helper.succeed();
	}

	@GameTest
	public void realChestTablesCarryNothing(final GameTestHelper helper) {
		int gear = 0;
		int materials = 0;
		for (String path : List.of("chests/bastion_treasure", "chests/village/village_weaponsmith", "chests/simple_dungeon")) {
			LootTable table = table(helper, path);
			for (int i = 0; i < 150; i++) {
				ObjectArrayList<ItemStack> drops = table.getRandomItems(chestParams(helper), 77L + i);
				for (ItemStack stack : drops) {
					if (WeaponRolls.kindOf(stack) != null) {
						gear++;
						helper.assertTrue(stack.has(WeaponRolls.STAT_ROLLS) && stack.get(WeaponRolls.STAT_ROLLS).isEmpty(), path + " gear " + stack + " rolls " + stack.get(WeaponRolls.STAT_ROLLS));
					} else if (stack.is(Items.IRON_INGOT) || stack.is(Items.GOLD_INGOT) || stack.is(Items.DIAMOND) || stack.is(Items.NETHERITE_INGOT)) {
						materials++;
						helper.assertTrue(plain(stack), path + " material carries " + stack.getComponentsPatch());
					}
				}
			}
		}
		RpgTestSupport.result("realChestTablesCarryNothing", "gear=" + gear + " materials=" + materials);
		helper.assertTrue(gear > 0 && materials > 0, "the real chest tables gave gear=" + gear + " materials=" + materials);
		helper.succeed();
	}

	@GameTest
	public void nonChestGearStillRolls(final GameTestHelper helper) {
		RpgTestSupport.withAffixChance(0.0, () -> {
			LootTable table = inlineTable(LootContextParamSets.GIFT, Items.IRON_SWORD);
			for (ItemStack stack : table.getRandomItems(giftParams(helper), 5L)) {
				helper.assertTrue(stack.has(WeaponRolls.STAT_ROLLS) && !stack.get(WeaponRolls.STAT_ROLLS).isEmpty(), "gift gear did not roll: " + stack.get(WeaponRolls.STAT_ROLLS));
			}
		});
		helper.succeed();
	}

	@GameTest
	public void minedOreMakesASwordWithBonuses(final GameTestHelper helper) {
		withMaterials(ALL_ROLL, () -> {
			ServerPlayer player = RpgTestSupport.player(helper);
			List<ItemStack> ingots = new ArrayList<>();
			Map<String, Integer> expectedStats = new LinkedHashMap<>();
			Map<String, Integer> expectedAffixes = new LinkedHashMap<>();
			for (int i = 0; i < 2; i++) {
				ItemStack raw = ItemStack.EMPTY;
				// Scan seeds for an ore that carries a stat, so the sword has something to show.
				for (long seed = 11L + i * 1000L; (raw.isEmpty() || !raw.has(WeaponRolls.STAT_ROLLS)) && seed < 11L + i * 1000L + 500L; seed++) {
					raw = mine(helper, "iron_ore", Blocks.IRON_ORE, 0.5, tool(helper), player, seed).stream()
						.filter(stack -> stack.is(Items.RAW_IRON)).findFirst().orElseThrow();
				}
				helper.assertTrue(raw.has(WeaponRolls.STAT_ROLLS), "no mined raw iron carried a stat in 500 seeds");
				final ItemStack mined = raw;
				mined.getOrDefault(WeaponRolls.STAT_ROLLS, Map.<String, Integer>of()).forEach((key, value) -> expectedStats.merge(key, value, Integer::sum));
				mined.getOrDefault(WeaponRolls.AFFIX_ROLLS, Map.<String, Integer>of()).forEach((key, value) -> {
					if (Set.of("damage_flat", "damage_pct", "armor_pen", "durability_pct").contains(key)) {
						expectedAffixes.merge(key, value, Integer::sum);
					}
				});
				ItemStack ingot = BonusTestSupport.smelt(helper, raw.copyWithCount(1));
				helper.assertTrue(ingot.is(Items.IRON_INGOT) && ingot.getOrDefault(WeaponRolls.STAT_ROLLS, Map.of()).equals(raw.getOrDefault(WeaponRolls.STAT_ROLLS, Map.of())),
					"the smelted ingot lost the bonus: " + ingot.getComponentsPatch());
				ingots.add(ingot);
			}
			ItemStack sword = BonusTestSupport.craft(helper, 1, 3, ingots.get(0), ingots.get(1), new ItemStack(Items.STICK));
			RpgTestSupport.result("minedOreMakesASwordWithBonuses", "stats=" + BonusTestSupport.stats(sword) + " affixes=" + sword.get(WeaponRolls.AFFIX_ROLLS));
			helper.assertTrue(sword.is(Items.IRON_SWORD) && expectedStats.equals(BonusTestSupport.stats(sword)), "sword stats " + BonusTestSupport.stats(sword) + " expected " + expectedStats);
			helper.assertTrue(expectedAffixes.equals(sword.getOrDefault(WeaponRolls.AFFIX_ROLLS, Map.of())), "sword affixes " + sword.get(WeaponRolls.AFFIX_ROLLS) + " expected " + expectedAffixes);
		});
		helper.succeed();
	}
}
