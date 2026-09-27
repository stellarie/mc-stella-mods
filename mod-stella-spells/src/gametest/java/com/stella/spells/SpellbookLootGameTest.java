package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpellbookLootGameTest {
	private static final int ROLLS = 100_000;
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final Set<Identifier> SPELLBOOK_IDS = SpellbookCatalog.DEFINITIONS.stream()
		.map(SpellDefinition::id)
		.collect(Collectors.toUnmodifiableSet());
	private static final Identifier DUNGEON_CHEST = Identifier.parse("minecraft:chests/simple_dungeon");

	@GameTest
	public void vanillaChestLootUsesTheRegisteredDropHook(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "vanillaChestLootUsesTheRegisteredDropHook");
		LootTable chest = helper.getLevel().getServer().reloadableRegistries()
			.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, DUNGEON_CHEST));
		Vec3 origin = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		LootParams params = new LootParams.Builder(helper.getLevel())
			.withParameter(LootContextParams.ORIGIN, origin)
			.withLuck(0.0f)
			.create(LootContextParamSets.CHEST);
		int bookCount = 0;
		for (long seed = 0; seed < 1_000 && bookCount == 0; seed++) {
			bookCount = chest.getRandomItems(params, RandomSource.create(seed)).stream()
				.filter(SpellbookLootGameTest::isSpellbook)
				.mapToInt(ItemStack::getCount)
				.sum();
		}
		helper.assertTrue(bookCount == 1, "vanilla chest generation did not add exactly one spellbook");
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void chestRollUsesProductionFivePercentAndUniformlySelectsEveryBook(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "chestRollUsesProductionFivePercentAndUniformlySelectsEveryBook");
		double chance = SpellbookTestAccess.chance("CHEST_DROP_CHANCE");
		RandomSource random = RandomSource.create(582019L);
		int drops = 0;
		boolean invalidDrop = false;
		Map<Identifier, Integer> bookCounts = emptyBookCounts();
		for (int i = 0; i < ROLLS; i++) {
			ItemStack stack = SpellbookTestAccess.rollChest(DUNGEON_CHEST, true, random);
			if (!stack.isEmpty()) {
				drops++;
				Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				invalidDrop |= stack.getCount() != 1 || !SPELLBOOK_IDS.contains(id);
				bookCounts.computeIfPresent(id, (ignored, count) -> count + 1);
			}
		}
		helper.assertTrue(
			withinThreeStandardDeviations(drops, chance),
			"chest drops " + drops + " do not match production chance " + chance
		);
		helper.assertTrue(
			uniformlyDistributed(bookCounts, chance),
			"chest book counts are not uniform: " + bookCounts
		);
		helper.assertFalse(invalidDrop, "a chest roll returned more than one or a non-catalog spellbook");
		Spellbooks.LOGGER.info(
			"[SPELLTEST] loot source=chest rolls={} drop_chance={} catalog_counts={}", ROLLS, chance, bookCounts
		);
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void witchRollUsesProductionFiftyPercentAndUniformlySelectsEveryBook(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "witchRollUsesProductionFiftyPercentAndUniformlySelectsEveryBook");
		double chance = SpellbookTestAccess.chance("WITCH_DROP_CHANCE");
		RandomSource random = RandomSource.create(782131L);
		int drops = 0;
		boolean invalidDrop = false;
		Map<Identifier, Integer> bookCounts = emptyBookCounts();
		for (int i = 0; i < ROLLS; i++) {
			ItemStack stack = SpellbookTestAccess.rollWitch(random);
			if (!stack.isEmpty()) {
				drops++;
				Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				invalidDrop |= stack.getCount() != 1 || !SPELLBOOK_IDS.contains(id);
				bookCounts.computeIfPresent(id, (ignored, count) -> count + 1);
			}
		}
		helper.assertTrue(
			withinThreeStandardDeviations(drops, chance),
			"witch drops " + drops + " do not match production chance " + chance
		);
		helper.assertTrue(
			uniformlyDistributed(bookCounts, chance),
			"witch book counts are not uniform: " + bookCounts
		);
		helper.assertFalse(invalidDrop, "a witch roll returned more than one or a non-catalog spellbook");
		Spellbooks.LOGGER.info(
			"[SPELLTEST] loot source=witch rolls={} drop_chance={} catalog_counts={}", ROLLS, chance, bookCounts
		);
		outcome.pass();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void witchDeathWithoutAKillerCanDropOneSpellbook(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "witchDeathWithoutAKillerCanDropOneSpellbook");
		Vec3 origin = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		AABB dropsArea = new AABB(
			origin.x - 10.0, origin.y - 5.0, origin.z - 10.0,
			origin.x + 10.0, origin.y + 5.0, origin.z + 10.0
		);
		AABB structureBounds = helper.getBounds();
		AABB relativeBounds = helper.getRelativeBounds();
		Vec3 relativeSentinelPosition = new Vec3(
			relativeBounds.getCenter().x,
			relativeBounds.minY + 0.5,
			relativeBounds.getCenter().z
		);
		for (int i = 0; i < 33; i++) {
			ItemEntity sentinel = helper.spawnItem(Spellbooks.SPARK_SHOT, relativeSentinelPosition);
			helper.assertTrue(structureBounds.contains(sentinel.position()), "a Spark Shot sentinel is outside the test structure");
			helper.assertTrue(dropsArea.contains(sentinel.position()), "a Spark Shot sentinel is outside the old count bounds");
		}
		long sentinelCount = helper.getLevel().getEntitiesOfClass(
			ItemEntity.class,
			structureBounds,
			item -> SPARK_SHOT.equals(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()))
		).size();
		helper.assertTrue(sentinelCount == 33, "the test did not add 33 pre-existing Spark Shot entities");
		int spellbooks = 0;
		for (int i = 0; i < 32; i++) {
			Witch witch = helper.spawnMob(EntityTypes.WITCH, 2 + i % 4, 1, 2 + i / 4).withNoFreeWill().spawn();
			witch.getRandom().setSeed(i + 1L);
			Set<UUID> itemIdsBeforeDeath = itemEntityIds(helper, structureBounds);
			witch.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
			List<ItemEntity> newItems = helper.getLevel().getEntitiesOfClass(
				ItemEntity.class,
				structureBounds,
				item -> !itemIdsBeforeDeath.contains(item.getUUID())
			);
			List<ItemEntity> newSpellbooks = newItems.stream()
				.filter(item -> isSpellbook(item.getItem()))
				.toList();
			List<String> spellbookDetails = newSpellbooks.stream()
				.map(item -> BuiltInRegistries.ITEM.getKey(item.getItem().getItem()) + ":" + item.getUUID()
					+ ":" + item.getItem().getCount() + "@" + item.position())
				.toList();
			helper.assertTrue(
				newSpellbooks.size() <= 1,
				"witch=" + witch.getUUID() + " added multiple spellbook item entities: " + spellbookDetails
			);
			for (ItemEntity spellbook : newSpellbooks) {
				helper.assertTrue(spellbook.getItem().getCount() == 1, "a witch death added a stacked spellbook entity");
				spellbooks++;
			}
		}
		helper.assertTrue(spellbooks >= 1 && spellbooks <= 32, "uncredited witch deaths did not drop one roll per success");
		outcome.pass();
		helper.succeed();
	}

	private static Set<UUID> itemEntityIds(final GameTestHelper helper, final AABB bounds) {
		Set<UUID> ids = new HashSet<>();
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds, entity -> true)) {
			ids.add(item.getUUID());
		}

		return ids;
	}

	@GameTest
	public void cowLootTableNeverAddsASpellbook(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "cowLootTableNeverAddsASpellbook");
		ItemStack stack = SpellbookTestAccess.rollChest(
			Identifier.parse("minecraft:entities/cow"), true, RandomSource.create(successfulRollSeed(helper))
		);
		helper.assertTrue(stack.isEmpty(), "the cow loot table returned a spellbook");
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void testModChestTableNeverAddsASpellbook(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "testModChestTableNeverAddsASpellbook");
		Identifier tableId = Identifier.parse("mod-stella-spells-test-mod-stella-spells:chests/excluded");
		ItemStack stack = SpellbookTestAccess.rollChest(
			tableId, true, RandomSource.create(successfulRollSeed(helper))
		);
		helper.assertTrue(stack.isEmpty(), "the test-mod chest roll returned a spellbook");
		LootTable testChest = helper.getLevel().getServer().reloadableRegistries()
			.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId));
		helper.assertTrue(testChest != null, "the excluded test-mod chest table is not loaded");
		Vec3 origin = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		LootParams params = new LootParams.Builder(helper.getLevel())
			.withParameter(LootContextParams.ORIGIN, origin)
			.withLuck(0.0f)
			.create(LootContextParamSets.CHEST);
		boolean generatedSpellbook = false;
		for (long seed = 0; seed < 1_000 && !generatedSpellbook; seed++) {
			generatedSpellbook = testChest.getRandomItems(params, RandomSource.create(seed)).stream()
				.anyMatch(SpellbookLootGameTest::isSpellbook);
		}
		helper.assertFalse(generatedSpellbook, "the test-mod chest loot path added a Spark Shot book");
		outcome.pass();
		helper.succeed();
	}

	private static long successfulRollSeed(final GameTestHelper helper) {
		double chance = SpellbookTestAccess.chance("CHEST_DROP_CHANCE");
		for (long seed = 0; seed < 10_000; seed++) {
			if (RandomSource.create(seed).nextDouble() < chance) {
				return seed;
			}
		}
		helper.assertTrue(false, "no seeded chest roll succeeded");
		return 0L;
	}

	private static Map<Identifier, Integer> emptyBookCounts() {
		Map<Identifier, Integer> counts = new HashMap<>();
		for (Identifier id : SPELLBOOK_IDS) {
			counts.put(id, 0);
		}
		return counts;
	}

	private static boolean uniformlyDistributed(final Map<Identifier, Integer> counts, final double chance) {
		double perBookChance = chance / SpellbookCatalog.DEFINITIONS.size();
		return SPELLBOOK_IDS.stream().allMatch(
			id -> withinThreeStandardDeviations(counts.getOrDefault(id, 0), perBookChance)
		);
	}

	private static boolean isSpellbook(final ItemStack stack) {
		return !stack.isEmpty() && SPELLBOOK_IDS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	private static boolean withinThreeStandardDeviations(final int actual, final double chance) {
		double expected = ROLLS * chance;
		double deviation = Math.sqrt(ROLLS * chance * (1.0 - chance));
		return actual >= expected - 3.0 * deviation && actual <= expected + 3.0 * deviation;
	}
}
