package com.stella.equips;

import com.stella.equips.loot.ContainerLoot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Oniichan's container table: chances, the 2% enchant chain, material bands, emeralds, and supported enchantments. */
public final class ContainerLootGameTest {
	private static final int CHANCE_SAMPLES = 200_000;
	private static final Vec3 SPOT = new Vec3(100_000.5, 0.0, 100_000.5);

	@GameTest
	public void chances(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(1L);
		int ordinary = 0;
		int food = 0;
		int potions = 0;
		int emeralds = 0;
		for (int i = 0; i < CHANCE_SAMPLES; i++) {
			for (ItemStack stack : ContainerLoot.roll(level, SPOT, null, random)) {
				if (stack.is(Items.EMERALD)) {
					emeralds++;
				} else if (stack.is(Items.POTION)) {
					potions++;
				} else if (stack.has(DataComponents.FOOD)) {
					food++;
				} else if (stack.isEnchanted() == false && isGear(stack)) {
					ordinary++;
				}
			}
		}
		String result = "ordinary=" + ordinary + " food=" + food + " potions=" + potions + " emeralds=" + emeralds;
		log("chances", result);
		helper.assertTrue(within(ordinary, ContainerLoot.ORDINARY_CHANCE) && within(food, ContainerLoot.FOOD_CHANCE)
			&& within(potions, ContainerLoot.POTION_CHANCE) && within(emeralds, ContainerLoot.EMERALD_CHANCE), "a roll rate is off: " + result);
		helper.succeed();
	}

