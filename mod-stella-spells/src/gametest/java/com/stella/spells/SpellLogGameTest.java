package com.stella.spells;

import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class SpellLogGameTest {
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final Identifier WITHER_SEAL = Identifier.parse("mod-stella-spells:wither_seal");
	private static final Identifier CRAFT_LOG_CHEST = Identifier.parse("minecraft:chests/w005_craft_log");
	private static final Identifier FAILED_LOG_CHEST = Identifier.parse("minecraft:chests/w005_failed_log");
	private static final Identifier ANVIL_TEST_ENCHANTMENT = Identifier.parse("mod-stella-spells-test-mod-stella-spells:anvil_log_test");

	@GameTest
	public void castLogHasSpellTargetCooldownAndEnchantFields(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "castLogHasSpellTargetCooldownAndEnchantFields");
		SpellbookItem book = spellbook(helper, WITHER_SEAL, "Wither Seal");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		try (SpellLogCapture capture = new SpellLogCapture()) {
			book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(
				capture.contains(
					"INFO [SPELL]", "spell_id=" + WITHER_SEAL, "result=CAST",
					"target=" + target.getUUID(), "cooldown_ticks=360", "book_enchants=[]",
					"head_enchants=[]", "chest_enchants=[]"
				),
				"CAST log omitted the actual spellbook ID or a required field"
			);
			capture.echoOne("result=CAST", "target=" + target.getUUID(), "caster=" + player.getUUID());
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void noTargetLogHasSpellAndResultFields(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "noTargetLogHasSpellAndResultFields");
		Item item = sparkShot(helper);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		helper.setBlock(2, 2, 4, Blocks.STONE);
		ItemStack book = new ItemStack(item);
		player.setItemInHand(InteractionHand.MAIN_HAND, book);
		try (SpellLogCapture capture = new SpellLogCapture()) {
			item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(
				capture.contains(
					"INFO [SPELL]", "spell_id=mod-stella-spells:spark_shot", "result=NO_TARGET",
					"target=none", "cooldown_ticks=60", "book_enchants=[]", "head_enchants=[]", "chest_enchants=[]"
				),
				"NO_TARGET log omitted a required field"
			);
			capture.echoOne("result=NO_TARGET", "target=none", "caster=" + player.getUUID());
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void cooldownLogHasSpellAndResultFields(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "cooldownLogHasSpellAndResultFields");
		Item item = sparkShot(helper);
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		ItemStack book = new ItemStack(item);
		player.setItemInHand(InteractionHand.MAIN_HAND, book);
		try (SpellLogCapture capture = new SpellLogCapture()) {
			item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(
				capture.contains(
					"INFO [SPELL]", "spell_id=mod-stella-spells:spark_shot", "result=COOLDOWN",
					"target=" + target.getUUID(), "cooldown_ticks=60", "book_enchants=[]",
					"head_enchants=[]", "chest_enchants=[]"
				),
				"COOLDOWN log omitted a required field"
			);
			capture.echoOne("result=COOLDOWN", "target=" + target.getUUID(), "caster=" + player.getUUID());
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void successfulDropLogUsesActualSpellbookItemId(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "successfulDropLogUsesActualSpellbookItemId");
		// The seed is found on the whole chest path, whose scroll and clue draws come before the book's.
		long seed = chestDropSeedForOneNonSparkBook(helper, CRAFT_LOG_CHEST);
		ArrayList<ItemStack> probe = new ArrayList<>();
		SpellbookTestAccess.modifyChestDrops(CRAFT_LOG_CHEST, true, RandomSource.create(seed), probe);
		Identifier itemId = BuiltInRegistries.ITEM.getKey(probe.get(0).getItem());
		ArrayList<ItemStack> drops = new ArrayList<>();
		try (SpellLogCapture capture = new SpellLogCapture()) {
			SpellbookTestAccess.modifyChestDrops(CRAFT_LOG_CHEST, true, RandomSource.create(seed), drops);
			helper.assertTrue(drops.size() == 1, "a successful chest roll did not add one book");
			helper.assertTrue(
				capture.contains(
					"INFO [SPELL]", "event=DROP", "source=chest", "table_id=" + CRAFT_LOG_CHEST,
					"item_id=" + itemId
				),
				"DROP log did not use the actual spellbook item ID"
			);
			capture.echoOne("event=DROP", "table_id=" + CRAFT_LOG_CHEST, "item_id=" + itemId);
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void successfulWitchDropLogUsesActualSpellbookItemId(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "successfulWitchDropLogUsesActualSpellbookItemId");
		Witch witch = helper.spawnMob(EntityTypes.WITCH, 2, 1, 2).withNoFreeWill().spawn();
		long seed = witchSeedForNonSpark(helper);
		Identifier itemId = BuiltInRegistries.ITEM.getKey(
			SpellbookTestAccess.rollWitch(RandomSource.create(seed)).getItem()
		);
		try (SpellLogCapture capture = new SpellLogCapture()) {
			SpellbookTestAccess.applyWitchDeath(
				witch, helper.getLevel().damageSources().generic(), RandomSource.create(seed)
			);
			helper.assertTrue(
				capture.contains(
					"INFO [SPELL]", "event=DROP", "source=witch", "source_entity=" + witch.getUUID(),
					"item_id=" + itemId
				),
				"witch DROP log did not use the actual spellbook item ID"
			);
			capture.echoOne("event=DROP", "source=witch", "source_entity=" + witch.getUUID(), "item_id=" + itemId);
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void failedLootRollLogHasSourceChanceRollAndResult(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "failedLootRollLogHasSourceChanceRollAndResult");
		long seed = emptyChestSeed(helper, FAILED_LOG_CHEST);
		ArrayList<ItemStack> drops = new ArrayList<>();
		try (SpellLogCapture capture = new SpellLogCapture()) {
			SpellbookTestAccess.modifyChestDrops(FAILED_LOG_CHEST, true, RandomSource.create(seed), drops);
			helper.assertTrue(drops.isEmpty(), "a failed chest roll added a book");
			helper.assertTrue(
				capture.contains(
					"DEBUG [SPELL]", "table_id=" + FAILED_LOG_CHEST, "source=chest", "chance=", "roll=", "result=FAIL"
				),
				"failed-roll log omitted a required field or DEBUG level"
			);
			capture.echoOne("DEBUG [SPELL]", "table_id=" + FAILED_LOG_CHEST, "result=FAIL");
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void directCraftLogsOnceAfterARecipePreview(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "directCraftLogsOnceAfterARecipePreview");
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		try (SpellLogCapture capture = new SpellLogCapture()) {
			CraftingMenu menu = craftingMenu(helper, player);
			ResultSlot resultSlot = (ResultSlot) menu.getResultSlot();
			ItemStack preview = resultSlot.getItem();
			helper.assertTrue(isSparkShot(preview), "the Spark Shot recipe preview is missing");
			helper.assertTrue(capture.count("event=CRAFT") == 0, "the recipe preview emitted a craft log");
			ItemStack taken = resultSlot.remove(1);
			resultSlot.onTake(player, taken);
			helper.assertTrue(
				capture.count("event=CRAFT", "recipe_id=mod-stella-spells:spark_shot", "item_id=mod-stella-spells:spark_shot") == 1,
				"direct pickup did not emit exactly one craft log with the required fields"
			);
			capture.echoOne("event=CRAFT", "recipe_id=mod-stella-spells:spark_shot");
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void craftLogRequiresAResultRemovalCount(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "craftLogRequiresAResultRemovalCount");
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		CraftingMenu menu = craftingMenu(helper, player);
		ResultSlot resultSlot = (ResultSlot) menu.getResultSlot();
		RecipeCraftingHolder holder = (RecipeCraftingHolder) resultSlot.container;
		RecipeHolder<?> recipe = holder.getRecipeUsed();
		helper.assertTrue(recipe != null, "the crafting result has no recipe holder");
		try (SpellLogCapture capture = new SpellLogCapture()) {
			ItemStack taken = resultSlot.remove(1);
			resultSlot.onTake(player, taken);
			helper.assertTrue(capture.count("event=CRAFT") == 1, "the pickup emitted no craft log");
			holder.setRecipeUsed(recipe);
			resultSlot.onTake(player, taken);
			helper.assertTrue(capture.count("event=CRAFT") == 1, "a take with no item removal emitted another log");
			capture.echoOne("event=CRAFT", "recipe_id=mod-stella-spells:spark_shot");
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void shiftClickCraftLogsOnce(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "shiftClickCraftLogsOnce");
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		try (SpellLogCapture capture = new SpellLogCapture()) {
			CraftingMenu menu = craftingMenu(helper, player);
			ItemStack taken = menu.quickMoveStack(player, CraftingMenu.RESULT_SLOT);
			helper.assertTrue(isSparkShot(taken), "shift-click did not take the Spark Shot result");
			helper.assertTrue(
				capture.count("event=CRAFT", "recipe_id=mod-stella-spells:spark_shot", "item_id=mod-stella-spells:spark_shot") == 1,
				"shift-click emitted more or fewer than one craft log"
			);
			capture.echoOne("event=CRAFT", "recipe_id=mod-stella-spells:spark_shot");
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void resultWithoutRecipeHolderDoesNotLogACraft(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "resultWithoutRecipeHolderDoesNotLogACraft");
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		CraftingMenu menu = craftingMenu(helper, player);
		CraftingContainer grid = (CraftingContainer) menu.getInputGridSlots().get(0).container;
		SimpleContainer resultContainer = new SimpleContainer(1);
		resultContainer.setItem(0, Spellbooks.SPARK_SHOT.getDefaultInstance());
		ResultSlot resultSlot = new ResultSlot(player, grid, resultContainer, 0, 0, 0);
		try (SpellLogCapture capture = new SpellLogCapture()) {
			ItemStack result = resultSlot.remove(1);
			resultSlot.onTake(player, result);
			helper.assertTrue(capture.count("event=CRAFT") == 0, "a result without a recipe holder emitted a craft log");
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void anvilLogsSpellEnchantmentIncreaseAtTake(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "anvilLogsSpellEnchantmentIncreaseAtTake");
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, ANVIL_TEST_ENCHANTMENT);
		Holder.Reference<Enchantment> enchantment = helper.getLevel().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		ItemStack input = enchantSparkShot(enchantment, 1);
		ItemStack addition = enchantedBook(enchantment, 1);
		helper.assertTrue(EnchantmentHelper.canStoreEnchantments(input), "the test book cannot store enchantments");
		helper.assertTrue(enchantment.value().canEnchant(input), "the test enchantment does not support the test book");
		helper.assertTrue(enchantment.value().isPrimaryItem(input), "the test book is not a primary item for the fixture");
		AnvilMenu menu = new AnvilMenu(1, player.getInventory());
		try (SpellLogCapture capture = new SpellLogCapture()) {
			menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
			menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(addition);
			menu.createResult();
			ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
			helper.assertTrue(
				isSparkShot(result),
				"the anvil did not return a Spark Shot book: input=" + input + " addition=" + addition + " cost=" + menu.getCost()
			);
			helper.assertTrue(
				EnchantmentHelper.getEnchantmentsForCrafting(result).getLevel(enchantment) == 2,
				"the anvil did not raise the test enchantment to level 2"
			);
			helper.assertTrue(capture.count("event=ANVIL") == 0, "the anvil preview emitted a log");
			menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result);
			helper.assertTrue(
				capture.count(
					"INFO [SPELL]", "event=ANVIL", "item_id=mod-stella-spells:spark_shot",
					"enchantment_id=" + ANVIL_TEST_ENCHANTMENT, "level=2"
				) == 1,
				"taking anvil output did not log one spell-enchantment increase"
			);
			capture.echoOne("event=ANVIL", "enchantment_id=" + ANVIL_TEST_ENCHANTMENT, "level=2");
		}
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void spellTestSummaryUsesLifecycleResult(final GameTestHelper helper) {
		SpellTestLog.track(helper, "spellTestSummaryUsesLifecycleResult");
		helper.succeed();
	}

	private static ItemStack enchantSparkShot(final Holder<Enchantment> enchantment, final int level) {
		ItemEnchantments.Mutable values = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		values.set(enchantment, level);
		ItemStack stack = Spellbooks.SPARK_SHOT.getDefaultInstance();
		stack.set(DataComponents.ENCHANTMENTS, values.toImmutable());
		return stack;
	}

	private static ItemStack enchantedBook(final Holder<Enchantment> enchantment, final int level) {
		ItemEnchantments.Mutable values = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		values.set(enchantment, level);
		ItemStack stack = Items.ENCHANTED_BOOK.getDefaultInstance();
		stack.set(DataComponents.STORED_ENCHANTMENTS, values.toImmutable());
		return stack;
	}

	private static CraftingMenu craftingMenu(final GameTestHelper helper, final ServerPlayer player) {
		CraftingMenu menu = new CraftingMenu(
			1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), BlockPos.ZERO)
		);
		var inputs = menu.getInputGridSlots();
		for (Slot slot : inputs) {
			slot.set(ItemStack.EMPTY);
		}
		inputs.get(0).set(new ItemStack(Items.FLINT));
		inputs.get(2).set(new ItemStack(Items.FLINT));
		inputs.get(4).set(new ItemStack(Items.BOOK));
		inputs.get(6).set(new ItemStack(Items.FLINT));
		inputs.get(8).set(new ItemStack(Items.FLINT));
		CraftingContainer grid = (CraftingContainer) inputs.get(0).container;
		var recipe = player.level().getServer().getRecipeManager()
			.getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), player.level()).orElseThrow();
		player.getRecipeBook().add(recipe.id());
		menu.slotsChanged(grid);
		return menu;
	}

	private static boolean isSparkShot(final ItemStack stack) {
		return !stack.isEmpty() && SPARK_SHOT.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	private static SpellbookItem spellbook(final GameTestHelper helper, final Identifier id, final String name) {
		Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item instanceof SpellbookItem, name + " item is not registered");
		return (SpellbookItem) item;
	}

	private static Item sparkShot(final GameTestHelper helper) {
		Item item = BuiltInRegistries.ITEM.get(SPARK_SHOT).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item != Items.AIR, "Spark Shot item is not registered");
		return item;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer addPlayer(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.setPos(position.x, position.y, position.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		return player;
	}

	private static long witchSeedForNonSpark(final GameTestHelper helper) {
		for (long seed = 0; seed < 100_000; seed++) {
			ItemStack stack = SpellbookTestAccess.rollWitch(RandomSource.create(seed));
			if (!stack.isEmpty() && !isSparkShot(stack)) {
				return seed;
			}
		}
		helper.assertTrue(false, "no deterministic witch seed produced a non-Spark spellbook");
		return 0L;
	}

	private static long chestSeedForNonSpark(final GameTestHelper helper, final Identifier table) {
		for (long seed = 0; seed < 100_000; seed++) {
			ItemStack stack = SpellbookTestAccess.rollChest(table, true, RandomSource.create(seed));
			if (!stack.isEmpty() && !isSparkShot(stack)) {
				return seed;
			}
		}
		helper.assertTrue(false, "no deterministic chest seed produced a non-Spark spellbook");
		return 0L;
	}

	private static long chestDropSeedForOneNonSparkBook(final GameTestHelper helper, final Identifier table) {
		for (long seed = 0; seed < 100_000; seed++) {
			ArrayList<ItemStack> drops = new ArrayList<>();
			SpellbookTestAccess.modifyChestDrops(table, true, RandomSource.create(seed), drops);
			if (drops.size() == 1 && drops.get(0).getItem() instanceof SpellbookItem && !isSparkShot(drops.get(0))) {
				return seed;
			}
		}
		helper.assertTrue(false, "no deterministic chest seed dropped one non-Spark spellbook");
		return 0L;
	}

	private static long witchSeed(final GameTestHelper helper) {
		for (long seed = 0; seed < 10_000; seed++) {
			if (!SpellbookTestAccess.rollWitch(RandomSource.create(seed)).isEmpty()) {
				return seed;
			}
		}
		helper.assertTrue(false, "no deterministic witch seed produced a spellbook");
		return 0L;
	}

	// A seed on which every chest draw fails, the scroll and the clue draws included, so the book roll is the one logged.
	private static long emptyChestSeed(final GameTestHelper helper, final Identifier table) {
		for (long seed = 0; seed < 10_000; seed++) {
			ArrayList<ItemStack> drops = new ArrayList<>();
			SpellbookTestAccess.modifyChestDrops(table, true, RandomSource.create(seed), drops);
			if (drops.isEmpty()) {
				return seed;
			}
		}
		helper.assertTrue(false, "no deterministic chest seed left the chest empty");
		return 0L;
	}

	private static long chestSeed(final GameTestHelper helper, final boolean success, final Identifier table) {
		for (long seed = 0; seed < 10_000; seed++) {
			boolean drops = !SpellbookTestAccess.rollChest(table, true, RandomSource.create(seed)).isEmpty();
			if (drops == success) {
				return seed;
			}
		}
		helper.assertTrue(false, "no deterministic chest seed matched the requested result");
		return 0L;
	}
}
