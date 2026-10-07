package com.stella.rpg;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** The second hotbar row swaps with the main row, and both rows keep every item through the swap. */
public final class SecondHotbarGameTest {
	private static final int SIZE = SecondHotbar.SIZE;

	@GameTest
	public void theRowsSwapAndKeepEveryItem(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		ItemStack[] mainBefore = fill(inventory, Items.DIAMOND_SWORD, Items.COBBLESTONE);
		ItemStack[] storedBefore = fill(SecondHotbar.of(player), Items.GOLDEN_APPLE, Items.ARROW);
		Map<String, Integer> before = totals(player);

		boolean swapped = SecondHotbar.swap(player);
		SecondHotbar stored = SecondHotbar.of(player);
		int mismatched = 0;
		StringBuilder detail = new StringBuilder();
		for (int i = 0; i < SIZE; i++) {
			detail.append(i).append(':').append(inventory.getItem(i)).append('|').append(stored.get(i)).append(' ');
			if (inventory.getItem(i) != storedBefore[i] || stored.get(i) != mainBefore[i]) {
				mismatched++;
			}
		}
		Map<String, Integer> after = totals(player);
		RpgTestSupport.result("theRowsSwapAndKeepEveryItem",
			"mismatched=" + mismatched + " before=" + before + " after=" + after);
		helper.assertTrue(swapped, "the swap was refused");
		helper.assertTrue(mismatched == 0, mismatched + " of " + SIZE + " slots did not exchange: " + detail);
		helper.assertTrue(before.equals(after), "before " + before + " after " + after);
		helper.succeed();
	}

	@GameTest
	public void theHeldItemFollowsTheShownRow(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		QuickbarMode.set(player, false);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		fill(inventory, Items.DIAMOND_SWORD, Items.COBBLESTONE);
		fill(SecondHotbar.of(player), Items.GOLDEN_APPLE, Items.ARROW);
		int held = 3;
		inventory.setSelectedSlot(held);
		ItemStack before = player.getMainHandItem();

		SecondHotbar.swap(player);
		ItemStack after = player.getMainHandItem();
		RpgTestSupport.result("theHeldItemFollowsTheShownRow",
			"held=" + held + " before=" + before + " after=" + after + " selected=" + inventory.getSelectedSlot());
		helper.assertTrue(before.is(Items.COBBLESTONE), "the main row did not hold the expected item: " + before);
		helper.assertTrue(after.is(Items.ARROW), "the main hand after the swap is " + after);
		helper.assertTrue(inventory.getSelectedSlot() == held, "the held slot moved to " + inventory.getSelectedSlot());
		helper.assertTrue(inventory.getItem(held) == after, "the hand is not the shown row's held slot");
		helper.succeed();
	}

	@GameTest
	public void theSwapIsRefusedInSpectator(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		fill(inventory, Items.DIAMOND_SWORD, Items.COBBLESTONE);
		fill(SecondHotbar.of(player), Items.GOLDEN_APPLE, Items.ARROW);
		player.setGameMode(GameType.SPECTATOR);

		boolean swapped = SecondHotbar.swap(player);
		int moved = 0;
		for (int i = 0; i < SIZE; i++) {
			if (!inventory.getItem(i).is(Items.DIAMOND_SWORD) && !inventory.getItem(i).is(Items.COBBLESTONE)) {
				moved++;
			}
		}
		RpgTestSupport.result("theSwapIsRefusedInSpectator", "swapped=" + swapped + " moved=" + moved);
		helper.assertTrue(!swapped, "a spectator swapped the rows");
		helper.assertTrue(moved == 0, moved + " of " + SIZE + " slots moved for a spectator");
		helper.succeed();
	}

	@GameTest
	public void theSwapIsItsOwnInverse(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		ItemStack[] mainBefore = fill(inventory, Items.DIAMOND_SWORD, Items.COBBLESTONE);
		ItemStack[] storedBefore = fill(SecondHotbar.of(player), Items.GOLDEN_APPLE, Items.ARROW);
		Map<String, Integer> before = totals(player);

		SecondHotbar.swap(player);
		SecondHotbar.swap(player);
		SecondHotbar stored = SecondHotbar.of(player);
		int returned = 0;
		for (int i = 0; i < SIZE; i++) {
			if (inventory.getItem(i) == mainBefore[i] && stored.get(i) == storedBefore[i]) {
				returned++;
			}
		}
		Map<String, Integer> after = totals(player);
		RpgTestSupport.result("theSwapIsItsOwnInverse",
			"returned=" + returned + " before=" + before + " after=" + after);
		helper.assertTrue(returned == SIZE, "only " + returned + " of " + SIZE + " slots came back");
		helper.assertTrue(before.equals(after), "before " + before + " after " + after);
		helper.succeed();
	}

	/** The main row: slot i holds the odd item on an even index, so a swap that does nothing is visible. */
	private static ItemStack[] fill(final Inventory row, final Item odd, final Item even) {
		ItemStack[] before = new ItemStack[SIZE];
		for (int i = 0; i < SIZE; i++) {
			before[i] = new ItemStack(i % 2 == 0 ? odd : even, 1 + i);
			row.setItem(i, before[i]);
		}
		return before;
	}

	/** The second row, filled the same way as the main one. */
	private static ItemStack[] fill(final SecondHotbar row, final Item odd, final Item even) {
		ItemStack[] before = new ItemStack[SIZE];
		for (int i = 0; i < SIZE; i++) {
			before[i] = new ItemStack(i % 2 == 0 ? odd : even, 1 + i);
			row.set(i, before[i]);
		}
		return before;
	}

	/** Both rows together hold 90 items, so a lost or duplicated stack is visible. */
	private static Map<String, Integer> totals(final ServerPlayer player) {
		Inventory inventory = player.getInventory();
		SecondHotbar row = SecondHotbar.of(player);
		Map<String, Integer> totals = new HashMap<>();
		for (int i = 0; i < SIZE; i++) {
			count(totals, inventory.getItem(i));
			count(totals, row.get(i));
		}
		return totals;
	}

	private static void count(final Map<String, Integer> totals, final ItemStack stack) {
		if (!stack.isEmpty()) {
			totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
		}
	}
}
