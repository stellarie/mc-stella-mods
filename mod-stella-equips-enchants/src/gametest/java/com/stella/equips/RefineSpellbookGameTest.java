package com.stella.equips;

import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineSpecials;
import com.stella.equips.refine.RefineStation;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A spellbook refines, with amethyst shards, as a grade 3 weapon. The equips mod finds it by its class name,
 * so the test mod carries a stand-in of that name. The mod holds no compile dependency on the spells mod.
 */
public class RefineSpellbookGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.refine-spellbook");
	private static final String SPELLBOOK_CLASS = "com.stella.spells.SpellbookItem";

	static {
		RefineRolls.install();
	}

	@GameTest
	public void spellbookRefines(final GameTestHelper helper) {
		ItemStack book = new ItemStack(TestItems.BOOK);
		helper.assertTrue(Refine.isSpellbook(book) && Refine.canRefine(book), "a one-stack item of the spellbook class is not refinable");
		helper.assertTrue(book.getItem().getClass().getName().equals(SPELLBOOK_CLASS), "the stand-in has the wrong class name");
		helper.assertTrue(Refine.material(book) == Items.AMETHYST_SHARD && Refine.isMaterial(book, new ItemStack(Items.AMETHYST_SHARD)) && !Refine.isMaterial(book, new ItemStack(Items.IRON_INGOT)),
			"a spellbook's material is " + Refine.material(book));
		helper.assertTrue(Refine.grade(book) == Refine.Grade.WEAPON_3, "a spellbook's grade is " + Refine.grade(book));

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.getInventory().add(new ItemStack(Items.AMETHYST_SHARD, 10));
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		player.containerMenu = menu;
		menu.getSlot(0).set(book);
		RefineStation.Result result = RefineStation.refine(player, menu.containerId);
		ItemStack refined = menu.getSlot(0).getItem();
		ItemAttributeModifiers modifiers = refined.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		LOG.info("[BEHAVIOR] test=spellbookRefines RESULT result={} level={} shards={} modifiers={}", result, Refine.level(refined), player.getInventory().countItem(Items.AMETHYST_SHARD), modifiers.modifiers());
		helper.assertTrue(result == RefineStation.Result.SUCCESS && Refine.level(refined) == 1, "refining the book gave " + result + " +" + Refine.level(refined));
		helper.assertTrue(player.getInventory().countItem(Items.AMETHYST_SHARD) == 10 - Refine.cost(0), "the step took " + (10 - player.getInventory().countItem(Items.AMETHYST_SHARD)) + " shards");
		helper.assertTrue(modifiers.modifiers().isEmpty(), "a refined book carries attribute modifiers: " + modifiers.modifiers());
		helper.assertTrue(refined.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getIntOr(Refine.LEVEL_KEY, -1) == 1,
			"the level is not in the " + Refine.LEVEL_KEY + " key");

		ItemStack high = new ItemStack(TestItems.BOOK);
		Refine.setLevel(high, Refine.MAX_LEVEL);
		helper.assertTrue(Refine.level(high) == Refine.MAX_LEVEL && high.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty(),
			"a +15 book has modifiers");
		helper.assertTrue(RefineSpecials.of(high, Refine.MAX_LEVEL).isEmpty() && Refine.effects(high, Refine.MAX_LEVEL).isEmpty(), "a +15 book has specials: " + RefineSpecials.of(high, Refine.MAX_LEVEL));
		helper.succeed();
	}

	@GameTest
	public void aStackingItemOfTheSameClassIsNotABook(final GameTestHelper helper) {
		ItemStack scroll = new ItemStack(TestItems.STACKING);
		LOG.info("[BEHAVIOR] test=aStackingItemOfTheSameClassIsNotABook RESULT maxStack={} refinable={}", scroll.getMaxStackSize(), Refine.canRefine(scroll));
		helper.assertTrue(scroll.getMaxStackSize() == 16 && !Refine.isSpellbook(scroll) && !Refine.canRefine(scroll), "a stacking item of the book's class counts as a book");
		helper.succeed();
	}

	@GameTest
	public void noSpellsModNoBooks(final GameTestHelper helper) {
		List<String> books = new ArrayList<>();
		int oneStacks = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.getMaxStackSize() == 1 && !item.getClass().getName().equals(SPELLBOOK_CLASS)) {
				oneStacks++;
				if (Refine.isSpellbook(stack)) {
					books.add(BuiltInRegistries.ITEM.getKey(item).toString());
				}
			}
		}
		ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
		LOG.info("[BEHAVIOR] test=noSpellsModNoBooks RESULT oneStackItems={} counted={}", oneStacks, books);
		helper.assertTrue(oneStacks > 0, "no one-stack item was checked, so the case proves nothing");
		helper.assertTrue(books.isEmpty(), "items counted as spellbooks without the spells class: " + books);
		helper.assertTrue(!Refine.isSpellbook(enchantedBook) && !Refine.canRefine(enchantedBook) && !Refine.isSpellbook(ItemStack.EMPTY), "an enchanted book or nothing counts as a spellbook");
		helper.succeed();
	}
}
