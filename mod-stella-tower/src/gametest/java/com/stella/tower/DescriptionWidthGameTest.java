package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every description line an item of this mod shows is at most 24 characters wide. */
public final class DescriptionWidthGameTest {
	private static final String MOD_ID = "mod-stella-tower";
	private static final int[] FLOORS = {1, 300, 100_000};

	@GameTest
	public void everyRegisteredItemShowsShortDescriptionLines(final GameTestHelper helper) {
		List<String> tooLong = new ArrayList<>();
		int items = 0;
		int shown = 0;
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (!MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			items++;
			List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, new ItemStack(BuiltInRegistries.ITEM.getValue(id))));
			shown += lines.size();
			tooLong.addAll(DescriptionSupport.tooLong(id.toString(), lines));
		}
		TowerTestSupport.result("descriptionWidthItems", "items=" + items + " lines=" + shown + " tooLong=" + tooLong.size());
		helper.assertTrue(shown > 0, "no registered item showed a description line");
		helper.assertTrue(tooLong.isEmpty(), "description lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong);
		helper.succeed();
	}

	/** The rune numbers are widest for high stats and high floors, so the case pins every stat high. */
	@GameTest
	public void runesAndRunedGearShowShortDescriptionLines(final GameTestHelper helper) {
		List<ItemStack> stacks = new ArrayList<>();
		for (TowerElement element : TowerElement.values()) {
			for (int floor : FLOORS) {
				stacks.add(TowerRunes.rune(element, floor));
				stacks.add(TowerRunes.apply(new ItemStack(Items.DIAMOND_SWORD), new RuneData(element, floor)));
				stacks.add(TowerRunes.apply(new ItemStack(Items.DIAMOND_CHESTPLATE), new RuneData(element, floor)));
			}
		}
		stacks.add(GhostRunes.apply(new ItemStack(Items.DIAMOND_CHESTPLATE)));
		stacks.add(GhostRunes.apply(new ItemStack(Items.DIAMOND_SWORD)));
		ItemStack ascended = new ItemStack(Items.DIAMOND_SWORD);
		ascended.set(TowerItems.ASCENDED, new TowerGrowth.Ascension(123_456, 1_000));
		stacks.add(ascended);
		stacks.add(FloorKeyItem.forFloor(123_456));
		stacks.add(PerfectVeil.potion());
		List<String> tooLong = new ArrayList<>();
		int shown = 0;
		RuneStats.pin((entity, stat) -> 1_000);
		try {
			for (ItemStack stack : stacks) {
				helper.assertTrue(!stack.isEmpty(), "a test stack was empty");
				List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, stack));
				shown += lines.size();
				tooLong.addAll(DescriptionSupport.tooLong(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), lines));
			}
		} finally {
			RuneStats.pin(null);
		}
		TowerTestSupport.result("descriptionWidthRunes", "stacks=" + stacks.size() + " lines=" + shown + " tooLong=" + tooLong.size());
		helper.assertTrue(shown > stacks.size(), "the rune stacks showed only " + shown + " lines");
		helper.assertTrue(tooLong.isEmpty(), "description lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong);
		helper.succeed();
	}

	@GameTest
	public void theMerchantShelfFitsAndKeepsTheDescriptionAndPrice(final GameTestHelper helper) {
		List<String> tooLong = new ArrayList<>();
		int goods = 0;
		for (MerchantGoods.Good good : MerchantGoods.stocked()) {
			goods++;
			List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, MerchantShopMenu.shelf(good)));
			tooLong.addAll(DescriptionSupport.tooLong(good.id().toString(), lines));
			String joined = String.join(" ", lines);
			helper.assertTrue(joined.contains(good.price() + " emeralds") && joined.contains("Click to buy"), good.id() + " lost its price lines: " + lines);
			if (good.id().equals(MerchantGoods.SOUL_TONIC.id())) {
				helper.assertTrue(joined.indexOf("Regeneration III") >= 0 && joined.indexOf("Regeneration III") < joined.indexOf("emeralds"),
					"the shelf shows the price above the description: " + lines);
			}
		}
		TowerTestSupport.result("descriptionWidthShelf", "goods=" + goods + " tooLong=" + tooLong.size());
		helper.assertTrue(goods > 0, "the merchant stocked no good");
		helper.assertTrue(tooLong.isEmpty(), "shelf lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong);
		helper.succeed();
	}

	@GameTest
	public void theSplitKeepsTheWordsOfTheKeysAndTheTonic(final GameTestHelper helper) {
		String key = String.join(" ", DescriptionSupport.text(DescriptionSupport.lines(helper, FloorKeyItem.forFloor(7))));
		String tonic = String.join(" ", DescriptionSupport.text(DescriptionSupport.lines(helper, new ItemStack(MerchantItems.SOUL_TONIC))));
		RuneStats.pin((entity, stat) -> 100);
		List<String> sample;
		try {
			sample = DescriptionSupport.text(DescriptionSupport.lines(helper, TowerRunes.rune(TowerElement.FIRE, 300)));
		} finally {
			RuneStats.pin(null);
		}
		String rune = String.join(" ", sample);
		TowerTestSupport.result("descriptionWidthWords", "key=" + key + " tonic=" + tonic + " fireRune=" + sample);
		helper.assertTrue(rune.contains("x1.5 against Ice") && rune.contains("x0.5 against its own element"),
			"the rune lost the opposed and same element numbers: " + sample);
		helper.assertTrue(rune.contains("combusts for 200% of the hit (STR)"), "the fire rune lost its number: " + sample);
		helper.assertTrue(key.contains("Requires: none Desc: Goes to its floor."), "the floor key lost its first sentence: " + key);
		helper.assertTrue(tonic.contains("Heals you in full and grants Regeneration III for 30 seconds."), "the tonic lost its text: " + tonic);
		helper.succeed();
	}

	// An item whose lines all state a floor, a rune name or a price has no description to put in a block.
	private static final java.util.Set<String> STAT_ONLY = java.util.Set.of();

	@GameTest
	public void everyDescribedItemShowsARequiresAndDescBlock(final GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int blocks = 0;
		List<ItemStack> stacks = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (MOD_ID.equals(id.getNamespace()) && !STAT_ONLY.contains(id.getPath())) {
				stacks.add(new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
			}
		}
		for (TowerElement element : TowerElement.values()) {
			stacks.add(TowerRunes.rune(element, 300));
		}
		stacks.add(FloorKeyItem.forFloor(7));
		stacks.add(PerfectVeil.potion());
		RuneStats.pin((entity, stat) -> 1_000);
		try {
			for (ItemStack stack : stacks) {
				List<Component> lines = DescriptionSupport.lines(helper, stack);
				String owner = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
				if (lines.isEmpty()) {
					continue;
				}
				blocks++;
				problems.addAll(DescriptionSupport.blockProblems(owner, lines));
				if (!DescriptionSupport.hasBlock(lines)) {
					problems.add(owner + " has no Requires and Desc block: " + DescriptionSupport.text(lines));
				}
			}
			for (MerchantGoods.Good good : MerchantGoods.stocked()) {
				// A good from another mod that tells nothing of itself has only its price on the shelf.
				if (DescriptionSupport.lines(helper, good.shown().get()).isEmpty()) {
					continue;
				}
				List<Component> lines = DescriptionSupport.lines(helper, MerchantShopMenu.shelf(good));
				blocks++;
				problems.addAll(DescriptionSupport.blockProblems("shelf " + good.id(), lines));
				if (!DescriptionSupport.hasBlock(lines)) {
					problems.add("shelf " + good.id() + " has no Requires and Desc block: " + DescriptionSupport.text(lines));
				}
			}
		} finally {
			RuneStats.pin(null);
		}
		TowerTestSupport.result("descriptionBlocks", "blocks=" + blocks + " problems=" + problems.size());
		helper.assertTrue(blocks >= 8, "only " + blocks + " described stacks were checked");
		helper.assertTrue(problems.isEmpty(), "description blocks out of format: " + problems);
		helper.succeed();
	}
}
