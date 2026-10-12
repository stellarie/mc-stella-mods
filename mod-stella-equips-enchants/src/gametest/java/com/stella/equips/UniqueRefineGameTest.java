package com.stella.equips;

import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineSpecials;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueTooltip;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

/** Refining a unique: no generic specials, the generic numbers still apply, and the tooltip tells the effect and its four tiers. */
public class UniqueRefineGameTest {
	private static final TextColor GOLD = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
	private static final TextColor LOCKED = TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY);

	@GameTest
	public void aUniqueHasNoGenericSpecials(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ItemStack plainBow = UniqueTestKit.stack(Items.BOW, Refine.MAX_LEVEL);
		helper.assertTrue(!RefineSpecials.of(plainBow, Refine.MAX_LEVEL).isEmpty(), "a plain bow at +15 has no specials: the check cannot fail");
		int checked = 0;
		for (Item item : List.of(Uniques.item(def), UniqueFixtures.sword, UniqueFixtures.helmet, UniqueFixtures.boots, UniqueFixtures.crossbow)) {
			for (int level : List.of(1, Refine.KEEN_LEVEL, Refine.SEARING_LEVEL, Refine.RENDING_LEVEL, Refine.ASCENDED_LEVEL)) {
				ItemStack stack = UniqueTestKit.stack(item, level);
				helper.assertTrue(RefineSpecials.of(stack, level).isEmpty(), item + " has specials " + RefineSpecials.of(stack, level) + " at +" + level);
				helper.assertTrue(Refine.effects(stack, level).isEmpty(), item + " lists effects at +" + level);
				List<String> lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
				helper.assertTrue(lore.size() == 1 && lore.get(0).equals("Refine +" + level), item + " lore at +" + level + ": " + lore);
				checked++;
			}
		}
		UniqueTestKit.result("refineNoSpecials", "checked=" + checked + " plainBowSpecials=" + RefineSpecials.of(plainBow, Refine.MAX_LEVEL));
		helper.succeed();
	}

	@GameTest
	public void genericRefineNumbersStillApply(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ItemStack bow = UniqueTestKit.stack(def, Refine.MAX_LEVEL);
		ItemStack plainBow = UniqueTestKit.stack(Items.BOW, Refine.MAX_LEVEL);
		int expectedCap = bow.getItem().components().getOrDefault(DataComponents.MAX_DAMAGE, 0) + Refine.DURABILITY_PER_LEVEL * Refine.MAX_LEVEL;
		ItemStack sword = UniqueTestKit.stack(UniqueFixtures.sword, Refine.SEARING_LEVEL);
		ItemStack diamond = UniqueTestKit.stack(Items.DIAMOND_SWORD, Refine.SEARING_LEVEL);
		double swordDamage = attack(sword);
		double diamondDamage = attack(diamond);
		ItemStack helmet = UniqueTestKit.stack(UniqueFixtures.helmet, Refine.SEARING_LEVEL);
		ItemStack diamondHelmet = UniqueTestKit.stack(Items.DIAMOND_HELMET, Refine.SEARING_LEVEL);
		UniqueTestKit.result("refineGeneric", "bowCap=" + bow.getMaxDamage() + " expected=" + expectedCap + " plainBowCap=" + plainBow.getMaxDamage()
			+ " sword=" + swordDamage + " diamond=" + diamondDamage + " helmet=" + armor(helmet) + " diamondHelmet=" + armor(diamondHelmet));
		helper.assertTrue(bow.getMaxDamage() == expectedCap, "the unique bow cap is " + bow.getMaxDamage() + ", expected " + expectedCap);
		helper.assertTrue(Math.abs(swordDamage - diamondDamage) < 1.0E-9, "a refined unique sword hits " + swordDamage + " against the diamond sword's " + diamondDamage);
		helper.assertTrue(Refine.level(sword) == Refine.SEARING_LEVEL, "the level is " + Refine.level(sword));
		helper.assertTrue(Math.abs(armor(helmet) - armor(diamondHelmet)) < 1.0E-9, "a refined unique helmet gives " + armor(helmet) + " armor against " + armor(diamondHelmet));
		helper.assertTrue(Refine.levelAfterFailure(Refine.ASCENDED_LEVEL) == Refine.ASCENDED_LEVEL - 1 && Refine.levelAfterFailure(Refine.RENDING_LEVEL) == Refine.RENDING_LEVEL,
			"the milestone floors changed");
		helper.succeed();
	}

	@GameTest
	public void theTooltipTellsTheEffectAndItsFourTiers(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		int[] gates = {0, 6, 7, 9, 10, 11, 12, 14, 15};
		for (int level : gates) {
			List<Component> lines = UniqueTooltip.lines(UniqueTestKit.stack(def, level));
			helper.assertTrue(lines.size() == 7, "the tooltip at +" + level + " has " + lines.size() + " lines: " + lines);
			helper.assertTrue(lines.get(0).getString().equals("Job: " + def.job().title()), "job line: " + lines.get(0).getString());
			String drops = lines.get(1).getString();
			helper.assertTrue(drops.startsWith("Drops from: ") && (drops.contains("Skeleton") || drops.contains("entity.minecraft.skeleton")), "drops line: " + drops);
			helper.assertTrue(lines.get(2).getString().contains(def.base()), "base line: " + lines.get(2).getString());
			helper.assertTrue(GOLD.equals(lines.get(2).getStyle().getColor()), "the base line is not gold");
			String[] texts = {def.plus7(), def.plus10(), def.plus12(), def.plus15()};
			String[] marks = {"+7", "+10", "+12", "+15"};
			int[] tiers = {Uniques.PLUS_7, Uniques.PLUS_10, Uniques.PLUS_12, Uniques.PLUS_15};
			for (int tier = 0; tier < 4; tier++) {
				Component line = lines.get(3 + tier);
				String text = line.getString();
				helper.assertTrue(text.startsWith(marks[tier] + ": ") && text.endsWith(texts[tier]), "tier line " + tier + " at +" + level + ": " + text);
				boolean active = level >= tiers[tier];
				helper.assertTrue((active ? GOLD : LOCKED).equals(line.getStyle().getColor()), "tier " + marks[tier] + " at +" + level + " should be "
					+ (active ? "gold" : "dark gray") + ": " + line.getStyle().getColor());
			}
		}
		UniqueTestKit.result("refineTooltip", "levels=" + gates.length);
		helper.succeed();
	}

	@GameTest
	public void theItemTooltipCarriesTheUniqueLines(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ItemStack stack = UniqueTestKit.stack(def, Uniques.PLUS_10);
		List<String> lines = stack.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
		List<String> expected = UniqueTooltip.lines(stack).stream()
			.flatMap(line -> com.stella.equips.tooltip.DescriptionLines.wrap(line).stream()).map(Component::getString).toList();
		int first = lines.indexOf(expected.get(0));
		helper.assertTrue(first >= 0, "the job line is not in the item tooltip: " + lines);
		helper.assertTrue(lines.subList(first, Math.min(lines.size(), first + expected.size())).equals(expected), "the unique lines are not in order: " + lines);
		helper.assertTrue(lines.stream().noneMatch(line -> line.startsWith("Keen:") || line.startsWith("Piercing:") || line.startsWith("Volley:")), "a generic special shows: " + lines);
		long once = lines.stream().filter(line -> line.equals(expected.get(0))).count();
		helper.assertTrue(once == 1, "the job line shows " + once + " times");
		UniqueTestKit.result("refineItemTooltip", "lines=" + lines.size());
		helper.succeed();
	}

	private static double attack(final ItemStack stack) {
		return sum(stack, Attributes.ATTACK_DAMAGE);
	}

	private static double armor(final ItemStack stack) {
		return sum(stack, Attributes.ARMOR);
	}

	private static double sum(final ItemStack stack, final Holder<Attribute> attribute) {
		double sum = 0.0;
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.attribute().equals(attribute)) {
				sum += entry.modifier().amount();
			}
		}
		return sum;
	}
}
