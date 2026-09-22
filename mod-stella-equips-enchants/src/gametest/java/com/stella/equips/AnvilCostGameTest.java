package com.stella.equips;

import com.stella.equips.anvil.AnvilCost;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class AnvilCostGameTest {
	private static final Identifier TEST_MARKER =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "test_marker");
	private static final int FIXTURE_ANVIL_COST = 10;
	private static final int FIXTURE_MIN_COST = 1;
	private static final int CLAMPED_COST = 1;
	private static final int MERGE_COST = 2;
	private static final int PICKAXE_DAMAGE = 500;

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
	public void anvilMenuCostIsClampedToOne(final GameTestHelper helper) {
		AnvilMenu menu = populatedMenu(helper);
		int cost = menu.getCost();
		helper.assertTrue(
			cost == CLAMPED_COST,
			"AnvilMenu.getCost() of a merge worth " + MERGE_COST + ": expected " + CLAMPED_COST + " but was " + cost
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=anvilCostClamped RESULT cost={} vanillaCost={}",
			cost, MERGE_COST
		);
		helper.succeed();
	}

	@GameTest
	public void clampFunctionKeepsOneAsTheCeiling(final GameTestHelper helper) {
		helper.assertTrue(AnvilCost.clamp(0) == 0, "clamp(0) changed a free operation to " + AnvilCost.clamp(0));
		helper.assertTrue(AnvilCost.clamp(1) == 1, "clamp(1) changed an exact cost to " + AnvilCost.clamp(1));
		helper.assertTrue(AnvilCost.clamp(40) == 1, "clamp(40) returned " + AnvilCost.clamp(40));
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=clampFunction RESULT clamp0={} clamp1={} clamp40={}",
			AnvilCost.clamp(0), AnvilCost.clamp(1), AnvilCost.clamp(40)
		);
		helper.succeed();
	}

	@SuppressWarnings("removal")
	private static AnvilMenu populatedMenu(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Inventory inventory = player.getInventory();
		AnvilMenu menu = new AnvilMenu(1, inventory, ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(damagedPickaxe());
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(damagedPickaxe());
		menu.createResult();
		return menu;
	}

	private static ItemStack damagedPickaxe() {
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		pickaxe.setDamageValue(PICKAXE_DAMAGE);
		EnchantmentHelper.setEnchantments(pickaxe, ItemEnchantments.EMPTY);
		return pickaxe;
	}

	private static Registry<Enchantment> enchantments(final GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}
}
