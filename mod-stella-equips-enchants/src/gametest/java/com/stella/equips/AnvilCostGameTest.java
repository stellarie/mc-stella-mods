package com.stella.equips;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;

/** Slice 6. The anvil price is emeralds per applied enchantment, never levels. */
public class AnvilCostGameTest {
	private static final Identifier TEST_MARKER =
		Identifier.fromNamespaceAndPath(PowerFixtures.TEST_MOD_ID, "test_marker");
	private static final int FIXTURE_ANVIL_COST = 10;
	private static final int FIXTURE_MIN_COST = 1;
	private static final int VANILLA_MERGE_COST = 2;
	private static final int PICKAXE_DAMAGE = 500;
	private static final int LEVELS_BEFORE = 10;
	private static final int REPAIR_COST_PER_INPUT = 20;
	private static final int GATE_THRESHOLD = 40;
	private static final int VANILLA_TAXED_COST = 42;
	private static final int EMERALDS = 16;
	private static final int FREE = 0;

	@GameTest
	public void testMarkerEnchantmentIsRegistered(final GameTestHelper helper) {
		Enchantment marker = enchantments(helper).getValue(TEST_MARKER);
		helper.assertTrue(
			marker != null,
			"the fixture enchantment " + TEST_MARKER + " is not in the registry"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=testMarkerRegistered RESULT id={} present={}",
			TEST_MARKER, marker != null
		);
		helper.succeed();
	}

	@GameTest
	public void fixtureKeepsItsOwnCostFields(final GameTestHelper helper) {
		Enchantment marker = enchantments(helper).getValue(TEST_MARKER);
		helper.assertTrue(
			marker != null,
			"the fixture enchantment " + TEST_MARKER + " is not in the registry"
		);
		helper.assertTrue(
			marker.getAnvilCost() == FIXTURE_ANVIL_COST,
			"fixture anvil_cost: expected " + FIXTURE_ANVIL_COST + " but was " + marker.getAnvilCost()
		);
		helper.assertTrue(
			marker.getMinCost(1) == FIXTURE_MIN_COST,
			"fixture min_cost at level 1: expected " + FIXTURE_MIN_COST + " but was " + marker.getMinCost(1)
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=fixtureCostFields RESULT anvilCost={} minCost={}",
			marker.getAnvilCost(), marker.getMinCost(1)
		);
		helper.succeed();
	}

	@GameTest
	public void eachEnchantmentDoublesThePriceOfTheNext(final GameTestHelper helper) {
		Registry<Enchantment> registry = enchantments(helper);
		// Two held, one new: 2^2.
		ItemStack sword = enchanted(registry, new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, Enchantments.UNBREAKING);
		int[] one = takeWith(helper, sword, enchanted(registry, new ItemStack(Items.ENCHANTED_BOOK), Enchantments.FIRE_ASPECT), 4);
		// One held, two new: 2^1 + 2^2.
		ItemStack axe = enchanted(registry, new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS);
		int[] two = takeWith(helper, axe, enchanted(registry, new ItemStack(Items.ENCHANTED_BOOK), Enchantments.FIRE_ASPECT, Enchantments.KNOCKBACK), 6);
		// None held: the first costs 1.
		int[] first = takeWith(helper, new ItemStack(Items.DIAMOND_SWORD), enchanted(registry, new ItemStack(Items.ENCHANTED_BOOK), Enchantments.FIRE_ASPECT), 1);
		helper.assertTrue(one[0] == 4 && one[1] == 0 && two[0] == 6 && two[1] == 0 && first[0] == 1 && first[1] == 0,
			"price/left: held2+1=" + one[0] + "/" + one[1] + " held1+2=" + two[0] + "/" + two[1] + " held0+1=" + first[0] + "/" + first[1]);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=pow2Price RESULT held2add1={} held1add2={} held0add1={}", one[0], two[0], first[0]);
		helper.succeed();
	}

