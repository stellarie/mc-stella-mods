package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** The Scroll Book's recipes and its nine-slot menu: only different scrolls, saved on the book, the book locked. */
public final class ScrollBookGameTest {
	private static final int HOTBAR_FIRST = ScrollBookMenu.SIZE + 27;

	@GameTest
	public void bookFromScrolls(final GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getRecipeManager();
		List<String> mismatches = new ArrayList<>();
		for (SpellScrollItem scroll : Spellbooks.SCROLLS) {
			ItemStack s = new ItemStack(scroll);
			CraftingInput input = CraftingInput.of(3, 3, List.of(s, s, s, s, new ItemStack(Items.BOOK), s, s, s, s));
			var match = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
			ItemStack result = match.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
			boolean right = result.getItem() instanceof SpellbookItem book
				&& book.definition().id().equals(scroll.definition().id()) && result.getCount() == 1;
			if (!right) {
				mismatches.add(scroll.definition().id() + " -> " + result);
			}
		}
		log("bookFromScrolls", "scrolls=" + Spellbooks.SCROLLS.size() + " mismatches=" + mismatches);
		helper.assertTrue(mismatches.isEmpty(), "wrong scroll recipes: " + mismatches);
		helper.succeed();
	}

	@GameTest
	public void scrollBookRecipe(final GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getRecipeManager();
		ItemStack l = new ItemStack(Items.LEATHER);
		ItemStack p = new ItemStack(Items.PAPER);
		CraftingInput input = CraftingInput.of(3, 3, List.of(l, p, l, p, new ItemStack(Items.BOOK), p, l, p, l));
		ItemStack result = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
		log("scrollBookRecipe", "result=" + result);
		helper.assertTrue(result.is(Spellbooks.SCROLL_BOOK) && result.getCount() == 1, "the recipe made " + result);
		helper.succeed();
	}

	@GameTest
	public void onlyDifferentScrolls(final GameTestHelper helper) {
		ServerPlayer player = holder(helper);
		ScrollBookMenu menu = open(player);
		place(menu, player, 0, scroll(0, 16));
		place(menu, player, 1, scroll(0, 1));
		place(menu, player, 2, new ItemStack(Items.DIRT));
		boolean duplicateRejected = menu.getSlot(1).getItem().isEmpty();
		boolean dirtRejected = menu.getSlot(2).getItem().isEmpty();
		menu.setCarried(ItemStack.EMPTY);
		for (int i = 1; i < ScrollBookMenu.SIZE; i++) {
			place(menu, player, i, scroll(i, 1));
		}
		int filled = 0;
		for (int i = 0; i < ScrollBookMenu.SIZE; i++) {
			filled += menu.getSlot(i).hasItem() ? 1 : 0;
		}
		log("onlyDifferentScrolls", "duplicateRejected=" + duplicateRejected
			+ " dirtRejected=" + dirtRejected + " filled=" + filled + " first=" + menu.getSlot(0).getItem());
		helper.assertTrue(duplicateRejected && dirtRejected, "a duplicate or a non-scroll entered the book");
		helper.assertTrue(filled == ScrollBookMenu.SIZE && menu.getSlot(0).getItem().getCount() == 16, "nine different scrolls did not fit");
		helper.succeed();
	}

	@GameTest
	public void contentsPersist(final GameTestHelper helper) {
		ServerPlayer player = holder(helper);
		ScrollBookMenu menu = open(player);
		place(menu, player, 3, scroll(2, 7));
		menu.removed(player);
		ItemStack book = player.getMainHandItem();
		long stored = book.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
		ScrollBookMenu again = open(player);
		ItemStack seen = again.getSlot(3).getItem();
		log("contentsPersist", "stored=" + stored + " seen=" + seen);
		helper.assertTrue(stored == 1 && seen.is(scroll(2, 1).getItem()) && seen.getCount() == 7, "the book lost its scrolls: " + seen);
		helper.succeed();
	}

	@GameTest
	public void quickMove(final GameTestHelper helper) {
		ServerPlayer player = holder(helper);
		ScrollBookMenu menu = open(player);
		place(menu, player, 4, scroll(0, 3));
		player.getInventory().setItem(9, scroll(0, 5));
		player.getInventory().setItem(10, new ItemStack(Items.DIRT, 4));
		player.getInventory().setItem(11, scroll(1, 2));
		menu.quickMoveStack(player, ScrollBookMenu.SIZE);
		menu.quickMoveStack(player, ScrollBookMenu.SIZE + 1);
		menu.quickMoveStack(player, ScrollBookMenu.SIZE + 2);
		int merged = menu.getSlot(4).getItem().getCount();
		boolean dirtStayed = player.getInventory().getItem(10).is(Items.DIRT);
		boolean secondInFirstEmpty = menu.getSlot(0).getItem().is(scroll(1, 1).getItem());
		menu.quickMoveStack(player, 4);
		boolean backOut = !menu.getSlot(4).hasItem();
		log("quickMove", "merged=" + merged + " dirtStayed=" + dirtStayed
			+ " secondInFirstEmpty=" + secondInFirstEmpty + " backOut=" + backOut);
		helper.assertTrue(merged == 8 && dirtStayed && secondInFirstEmpty && backOut, "shift-click moved the wrong way");
		helper.succeed();
	}

	@GameTest
	public void bookLocked(final GameTestHelper helper) {
		ServerPlayer player = holder(helper);
		ScrollBookMenu menu = open(player);
		int bookSlot = HOTBAR_FIRST + player.getInventory().getSelectedSlot();
		menu.clicked(bookSlot, 0, ContainerInput.PICKUP, player);
		boolean notPicked = menu.getCarried().isEmpty() && menu.getSlot(bookSlot).getItem().is(Spellbooks.SCROLL_BOOK);
		menu.clicked(bookSlot, 0, ContainerInput.THROW, player);
		boolean notThrown = menu.getSlot(bookSlot).getItem().is(Spellbooks.SCROLL_BOOK);
		menu.clicked(ScrollBookMenu.SIZE, player.getInventory().getSelectedSlot(), ContainerInput.SWAP, player);
		boolean notSwapped = player.getMainHandItem().is(Spellbooks.SCROLL_BOOK);
		boolean validWhileHeld = menu.stillValid(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		boolean closesWhenGone = !menu.stillValid(player);
		log("bookLocked", "notPicked=" + notPicked + " notThrown=" + notThrown
			+ " notSwapped=" + notSwapped + " validWhileHeld=" + validWhileHeld + " closesWhenGone=" + closesWhenGone);
		helper.assertTrue(notPicked && notThrown && notSwapped && validWhileHeld && closesWhenGone, "the open book could move");
		helper.succeed();
	}

	private static void log(final String test, final String result) {
		Spellbooks.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, result);
	}

	private static ServerPlayer holder(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Spellbooks.SCROLL_BOOK));
		return player;
	}

	private static ScrollBookMenu open(final ServerPlayer player) {
		return ScrollBookMenu.open(1, player.getInventory(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
	}

	private static ItemStack scroll(final int index, final int count) {
		return new ItemStack(Spellbooks.SCROLLS.get(index), count);
	}

	private static void place(final ScrollBookMenu menu, final ServerPlayer player, final int slot, final ItemStack stack) {
		menu.setCarried(stack);
		menu.clicked(slot, 0, ContainerInput.PICKUP, player);
	}
}
