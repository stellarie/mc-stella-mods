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
	private static final int TARGET_LINE = 0;
	private static final int USE_LINE = 1;
	private static final int EFFECT_LINE = 2;
	private static final int COOLDOWN_LINE = 3;
	private static final int DESCRIPTION_LINES = 4;

	@GameTest
	public void everySpellbookDescribesItsTargetUseEffectAndCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everySpellbookDescribesItsTargetUseEffectAndCooldown");
		int described = 0;
		for (SpellbookItem book : Spellbooks.CATALOG) {
			List<Component> lines = lines(book);
			int expected = DESCRIPTION_LINES + (SpellCombos.involving(book.definition().id()).isPresent() ? 1 : 0);
			helper.assertTrue(lines.size() == expected, book.definition().id() + " has " + lines.size() + " description lines, not " + expected);
			for (Component line : lines) {
				helper.assertTrue(translated(line), book.definition().id() + " shows an untranslated line: " + line.getString());
			}
			described++;
		}
		LOG.info("[SPELLTEST] test=everySpellbookDescribesItsTargetUseEffectAndCooldown RESULT described={}", described);
		helper.succeed();
	}

	@GameTest
	public void targetAndUseLinesFollowTheTargetMode(final GameTestHelper helper) {
		SpellTestLog.track(helper, "targetAndUseLinesFollowTheTargetMode");
		Map<String, String> targetByKey = new HashMap<>();
		Map<String, String> useByKey = new HashMap<>();
		for (SpellbookItem book : Spellbooks.CATALOG) {
			String key = lineKey(book);
			List<Component> lines = lines(book);
			String target = targetByKey.computeIfAbsent(key, ignored -> lines.get(TARGET_LINE).getString());
			String use = useByKey.computeIfAbsent(key, ignored -> lines.get(USE_LINE).getString());
			helper.assertTrue(target.equals(lines.get(TARGET_LINE).getString()), book.definition().id() + " target line differs from its mode");
			helper.assertTrue(use.equals(lines.get(USE_LINE).getString()), book.definition().id() + " use line differs from its mode");
		}
		// An archer skill that aims reaches further than a spell, so its target line is its own.
		int modes = TargetMode.values().length + 2;
		helper.assertTrue(targetByKey.size() == modes, "not every target mode has a book: " + targetByKey.keySet());
		helper.assertTrue(new HashSet<>(targetByKey.values()).size() == modes, "two target modes share a target line");
		helper.assertTrue(new HashSet<>(useByKey.values()).size() == TargetMode.values().length, "two target modes share a use line");
		for (String archer : List.of(TargetMode.HOSTILE + ".archer", TargetMode.BLOCK + ".archer")) {
			helper.assertTrue(targetByKey.get(archer).contains(Integer.toString(ArcherSkills.SKILL_RANGE_BLOCKS)),
				archer + " does not state the " + ArcherSkills.SKILL_RANGE_BLOCKS + " block reach: " + targetByKey.get(archer));
		}
		LOG.info("[SPELLTEST] test=targetAndUseLinesFollowTheTargetMode RESULT keys={}", targetByKey.size());
		helper.succeed();
	}

	private static String lineKey(final SpellbookItem book) {
		TargetMode mode = book.definition().targetMode();
		boolean archerReach = book.definition().school() == School.ARCHER && (mode == TargetMode.HOSTILE || mode == TargetMode.BLOCK);
		return mode + (archerReach ? ".archer" : "");
	}

	@GameTest
	public void everySpellbookDescribesADifferentEffect(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everySpellbookDescribesADifferentEffect");
		Set<String> effects = new HashSet<>();
		for (SpellbookItem book : Spellbooks.CATALOG) {
			helper.assertTrue(effects.add(lines(book).get(EFFECT_LINE).getString()), book.definition().id() + " repeats another book's effect text");
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
				helper.assertTrue(translated(last) && last.getString().contains(combo.name()), book.definition().id() + " does not name " + combo.name() + ": " + last.getString());
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

	// A line is translated when it renders as something other than its own key.
	private static boolean translated(final Component line) {
		if (!(line.getContents() instanceof TranslatableContents contents)) {
			return false;
		}
		String text = line.getString();
		return !text.isBlank() && !text.equals(contents.getKey());
	}
}
