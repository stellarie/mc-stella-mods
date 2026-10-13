package com.stella.spells;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clue books: three vanilla chests in ten hold a tattered note that teaches one spell's book and scroll,
 * naming its material, and now and then the Scroll of Return. Every spell has its note.
 */
public class ClueBooksGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.clues");
	private static final int ROLLS = 100_000;
	private static final Identifier CHEST = Identifier.parse("minecraft:chests/simple_dungeon");

	@GameTest
	public void threeChestsInTenHoldAClueBook(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20_260_927L);
		int clues = 0;
		Set<Identifier> taught = new HashSet<>();
		for (int i = 0; i < ROLLS; i++) {
			List<ItemStack> drops = new ArrayList<>();
			SpellbookLoot.modifyChestDrops(CHEST, true, random, drops);
			for (ItemStack drop : drops) {
				Identifier teaches = ClueBooks.teaches(drop);
				if (teaches != null) {
					clues++;
					taught.add(teaches);
				}
			}
		}
		double rate = (double) clues / ROLLS;
		double band = 4.0 * Math.sqrt(ClueBooks.CLUE_CHEST_CHANCE * (1.0 - ClueBooks.CLUE_CHEST_CHANCE) / ROLLS);
		LOG.info("[SPELLTEST] test=threeChestsInTenHoldAClueBook RESULT rolls={} rate={} chance={} taught={}", ROLLS, rate, ClueBooks.CLUE_CHEST_CHANCE, taught.size());
		helper.assertTrue(Math.abs(rate - ClueBooks.CLUE_CHEST_CHANCE) <= band, "clue books came in " + rate + " of chests, not " + ClueBooks.CLUE_CHEST_CHANCE);
		helper.assertTrue(taught.size() == SpellbookCatalog.DEFINITIONS.size() + 1, "the chests taught " + taught.size() + " things, not every spell and the return scroll");
		helper.succeed();
	}

	@GameTest
	public void everySpellsClueNamesItsMaterialAndBothRecipes(final GameTestHelper helper) {
		List<Identifier> missing = new ArrayList<>();
		for (SpellDefinition spell : SpellbookCatalog.DEFINITIONS) {
			ItemStack clue = ClueBooks.forSpell(spell, RandomSource.create(spell.id().hashCode()));
			WrittenBookContent content = clue.get(DataComponents.WRITTEN_BOOK_CONTENT);
			Set<String> keys = translationKeys(content.getPages(false));
			String text = String.join(" ", content.getPages(false).stream().map(Component::getString).toList());
			boolean names = keys.contains(spell.ingredient().getDescriptionId())
				&& keys.contains("item." + spell.id().getNamespace() + "." + spell.id().getPath() + ".desc")
				&& text.contains("Book") && text.contains("Paper") && content.title().raw().contains(ClueBooks.displayName(spell.id()));
			if (!names || !spell.id().equals(ClueBooks.teaches(clue))) {
				missing.add(spell.id());
			}
		}
		LOG.info("[SPELLTEST] test=everySpellsClueNamesItsMaterialAndBothRecipes RESULT spells={} missing={}", SpellbookCatalog.DEFINITIONS.size(), missing);
		helper.assertTrue(missing.isEmpty(), "these clues miss their material, a recipe or their spell: " + missing);
		helper.succeed();
	}

	@GameTest
	public void theReturnScrollsClueNamesItsThreeIngredients(final GameTestHelper helper) {
		ItemStack clue = ClueBooks.forReturnScroll(RandomSource.create(7L));
		Set<String> keys = translationKeys(clue.get(DataComponents.WRITTEN_BOOK_CONTENT).getPages(false));
		LOG.info("[SPELLTEST] test=theReturnScrollsClueNamesItsThreeIngredients RESULT keys={}", keys);
		helper.assertTrue(keys.contains(Items.PAPER.getDescriptionId()) && keys.contains(Items.ENDER_PEARL.getDescriptionId())
			&& keys.contains(Items.FEATHER.getDescriptionId()), "the return clue names " + keys);
		helper.assertTrue(Spellbooks.SCROLL_OF_RETURN_ID.equals(ClueBooks.teaches(clue)), "the return clue teaches " + ClueBooks.teaches(clue));
		helper.succeed();
	}

	@GameTest
	public void aClueBookIsATatteredWrittenBook(final GameTestHelper helper) {
		ItemStack clue = ClueBooks.roll(RandomSource.create(3L));
		WrittenBookContent content = clue.get(DataComponents.WRITTEN_BOOK_CONTENT);
		LOG.info("[SPELLTEST] test=aClueBookIsATatteredWrittenBook RESULT item={} generation={} pages={} author={}", clue.getItem(), content.generation(), content.pages().size(), content.author());
		helper.assertTrue(clue.is(Items.WRITTEN_BOOK) && content.generation() == 3 && content.pages().size() >= 3 && !content.author().isBlank(), "the clue is " + clue + " " + content);
		helper.succeed();
	}

	private static Set<String> translationKeys(final List<Component> pages) {
		Set<String> keys = new HashSet<>();
		for (Component page : pages) {
			page.visit((style, text) -> java.util.Optional.empty(), net.minecraft.network.chat.Style.EMPTY);
			collect(page, keys);
		}
		return keys;
	}

	private static void collect(final Component component, final Set<String> keys) {
		if (component.getContents() instanceof TranslatableContents translatable) {
			keys.add(translatable.getKey());
		}
		component.getSiblings().forEach(sibling -> collect(sibling, keys));
	}
}
