package com.stella.spells;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpellDescriptionsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.descriptions");
	private static final int REQUIRES_LINE = 0;
	private static final int DESC_LINE = 1;
	private static final int COOLDOWN_LINE = 2;
	private static final int DESCRIPTION_LINES = 3;
	/** The books that take no bow even though they belong to the archer school. */
	private static final Set<String> NO_BOW = Set.of("arrow_crafting", "hunters_mark", "summon_wolf");

	@GameTest
	public void everySpellbookDescribesItsRequirementEffectAndCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everySpellbookDescribesItsRequirementEffectAndCooldown");
		int described = 0;
		for (SpellbookItem book : Spellbooks.CATALOG) {
			List<Component> lines = lines(book);
			int expected = DESCRIPTION_LINES + (SpellCombos.involving(book.definition().id()).isPresent() ? 1 : 0);
			helper.assertTrue(lines.size() == expected, book.definition().id() + " has " + lines.size() + " description lines, not " + expected);
			for (Component line : lines) {
				helper.assertTrue(translated(line), book.definition().id() + " shows an untranslated line: " + line.getString());
			}
			helper.assertTrue(shown(lines.get(REQUIRES_LINE)).startsWith("Requires: ") && shown(lines.get(DESC_LINE)).startsWith("Desc: "),
				book.definition().id() + " does not open with Requires and Desc: " + shown(lines.get(REQUIRES_LINE)));
			described++;
		}
		LOG.info("[SPELLTEST] test=everySpellbookDescribesItsRequirementEffectAndCooldown RESULT described={}", described);
		helper.succeed();
	}

	/** An archer book needs a bow or a crossbow, except three; Shield Bash needs a shield; every other book needs nothing. */
	@GameTest
	public void theRequiresLineFollowsWhatTheBookNeeds(final GameTestHelper helper) {
		SpellTestLog.track(helper, "theRequiresLineFollowsWhatTheBookNeeds");
		Map<String, Integer> kinds = new HashMap<>();
		for (SpellbookItem book : Spellbooks.CATALOG) {
			String path = book.definition().id().getPath();
			String requires = shown(lines(book).get(REQUIRES_LINE));
			boolean bow = book.definition().school() == School.ARCHER && !NO_BOW.contains(path);
			if (bow) {
				helper.assertTrue(requires.startsWith("Requires: bow or crossbow"), path + " needs a bow or a crossbow: " + requires);
			} else if (path.equals("shield_bash")) {
				helper.assertTrue(requires.contains("shield"), "Shield Bash needs a shield: " + requires);
			} else if (path.equals("arrow_crafting")) {
				helper.assertTrue(requires.contains("arrow material"), "Arrow Crafting needs a material: " + requires);
			} else {
				helper.assertTrue(requires.equals("Requires: none"), path + " needs nothing: " + requires);
			}
			kinds.merge(requires, 1, Integer::sum);
		}
		LOG.info("[SPELLTEST] test=theRequiresLineFollowsWhatTheBookNeeds RESULT kinds={}", kinds);
		helper.succeed();
	}

	@GameTest
	public void everySpellbookDescribesADifferentEffect(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everySpellbookDescribesADifferentEffect");
		Set<String> effects = new HashSet<>();
		for (SpellbookItem book : Spellbooks.CATALOG) {
			helper.assertTrue(effects.add(shown(lines(book).get(DESC_LINE))), book.definition().id() + " repeats another book's effect text");
		}
		LOG.info("[SPELLTEST] test=everySpellbookDescribesADifferentEffect RESULT distinct={}", effects.size());
		helper.succeed();
	}

	@GameTest
	public void cooldownLineCarriesTheBaseCooldownInSeconds(final GameTestHelper helper) {
		SpellTestLog.track(helper, "cooldownLineCarriesTheBaseCooldownInSeconds");
		Map<Integer, String> textBySeconds = new HashMap<>();
		for (SpellbookItem book : Spellbooks.CATALOG) {
			Component line = lines(book).get(COOLDOWN_LINE);
			int seconds = book.definition().baseCooldownTicks() / 20;
			helper.assertTrue(line.getContents() instanceof TranslatableContents, book.definition().id() + " cooldown line is not translatable");
			Object[] args = ((TranslatableContents) line.getContents()).getArgs();
			helper.assertTrue(args.length == 1 && Integer.valueOf(seconds).equals(args[0]), book.definition().id() + " cooldown argument is not " + seconds);
			String text = textBySeconds.computeIfAbsent(seconds, key -> line.getString());
			helper.assertTrue(text.equals(line.getString()), book.definition().id() + " cooldown text differs at equal seconds");
		}
		helper.assertTrue(new HashSet<>(textBySeconds.values()).size() == textBySeconds.size(), "two cooldowns render the same text");
		LOG.info("[SPELLTEST] test=cooldownLineCarriesTheBaseCooldownInSeconds RESULT cooldowns={}", textBySeconds.size());
		helper.succeed();
	}

	@GameTest
	public void everyComboBookNamesItsCombo(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everyComboBookNamesItsCombo");
		int named = 0;
		for (SpellCombos.Combo combo : SpellCombos.combos()) {
			for (SpellbookItem book : Spellbooks.CATALOG) {
				if (!book.definition().id().equals(combo.first()) && !book.definition().id().equals(combo.second())) {
					continue;
				}
				List<Component> lines = lines(book);
				Component last = lines.getLast();
				helper.assertTrue(translated(last) && shown(last).contains(combo.name()), book.definition().id() + " does not name " + combo.name() + ": " + shown(last));
				named++;
			}
		}
		helper.assertTrue(named == SpellCombos.combos().size() * 2, "only " + named + " books name their combo");
		LOG.info("[SPELLTEST] test=everyComboBookNamesItsCombo RESULT books={}", named);
		helper.succeed();
	}

	private static List<Component> lines(final SpellbookItem book) {
		ItemStack stack = book.getDefaultInstance();
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines();
	}

	/** The text of a line with the shipped English installed. */
	private static String shown(final Component line) {
		return DescriptionSupport.text(List.of(line)).get(0);
	}

	// A line is translated when its translatable text renders as something other than its own key.
	private static boolean translated(final Component line) {
		TranslatableContents contents;
		try {
			contents = DescriptionSupport.translatable(line);
		} catch (IllegalStateException none) {
			return false;
		}
		String text = shown(line);
		return !text.isBlank() && !text.contains(contents.getKey());
	}
}
