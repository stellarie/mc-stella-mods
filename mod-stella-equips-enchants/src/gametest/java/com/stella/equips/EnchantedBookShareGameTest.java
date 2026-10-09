package com.stella.equips;

import com.stella.equips.loot.EnchantedBooks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** The enchanted-book share: the mobs mod asks for a book with a count of enchantments and reads it back. */
public final class EnchantedBookShareGameTest {
	private static final int DRAWS = 300;

	@SuppressWarnings("unchecked")
	private static Function<Object[], ItemStack> share() {
		Object found = FabricLoader.getInstance().getObjectShare().get(EnchantedBooks.SHARE);
		return found instanceof Function<?, ?> function ? (Function<Object[], ItemStack>) function : null;
	}

	private static ItemEnchantments stored(final ItemStack book) {
		return book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	private static List<String> faults(final ItemStack book, final int count) {
		List<String> faults = new ArrayList<>();
		ItemEnchantments enchantments = stored(book);
		if (!book.is(Items.ENCHANTED_BOOK)) {
			faults.add("not an enchanted book: " + book);
		}
		if (enchantments.size() != count) {
			faults.add("holds " + enchantments.size() + " not " + count);
		}
		List<Holder<Enchantment>> held = new ArrayList<>(enchantments.keySet());
		for (int i = 0; i < held.size(); i++) {
			Holder<Enchantment> enchantment = held.get(i);
			int level = enchantments.getLevel(enchantment);
			if (enchantment.is(EnchantmentTags.CURSE)) {
				faults.add("curse " + enchantment.getRegisteredName());
			}
			if (enchantment.getRegisteredName().contains("-test-")) {
				faults.add("test fixture " + enchantment.getRegisteredName());
			}
			if (level < enchantment.value().getMinLevel() || level > enchantment.value().getMaxLevel()) {
				faults.add(enchantment.getRegisteredName() + " at level " + level);
			}
			for (int j = i + 1; j < held.size(); j++) {
				if (!Enchantment.areCompatible(enchantment, held.get(j))) {
					faults.add("conflict " + enchantment.getRegisteredName() + " " + held.get(j).getRegisteredName());
				}
			}
		}
		return faults;
	}

	private static List<String> draw(final GameTestHelper helper, final int count, final long seed) {
		Function<Object[], ItemStack> share = share();
		List<String> faults = new ArrayList<>();
		RandomSource random = RandomSource.create(seed);
		for (int i = 0; i < DRAWS; i++) {
			ItemStack book = share.apply(new Object[] {helper.getLevel(), random, count});
			faults.addAll(faults(book, count));
		}
		return faults;
	}

	@GameTest
	public void theShareIsPublishedAsAFunction(final GameTestHelper helper) {
		StellaEquips.LOGGER.info("[BEHAVIOR] test=theShareIsPublishedAsAFunction RESULT share={}", share() != null);
		helper.assertTrue(share() != null, "no Function under " + EnchantedBooks.SHARE);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void twoEnchantmentBooksAreValid(final GameTestHelper helper) {
		helper.assertTrue(share() != null, "no Function under " + EnchantedBooks.SHARE);
		List<String> faults = draw(helper, 2, 11L);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=twoEnchantmentBooksAreValid RESULT draws={} faults={}", DRAWS, faults.size());
		helper.assertTrue(faults.isEmpty(), "faults: " + faults.stream().limit(5).toList());
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void threeEnchantmentBooksAreValid(final GameTestHelper helper) {
		helper.assertTrue(share() != null, "no Function under " + EnchantedBooks.SHARE);
		List<String> faults = draw(helper, 3, 12L);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=threeEnchantmentBooksAreValid RESULT draws={} faults={}", DRAWS, faults.size());
		helper.assertTrue(faults.isEmpty(), "faults: " + faults.stream().limit(5).toList());
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void thePoolHoldsVanillaAndTheEquipsCatalogue(final GameTestHelper helper) {
		helper.assertTrue(share() != null, "no Function under " + EnchantedBooks.SHARE);
		RandomSource random = RandomSource.create(13L);
		int vanilla = 0;
		int equips = 0;
		for (int i = 0; i < DRAWS * 3; i++) {
			ItemStack book = share().apply(new Object[] {helper.getLevel(), random, 3});
			for (Holder<Enchantment> enchantment : stored(book).keySet()) {
				String name = enchantment.getRegisteredName();
				vanilla += name.startsWith("minecraft:") ? 1 : 0;
				equips += name.startsWith(StellaEquips.MOD_ID + ":") ? 1 : 0;
			}
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=thePoolHoldsVanillaAndTheEquipsCatalogue RESULT vanilla={} equips={}", vanilla, equips);
		helper.assertTrue(vanilla > 0 && equips > 0, "vanilla=" + vanilla + " equips=" + equips);
		helper.succeed();
	}

	@GameTest
	public void badInputGivesAnEmptyStackAndNeverThrows(final GameTestHelper helper) {
		helper.assertTrue(share() != null, "no Function under " + EnchantedBooks.SHARE);
		RandomSource random = RandomSource.create(14L);
		Object[][] bad = {
			null,
			{},
			{helper.getLevel(), random},
			{"level", random, 2},
			{helper.getLevel(), "random", 2},
			{helper.getLevel(), random, "two"},
			{helper.getLevel(), random, 0},
			{helper.getLevel(), random, -3},
		};
		int notEmpty = 0;
		for (Object[] args : bad) {
			ItemStack result = share().apply(args);
			notEmpty += result == null || !result.isEmpty() ? 1 : 0;
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=badInputGivesAnEmptyStackAndNeverThrows RESULT cases={} notEmpty={}", bad.length, notEmpty);
		helper.assertTrue(notEmpty == 0, notEmpty + " bad inputs gave a book");
		helper.succeed();
	}
}