	@GameTest
	public void onlySupportedEnchantments(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(2L);
		List<String> bad = new ArrayList<>();
		int enchantments = 0;
		for (int i = 0; i < 2000; i++) {
			for (ItemStack stack : List.of(ContainerLoot.enchanted(level, i % 100, random), ContainerLoot.jackpot(level, random))) {
				for (Holder<Enchantment> enchantment : enchantments(stack).keySet()) {
					enchantments++;
					if (!enchantment.value().canEnchant(stack) || enchantment.is(EnchantmentTags.CURSE)) {
						bad.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " " + enchantment.getRegisteredName());
					}
				}
			}
		}
		log("onlySupportedEnchantments", "enchantments=" + enchantments + " bad=" + bad.size());
		helper.assertTrue(enchantments > 0 && bad.isEmpty(), "unsupported or cursed enchantments: " + bad.stream().limit(10).toList());
		helper.succeed();
	}

	@GameTest
	public void enchantChain(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(3L);
		int samples = 20_000;
		int many = 0;
		int none = 0;
		for (int i = 0; i < samples; i++) {
			int count = enchantments(ContainerLoot.enchanted(level, 30, random)).size();
			many += count >= 2 ? 1 : 0;
			none += count == 0 ? 1 : 0;
		}
		double sigma = Math.sqrt(samples * 0.02 * 0.98);
		log("enchantChain", "many=" + many + " none=" + none + " of=" + samples);
		helper.assertTrue(none == 0, none + " enchanted pieces had no enchantment");
		helper.assertTrue(Math.abs(many - samples * 0.02) <= 3.0 * sigma, "the 2% chain gave " + many + " of " + samples);
		helper.succeed();
	}

	@GameTest
	public void materialBands(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(4L);
		int samples = 100_000;
		Map<ContainerLoot.Tier, Integer> at60 = new EnumMap<>(ContainerLoot.Tier.class);
		Map<ContainerLoot.Tier, Integer> at90 = new EnumMap<>(ContainerLoot.Tier.class);
		for (int i = 0; i < samples; i++) {
			at60.merge(ContainerLoot.tier(60, random), 1, Integer::sum);
			at90.merge(ContainerLoot.tier(90, random), 1, Integer::sum);
		}
		boolean low = ContainerLoot.tier(0, random) == ContainerLoot.Tier.STONE && ContainerLoot.tier(9, random) == ContainerLoot.Tier.STONE
			&& ContainerLoot.tier(10, random) == ContainerLoot.Tier.COPPER && ContainerLoot.tier(19, random) == ContainerLoot.Tier.COPPER;
		boolean iron = true;
		for (int i = 0; i < 1000; i++) {
			iron &= ContainerLoot.tier(20 + i % 30, random) == ContainerLoot.Tier.IRON;
		}
		int diamond60 = at60.getOrDefault(ContainerLoot.Tier.DIAMOND, 0);
		int netherite60 = at60.getOrDefault(ContainerLoot.Tier.NETHERITE, 0);
		int netherite90 = at90.getOrDefault(ContainerLoot.Tier.NETHERITE, 0);
		int diamond90 = at90.getOrDefault(ContainerLoot.Tier.DIAMOND, 0);
		log("materialBands", "low=" + low + " iron=" + iron + " at60=" + at60 + " at90=" + at90);
		helper.assertTrue(low && iron, "the low bands are wrong");
		helper.assertTrue(netherite60 == 0 && near(diamond60, samples, 0.05), "level 60 rolled " + at60);
		helper.assertTrue(near(netherite90, samples, 0.05) && near(diamond90, samples, 0.95 * 0.05), "level 90 rolled " + at90);
		helper.succeed();
	}

	@GameTest
	public void levelFallback(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer opener = helper.makeMockServerPlayerInLevel();
		opener.setExperienceLevels(30);
		int withOpener = ContainerLoot.level(level, SPOT, opener);
		int alone = ContainerLoot.level(level, SPOT, null);
		log("levelFallback", "withOpener=" + withOpener + " alone=" + alone);
		helper.assertTrue(withOpener == 28 && alone == 0, "fallback level was " + withOpener + " and " + alone);
		helper.succeed();
	}

	@GameTest
	public void emeralds(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(5L);
		int samples = 100_000;
		long total = 0;
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (int i = 0; i < samples; i++) {
			int count = ContainerLoot.emeraldCount(random);
			total += count;
			min = Math.min(min, count);
			max = Math.max(max, count);
		}
		double mean = (double) total / samples;
		log("emeralds", "mean=" + mean + " min=" + min + " max=" + max);
		helper.assertTrue(min >= 1 && max <= 64 && Math.abs(mean - 16.0) <= 0.3, "emeralds mean " + mean + " range " + min + "-" + max);
		helper.succeed();
	}

	@GameTest
	public void jackpot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(6L);
		List<String> bad = new ArrayList<>();
		int full = 0;
		int samples = 500;
		for (int i = 0; i < samples; i++) {
			ItemStack stack = ContainerLoot.jackpot(level, random);
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			int count = enchantments(stack).size();
			boolean tier = id.startsWith("diamond_") || id.startsWith("netherite_");
			full += count == ContainerLoot.JACKPOT_ENCHANTS ? 1 : 0;
			if (!tier || count < 1 || count > ContainerLoot.JACKPOT_ENCHANTS) {
				bad.add(id + " x" + count);
			}
		}
		log("jackpot", "full=" + full + " of=" + samples + " bad=" + bad);
		helper.assertTrue(bad.isEmpty(), "jackpot pieces off: " + bad.stream().limit(10).toList());
		helper.assertTrue(full * 2 > samples, "only " + full + " of " + samples + " jackpots had five enchantments");
		helper.succeed();
	}

	@GameTest
	public void containerTablesGetTheRolls(final GameTestHelper helper) {
		int samples = 4000;
		int dungeon = ordinaryHits(helper, "chests/simple_dungeon", samples);
		double rate = (double) dungeon / samples;
		log("containerTablesGetTheRolls", "dungeonRate=" + rate);
		helper.assertTrue(Math.abs(rate - ContainerLoot.ORDINARY_CHANCE) < 0.035, "the dungeon chest rate was " + rate);
		helper.succeed();
	}

	private static int ordinaryHits(final GameTestHelper helper, final String path, final int samples) {
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(path));
		LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
		int hits = 0;
		for (int i = 0; i < samples; i++) {
			LootParams params = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, SPOT)
				.create(LootContextParamSets.CHEST);
			boolean hit = table.getRandomItems(params).stream().anyMatch(stack -> {
				String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
				return id.startsWith("stone_") || id.startsWith("leather_");
			});
			hits += hit ? 1 : 0;
		}
		return hits;
	}

	private static boolean isGear(final ItemStack stack) {
		return stack.has(DataComponents.EQUIPPABLE) || stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON);
	}

	private static ItemEnchantments enchantments(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	private static boolean within(final int hits, final double chance) {
		return near(hits, CHANCE_SAMPLES, chance);
	}

	private static boolean near(final int hits, final int samples, final double chance) {
		double sigma = Math.sqrt(samples * chance * (1.0 - chance));
		return Math.abs(hits - samples * chance) <= 3.0 * sigma;
	}

	private static void log(final String test, final String result) {
		StellaEquips.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, result);
	}
}
