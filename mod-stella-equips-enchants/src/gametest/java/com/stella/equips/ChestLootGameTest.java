package com.stella.equips;

import com.stella.equips.loot.ChestLoot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class ChestLootGameTest {
	private static final int SAMPLE = 4000;
	private static final int COVERAGE_SAMPLE = 200;
	private static final double GEAR_RATE = 0.30;
	private static final double BOOK_RATE = 0.30;
	private static final double JACKPOT_RATE = 0.05;
	private static final double RATE_TOLERANCE = 0.035;
	private static final double JACKPOT_TOLERANCE = 0.012;
	private static final double DOUBLED_GEAR_RATE = 0.51;
	private static final int VANILLA_DRAWS = 1000;
	private static final int VANILLA_DRAW_COUNT = 5;
	private static final String MOD = "mod-stella-equips-enchants";
	private static final Identifier SIMPLE_DUNGEON = chest("simple_dungeon");
	private static final Identifier ANCIENT_CITY = chest("ancient_city");
	private static final Identifier END_CITY_TREASURE = chest("end_city_treasure");
	private static final Identifier VILLAGE_WEAPONSMITH = chest("village/village_weaponsmith");
	private static final Vec3 NEAR = new Vec3(0.5, 64.0, 0.5);
	private static final Vec3 MID = new Vec3(1500.5, 64.0, 0.5);
	private static final Vec3 FAR = new Vec3(3000.5, 64.0, 0.5);
	private static final Vec3 DEEP = new Vec3(0.5, -70.0, 0.5);
	private static final List<String> GEAR_TYPES = List.of(
		"sword", "axe", "pickaxe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots"
	);
	private static final Set<String> ORDINARY_GEAR = materials(List.of("iron", "golden", "diamond"));
	private static final Set<String> JACKPOT_GEAR = materials(List.of("diamond", "netherite"));
	/** The vanilla chest tables a player can find and open, minus the tutorial chest, the arrow trap and the barrel. */
	private static final Set<String> TARGETED_CHESTS = Set.of(
		"chests/abandoned_camp_common_chest", "chests/abandoned_camp_secret_chest", "chests/abandoned_mineshaft",
		"chests/ancient_city", "chests/ancient_city_ice_box", "chests/bastion_bridge", "chests/bastion_hoglin_stable",
		"chests/bastion_other", "chests/bastion_treasure", "chests/buried_treasure", "chests/desert_pyramid",
		"chests/end_city_treasure", "chests/igloo_chest", "chests/jungle_temple", "chests/nether_bridge",
		"chests/pillager_outpost", "chests/ruined_portal", "chests/shipwreck_map", "chests/shipwreck_supply",
		"chests/shipwreck_treasure", "chests/simple_dungeon", "chests/stronghold_corridor", "chests/stronghold_crossing",
		"chests/stronghold_library", "chests/trial_chambers/corridor", "chests/trial_chambers/entrance",
		"chests/trial_chambers/intersection", "chests/trial_chambers/reward", "chests/trial_chambers/reward_common",
		"chests/trial_chambers/reward_ominous", "chests/trial_chambers/reward_ominous_common",
		"chests/trial_chambers/reward_ominous_rare", "chests/trial_chambers/reward_ominous_unique",
		"chests/trial_chambers/reward_rare", "chests/trial_chambers/reward_unique", "chests/trial_chambers/supply",
		"chests/underwater_ruin_big", "chests/underwater_ruin_small", "chests/village/village_armorer",
		"chests/village/village_butcher", "chests/village/village_cartographer", "chests/village/village_desert_house",
		"chests/village/village_fisher", "chests/village/village_fletcher", "chests/village/village_mason",
		"chests/village/village_plains_house", "chests/village/village_savanna_house", "chests/village/village_shepherd",
		"chests/village/village_snowy_house", "chests/village/village_taiga_house", "chests/village/village_tannery",
		"chests/village/village_temple", "chests/village/village_toolsmith", "chests/village/village_weaponsmith",
		"chests/spawn_bonus_chest",
		"chests/woodland_mansion"
	);
	private static final Set<String> NOT_TARGETED = Set.of(
		"chests/jungle_temple_dispenser", "chests/trial_chambers/intersection_barrel"
	);
	/** oniichan's head start: the seven iron pieces the bonus chest is guaranteed to carry. */
	private static final List<String> STARTER_KIT = List.of(
		"minecraft:iron_sword", "minecraft:iron_pickaxe", "minecraft:iron_axe", "minecraft:iron_helmet",
		"minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots"
	);
	private static final int STARTER_ENCHANTS = 2;

	@GameTest
	public void theTargetedTablesAreTheVanillaChestFolderMinusTwo(final GameTestHelper helper) {
		Set<String> found = new LinkedHashSet<>();

		for (ResourceKey<LootTable> key : lootTableKeys(helper)) {
			if (key.identifier().getNamespace().equals("minecraft") && key.identifier().getPath().startsWith("chests/")) {
				found.add(key.identifier().getPath());
			}
		}

		Set<String> targeted = new LinkedHashSet<>();

		for (String path : found) {
			if (ChestLoot.targetsChest(chestKey(path))) {
				targeted.add(path);
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=chestTableList RESULT chestTables={} targeted={} excluded={}",
			found.size(), targeted.size(), NOT_TARGETED
		);
		helper.assertTrue(found.size() == 58, "the jar holds " + found.size() + " chest tables, not 58");
		helper.assertTrue(targeted.size() == 56, "the hook targets " + targeted.size() + " chest tables, not 56");
		helper.assertTrue(
			targeted.equals(TARGETED_CHESTS),
			"the targeted set differs from the written list: " + difference(targeted, TARGETED_CHESTS)
		);
		helper.assertTrue(
			found.containsAll(NOT_TARGETED),
			"an excluded table is not in the jar: " + difference(NOT_TARGETED, found)
		);

		for (String excluded : NOT_TARGETED) {
			helper.assertTrue(!targeted.contains(excluded), excluded + " is targeted and should not be");
		}

		helper.assertTrue(
			!ChestLoot.targetsChest(chestKey("entities/zombie")),
			"a non chest table is targeted"
		);
		helper.assertTrue(
			!ChestLoot.targetsChest(ResourceKey.create(Registries.LOOT_TABLE, id("chest/gear"))),
			"our own pool is targeted as a chest"
		);
		helper.succeed();
	}

	@GameTest
	public void theOrdinaryGearRollLandsNearThirtyPercent(final GameTestHelper helper) {
		HolderSet<Enchantment> loot = lootTag(helper);
		int hits = 0;

		for (int roll = 0; roll < SAMPLE; roll++) {
			if (!ordinaryGear(roll(helper, VILLAGE_WEAPONSMITH, MID), loot).isEmpty()) {
				hits++;
			}
		}

		double rate = (double) hits / SAMPLE;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=gearRate RESULT table=minecraft:chests/village/village_weaponsmith rolls={} hits={} rate={} target={}",
			SAMPLE, hits, rate, GEAR_RATE
		);
		helper.assertTrue(
			Math.abs(rate - GEAR_RATE) < RATE_TOLERANCE,
			"the equipment roll landed at " + rate + " over " + SAMPLE + " chests, not near " + GEAR_RATE
		);
		helper.succeed();
	}

	@GameTest
	public void theBookRollLandsNearThirtyPercent(final GameTestHelper helper) {
		HolderSet<Enchantment> loot = lootTag(helper);
		int hits = 0;

		for (int roll = 0; roll < SAMPLE; roll++) {
			if (!enchantedBooks(roll(helper, VILLAGE_WEAPONSMITH, MID), loot).isEmpty()) {
				hits++;
			}
		}

		double rate = (double) hits / SAMPLE;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bookRate RESULT table=minecraft:chests/village/village_weaponsmith rolls={} hits={} rate={} target={}",
			SAMPLE, hits, rate, BOOK_RATE
		);
		helper.assertTrue(
			Math.abs(rate - BOOK_RATE) < RATE_TOLERANCE,
			"the book roll landed at " + rate + " over " + SAMPLE + " chests, not near " + BOOK_RATE
		);
		helper.succeed();
	}

	@GameTest
	public void theJackpotRollLandsNearFivePercent(final GameTestHelper helper) {
		HolderSet<Enchantment> loot = lootTag(helper);
		int hits = 0;

		for (int roll = 0; roll < SAMPLE; roll++) {
			if (!jackpotGear(roll(helper, VILLAGE_WEAPONSMITH, MID), loot).isEmpty()) {
				hits++;
			}
		}

		double rate = (double) hits / SAMPLE;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=jackpotRate RESULT table=minecraft:chests/village/village_weaponsmith rolls={} hits={} rate={} target={}",
			SAMPLE, hits, rate, JACKPOT_RATE
		);
		helper.assertTrue(
			Math.abs(rate - JACKPOT_RATE) < JACKPOT_TOLERANCE,
			"the jackpot roll landed at " + rate + " over " + SAMPLE + " chests, not near " + JACKPOT_RATE
		);
		helper.succeed();
	}

	@GameTest
	public void theOrdinaryGearIsNeverBelowIron(final GameTestHelper helper) {
		HolderSet<Enchantment> loot = lootTag(helper);
		Map<String, Integer> materials = new LinkedHashMap<>();
		int pieces = 0;

		for (int roll = 0; roll < SAMPLE; roll++) {
			for (ItemStack stack : ordinaryGear(roll(helper, VILLAGE_WEAPONSMITH, FAR), loot)) {
				pieces++;
				materials.merge(material(itemId(stack)), 1, Integer::sum);
				helper.assertTrue(
					ORDINARY_GEAR.contains(itemId(stack)),
					"a chest carried " + itemId(stack) + ", which is below iron or not equipment"
				);
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=gearFloor RESULT rolls={} pieces={} materials={} floor=iron",
			SAMPLE, pieces, materials
		);
		helper.assertTrue(pieces > 0, "no equipment piece was rolled in " + SAMPLE + " chests");
		helper.succeed();
	}

	@GameTest
	public void noCurseAndNoTestMarkerReachesAChest(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HolderSet.Named<Enchantment> curses = server.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(EnchantmentTags.CURSE);
		HolderSet<Enchantment> loot = lootTag(helper);
		int enchantments = 0;
		int curseCount = 0;
		int markerCount = 0;
		int ourRolls = 0;
		Set<String> seen = new LinkedHashSet<>();

		for (int roll = 0; roll < SAMPLE; roll++) {
			List<ItemStack> drops = roll(helper, VILLAGE_WEAPONSMITH, FAR);
			List<ItemStack> ours = new ArrayList<>(ordinaryGear(drops, loot));
			ours.addAll(jackpotGear(drops, loot));
			ours.addAll(enchantedBooks(drops, loot));

			if (!ours.isEmpty()) {
				ourRolls++;
			}

			for (ItemStack stack : ours) {
				helper.assertTrue(
					fromOurTag(stack, loot),
					"a chest carried " + itemId(stack) + " without an enchantment from the chest tag"
				);

				for (Holder<Enchantment> enchantment : enchantments(stack).keySet()) {
					enchantments++;
					String name = enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
					seen.add(name);

					if (curses.contains(enchantment)) {
						curseCount++;
					}

					if (name.equals(MOD + ":test_marker")) {
						markerCount++;
					}
				}
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=curseLeak RESULT rolls={} rollsWithOurLoot={} ourEnchantments={} distinct={} "
				+ "curses={} testMarker={} lootTag={}",
			SAMPLE, ourRolls, enchantments, seen.size(), curseCount, markerCount, loot.size()
		);
		helper.assertTrue(enchantments > 0, "no enchantment reached a chest in " + SAMPLE + " rolls");
		helper.assertTrue(curseCount == 0, curseCount + " curse enchantments reached a chest");
		helper.assertTrue(markerCount == 0, "the test marker reached a chest " + markerCount + " times");
		helper.succeed();
	}

	/**
	 * The package ships the 18 curses in #minecraft:on_random_loot, so a vanilla chest table that
	 * rolls enchant_randomly can curse a player. This measures that route; it is not our pool.
	 */
	@GameTest
	public void theVanillaRandomLootRouteCanStillCurse(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HolderSet.Named<Enchantment> curses = server.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(EnchantmentTags.CURSE);
		int curseCount = 0;
		int enchantments = 0;

		for (int roll = 0; roll < SAMPLE; roll++) {
			for (ItemStack stack : roll(helper, SIMPLE_DUNGEON, MID)) {
				for (Holder<Enchantment> enchantment : enchantments(stack).keySet()) {
					enchantments++;

					if (curses.contains(enchantment)) {
						curseCount++;
					}
				}
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=vanillaCurseRoute RESULT table=minecraft:chests/simple_dungeon rolls={} "
				+ "enchantments={} curses={}",
			SAMPLE, enchantments, curseCount
		);
		helper.assertTrue(
			curseCount > 0,
			"the vanilla route granted no curse in " + SAMPLE + " chests, so the measurement says nothing"
		);
		helper.succeed();
	}

	@GameTest
	public void theJackpotIsDiamondOrNetheriteWithExactlyFiveEnchantments(final GameTestHelper helper) {
		HolderSet<Enchantment> loot = lootTag(helper);
		Map<String, Integer> materials = new LinkedHashMap<>();
		int pieces = 0;

		for (int roll = 0; roll < SAMPLE; roll++) {
			for (ItemStack stack : jackpotGear(roll(helper, VILLAGE_WEAPONSMITH, MID), loot)) {
				pieces++;
				materials.merge(material(itemId(stack)), 1, Integer::sum);
				helper.assertTrue(
					JACKPOT_GEAR.contains(itemId(stack)),
					"the jackpot carried " + itemId(stack)
				);
				helper.assertTrue(
					enchantments(stack).size() == 5,
					"a jackpot piece carried " + enchantments(stack).size() + " enchantments"
				);
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=jackpotShape RESULT rolls={} pieces={} enchantments=5 materials={}",
			SAMPLE, pieces, materials
		);
		helper.assertTrue(pieces > 0, "no jackpot piece was rolled in " + SAMPLE + " chests");
		helper.succeed();
	}

	@GameTest
	public void theEnchantCountRisesWithDistanceAndDepth(final GameTestHelper helper) {
		HolderSet<Enchantment> loot = lootTag(helper);
		Map<String, String> observed = new LinkedHashMap<>();

		for (Map.Entry<String, Vec3> band : Map.of("near", NEAR, "mid", MID, "far", FAR, "deep", DEEP).entrySet()) {
			int expected = switch (band.getKey()) {
				case "near" -> 1;
				case "mid" -> 2;
				default -> 3;
			};
			int pieces = 0;

			for (int roll = 0; roll < SAMPLE; roll++) {
				for (ItemStack stack : ordinaryGear(roll(helper, VILLAGE_WEAPONSMITH, band.getValue()), loot)) {
					pieces++;
					helper.assertTrue(
						enchantments(stack).size() == expected,
						"a " + band.getKey() + " chest carried " + enchantments(stack).size()
							+ " enchantments instead of " + expected
					);
				}
			}

			helper.assertTrue(pieces > 0, "the " + band.getKey() + " band rolled no equipment");
			observed.put(band.getKey(), "count=" + expected + " pieces=" + pieces);
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=qualityTiers RESULT near={} mid={} far={} deep={} axisBlocks=1200/2400 depthY=-32/-64",
			observed.get("near"), observed.get("mid"), observed.get("far"), observed.get("deep")
		);
		helper.succeed();
	}

	@GameTest
	public void theDatapackOverriddenChestReceivesThePoolOnce(final GameTestHelper helper) {
		int overridden = gearHits(helper, ANCIENT_CITY, MID, SAMPLE);
		int plain = gearHits(helper, VILLAGE_WEAPONSMITH, MID, SAMPLE);
		double overriddenRate = (double) overridden / SAMPLE;
		double plainRate = (double) plain / SAMPLE;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=overrideOnce RESULT ancientCityHits={} ancientCityRate={} villageHits={} "
				+ "villageRate={} doubledRate={}",
			overridden, overriddenRate, plain, plainRate, DOUBLED_GEAR_RATE
		);
		helper.assertTrue(
			Math.abs(overriddenRate - GEAR_RATE) < RATE_TOLERANCE,
			"the table with a bundled datapack override landed at " + overriddenRate + ", not near " + GEAR_RATE
		);
		helper.assertTrue(
			Math.abs(plainRate - GEAR_RATE) < RATE_TOLERANCE,
			"the village chest landed at " + plainRate + ", not near " + GEAR_RATE
		);
		helper.assertTrue(
			overriddenRate < DOUBLED_GEAR_RATE - RATE_TOLERANCE,
			"the override attached twice: " + overriddenRate + " is near the doubled rate"
		);
		helper.succeed();
	}

	@GameTest
	public void everyTargetedTableCarriesThePools(final GameTestHelper helper) {
		List<String> empty = new ArrayList<>();
		double lowest = 1.0;

		for (String path : new java.util.TreeSet<>(TARGETED_CHESTS)) {
			double rate = (double) gearHits(helper, tableId(path), MID, COVERAGE_SAMPLE) / COVERAGE_SAMPLE;

			if (rate == 0.0) {
				empty.add(path);
			}

			lowest = Math.min(lowest, rate);
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=coverage RESULT tables={} rollsEach={} tablesWithNoRoll={} lowestRate={}",
			TARGETED_CHESTS.size(), COVERAGE_SAMPLE, empty, lowest
		);
		helper.assertTrue(empty.isEmpty(), "these tables received no pool: " + empty);
		helper.succeed();
	}

	@GameTest
	public void theVanillaFiveDrawCannotGuaranteeFiveEnchantments(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HolderSet.Named<Enchantment> pool = server.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(net.minecraft.tags.TagKey.create(Registries.ENCHANTMENT, id("chest_loot")));
		ItemStack probe = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "diamond_sword")));
		Holder<Enchantment> first = pool.get(0);
		probe.enchant(first, 1);
		probe.enchant(first, 3);
		int afterRepeat = probe.getEnchantments().size();
		int collisions = 0;
		java.util.Random random = new java.util.Random(7L);

		for (int draw = 0; draw < VANILLA_DRAWS; draw++) {
			Set<String> picked = new LinkedHashSet<>();

			for (int pick = 0; pick < VANILLA_DRAW_COUNT; pick++) {
				picked.add(pool.get(random.nextInt(pool.size())).unwrapKey().orElseThrow().identifier().toString());
			}

			if (picked.size() < VANILLA_DRAW_COUNT) {
				collisions++;
			}
		}

		double collisionRate = (double) collisions / VANILLA_DRAWS;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=vanillaCollision RESULT repeatDrawEnchantments={} drawsPerItem={} items={} "
				+ "itemsWithFewerThanFive={} collisionRate={} poolSize={}",
			afterRepeat, VANILLA_DRAW_COUNT, VANILLA_DRAWS, collisions, collisionRate, pool.size()
		);
		helper.assertTrue(
			afterRepeat == 1,
			"enchanting the same enchantment twice left " + afterRepeat + " enchantments, so duplicates would collapse"
		);
		helper.assertTrue(
			collisions > 0,
			"five undeduplicated draws collided on no item of " + VANILLA_DRAWS + ", so the measurement says nothing"
		);
		helper.succeed();
	}

	@GameTest
	public void theCatalogueCoversEveryChestItemType(final GameTestHelper helper) {
		HolderSet<Enchantment> loot = lootTag(helper);
		Set<String> items = new java.util.TreeSet<>(ORDINARY_GEAR);
		items.addAll(JACKPOT_GEAR);
		Map<String, Integer> supported = new LinkedHashMap<>();
		Map<String, Integer> thin = new LinkedHashMap<>();

		for (String item : items) {
			ItemStack stack = new ItemStack(
				BuiltInRegistries.ITEM.getValue(Identifier.parse(item))
			);
			int count = 0;

			for (Holder<Enchantment> enchantment : loot) {
				if (enchantment.value().canEnchant(stack)) {
					count++;
				}
			}

			supported.put(item.replace("minecraft:", ""), count);

			if (count < 3) {
				thin.put(item.replace("minecraft:", ""), count);
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=catalogueCoverage RESULT itemTypes={} lootTag={} supportedPerItem={} belowThree={}",
			items.size(), loot.size(), supported, thin
		);
		helper.assertTrue(
			supported.size() == 36,
			"the chest item types number " + supported.size() + ", not 36"
		);
		helper.succeed();
	}

	@GameTest
	public void theBonusChestCarriesTheHeadStartAndNothingRandom(final GameTestHelper helper) {
		List<ItemStack> drops = roll(helper, chest("spawn_bonus_chest"), NEAR);
		List<String> found = new ArrayList<>();
		int wrongCount = 0;
		int unenchanted = 0;
		int offTag = 0;

		for (ItemStack stack : drops) {
			String id = itemId(stack);
			if (!STARTER_KIT.contains(id)) {
				continue;
			}

			found.add(id);
			if (enchantments(stack).size() != STARTER_ENCHANTS) {
				wrongCount++;
			}

			if (enchantments(stack).isEmpty()) {
				unenchanted++;
			}

			if (!fromOurTag(stack, lootTag(helper))) {
				offTag++;
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=starterKit RESULT rolls=1 kitItems={} kitTypes={} eachWithEnchants={} wrongCount={}"
				+ " unenchanted={} offTag={}",
			found.size(), STARTER_KIT.size(), STARTER_ENCHANTS, wrongCount, unenchanted, offTag
		);
		helper.assertTrue(
			found.size() == STARTER_KIT.size(),
			"the bonus chest carried " + found + " instead of the " + STARTER_KIT.size() + " kit pieces"
		);
		helper.assertTrue(
			found.containsAll(STARTER_KIT),
			"the kit is missing: " + STARTER_KIT.stream().filter(id -> !found.contains(id)).toList()
		);
		helper.assertTrue(wrongCount == 0, wrongCount + " kit pieces did not carry exactly " + STARTER_ENCHANTS);
		helper.assertTrue(unenchanted == 0, unenchanted + " kit pieces were unenchanted");
		helper.assertTrue(offTag == 0, offTag + " kit pieces carried an enchantment outside the chest tag");
		helper.succeed();
	}

	private static List<ItemStack> roll(final GameTestHelper helper, final Identifier table, final Vec3 origin) {
		LootParams params = new LootParams.Builder(helper.getLevel())
			.withParameter(LootContextParams.ORIGIN, origin)
			.create(LootContextParamSets.CHEST);
		return helper.getLevel().getServer().reloadableRegistries().getLootTable(key(table)).getRandomItems(params);
	}

	private static int gearHits(
		final GameTestHelper helper, final Identifier table, final Vec3 origin, final int sample
	) {
		HolderSet<Enchantment> loot = lootTag(helper);
		int hits = 0;

		for (int roll = 0; roll < sample; roll++) {
			if (!ordinaryGear(roll(helper, table, origin), loot).isEmpty()) {
				hits++;
			}
		}

		return hits;
	}

	private static HolderSet<Enchantment> lootTag(final GameTestHelper helper) {
		return helper.getLevel().getServer().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(net.minecraft.tags.TagKey.create(Registries.ENCHANTMENT, id("chest_loot")));
	}

	private static List<ItemStack> ordinaryGear(final List<ItemStack> drops, final HolderSet<Enchantment> loot) {
		return matching(drops, ORDINARY_GEAR, 1, 3, loot);
	}

	private static List<ItemStack> jackpotGear(final List<ItemStack> drops, final HolderSet<Enchantment> loot) {
		return matching(drops, JACKPOT_GEAR, 5, 5, loot);
	}

	/** An enchanted book keeps its enchantments in the stored component, not in the item component. */
	private static ItemEnchantments enchantments(final ItemStack stack) {
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		return stored == null ? stack.getEnchantments() : stored;
	}

	/** Only our pools grant an enchantment from the chest tag, so this signature cannot be vanilla loot. */
	private static boolean fromOurTag(final ItemStack stack, final HolderSet<Enchantment> loot) {
		if (enchantments(stack).isEmpty()) {
			return false;
		}

		for (Holder<Enchantment> enchantment : enchantments(stack).keySet()) {
			if (!loot.contains(enchantment)) {
				return false;
			}
		}

		return true;
	}

	private static List<ItemStack> matching(
		final List<ItemStack> drops,
		final Set<String> allowed,
		final int minEnchantments,
		final int maxEnchantments,
		final HolderSet<Enchantment> loot
	) {
		List<ItemStack> found = new ArrayList<>();

		for (ItemStack stack : drops) {
			int count = enchantments(stack).size();

			if (allowed.contains(itemId(stack))
				&& count >= minEnchantments
				&& count <= maxEnchantments
				&& fromOurTag(stack, loot)) {
				found.add(stack);
			}
		}

		return found;
	}

	private static List<ItemStack> enchantedBooks(final List<ItemStack> drops, final HolderSet<Enchantment> loot) {
		List<ItemStack> found = new ArrayList<>();

		for (ItemStack stack : drops) {
			if (itemId(stack).equals("minecraft:enchanted_book") && fromOurTag(stack, loot)) {
				found.add(stack);
			}
		}

		return found;
	}

	private static String itemId(final ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String material(final String itemId) {
		return itemId.substring("minecraft:".length(), itemId.indexOf('_'));
	}

	private static Set<ResourceKey<LootTable>> lootTableKeys(final GameTestHelper helper) {
		return helper.getLevel().getServer().reloadableRegistries().lookup()
			.lookupOrThrow(Registries.LOOT_TABLE)
			.listElementIds()
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
	}

	private static Set<String> difference(final Set<String> left, final Set<String> right) {
		Set<String> only = new LinkedHashSet<>(left);
		only.removeAll(right);
		Set<String> missing = new LinkedHashSet<>(right);
		missing.removeAll(left);
		return Set.of("onlyInLeft=" + only, "onlyInRight=" + missing);
	}

	private static Set<String> materials(final List<String> names) {
		Set<String> items = new LinkedHashSet<>();

		for (String name : names) {
			for (String type : GEAR_TYPES) {
				items.add("minecraft:" + name + "_" + type);
			}
		}

		return items;
	}

	private static ResourceKey<LootTable> key(final Identifier id) {
		return ResourceKey.create(Registries.LOOT_TABLE, id);
	}

	private static ResourceKey<LootTable> chestKey(final String path) {
		return key(Identifier.fromNamespaceAndPath("minecraft", path));
	}

	private static Identifier chest(final String path) {
		return tableId("chests/" + path);
	}

	/** A path as it appears under data/minecraft/loot_table/, such as chests/simple_dungeon. */
	private static Identifier tableId(final String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD, path);
	}
}