	/** Puts both items in a real anvil, pays with exactly `emeralds`, and returns the price and the emeralds left. */
	private static int[] takeWith(final GameTestHelper helper, final ItemStack input, final ItemStack addition, final int emeralds) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(addition);
		menu.createResult();
		player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		int price = menu.getCost();
		helper.assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player), "not takeable at price " + price + " with " + emeralds);
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, menu.getSlot(AnvilMenu.RESULT_SLOT).getItem());
		return new int[] {price, countEmeralds(player)};
	}

	@SafeVarargs
	private static ItemStack enchanted(final Registry<Enchantment> registry, final ItemStack stack, final ResourceKey<Enchantment>... keys) {
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (ResourceKey<Enchantment> key : keys) {
			mutable.set(registry.getOrThrow(key), 1);
		}
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}

	@GameTest
	public void aMergeThatAppliesNoEnchantmentIsFree(final GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		AnvilMenu menu = populatedMenu(player, 0);
		int price = menu.getCost();
		player.getInventory().add(new ItemStack(Items.EMERALD, EMERALDS));
		player.experienceLevel = LEVELS_BEFORE;
		boolean takeable = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, ItemStack.EMPTY);
		int left = countEmeralds(player);
		helper.assertTrue(
			price == FREE && takeable && left == EMERALDS && player.experienceLevel == LEVELS_BEFORE,
			"plain merge: price=" + price + " takeable=" + takeable + " emeraldsLeft=" + left
				+ " level=" + player.experienceLevel
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=plainMergeIsFree RESULT price={} takeable={} emeraldsLeft={} level={}",
			price, takeable, left, player.experienceLevel
		);
		helper.succeed();
	}

	@GameTest
	public void theTaxedMergeStillAssemblesWithoutLevels(final GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		AnvilMenu menu = populatedMenu(player, REPAIR_COST_PER_INPUT);
		int inputTax = repairCostOf(menu, AnvilMenu.INPUT_SLOT) + repairCostOf(menu, AnvilMenu.ADDITIONAL_SLOT);
		int vanillaCost = inputTax + VANILLA_MERGE_COST;
		helper.assertTrue(
			vanillaCost == VANILLA_TAXED_COST && vanillaCost >= GATE_THRESHOLD,
			"the fixture no longer reaches the Too Expensive gate: inputTax=" + inputTax
				+ " mergePrice=" + VANILLA_MERGE_COST + " vanillaCost=" + vanillaCost
				+ " gateThreshold=" + GATE_THRESHOLD
		);
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		int price = menu.getCost();
		player.experienceLevel = LEVELS_BEFORE;
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, ItemStack.EMPTY);
		helper.assertTrue(
			!result.isEmpty() && price == FREE && player.experienceLevel == LEVELS_BEFORE,
			"taxed merge: resultEmpty=" + result.isEmpty() + " price=" + price
				+ " level=" + player.experienceLevel
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=taxedMergeAssembles RESULT inputTax={} vanillaCost={} gateThreshold={} "
				+ "resultEmpty={} price={} level={}",
			inputTax, vanillaCost, GATE_THRESHOLD, result.isEmpty(), price, player.experienceLevel
		);
		helper.succeed();
	}

	private static int repairCostOf(final AnvilMenu menu, final int slot) {
		return menu.getSlot(slot).getItem().getOrDefault(DataComponents.REPAIR_COST, 0);
	}

	private static int countEmeralds(final Player player) {
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	private static AnvilMenu populatedMenu(final Player player, final int repairCost) {
		Inventory inventory = player.getInventory();
		AnvilMenu menu = new AnvilMenu(1, inventory, ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(damagedPickaxe(repairCost));
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(damagedPickaxe(repairCost));
		menu.createResult();
		return menu;
	}

	private static ItemStack damagedPickaxe(final int repairCost) {
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		pickaxe.setDamageValue(PICKAXE_DAMAGE);
		EnchantmentHelper.setEnchantments(pickaxe, ItemEnchantments.EMPTY);
		pickaxe.set(DataComponents.REPAIR_COST, repairCost);
		return pickaxe;
	}

	private static Registry<Enchantment> enchantments(final GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}
}
