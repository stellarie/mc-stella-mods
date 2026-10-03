package com.stella.equips;

import com.stella.equips.compare.GearCompare;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GearCompareGameTest {
	private static String summary(final List<GearCompare.Line> lines) {
		StringBuilder text = new StringBuilder();

		for (GearCompare.Line line : lines) {
			text.append(line.affix() ? "affix:" : "stat:").append(line.key()).append('=').append(line.delta()).append(' ');
		}

		return text.toString().trim();
	}

	private static ItemStack rolled(final ItemStack stack, final Map<String, Integer> stats, final Map<String, Integer> affixes) {
		stack.set(TestItems.STAT_ROLLS, stats);

		if (!affixes.isEmpty()) {
			stack.set(TestItems.AFFIX_ROLLS, affixes);
		}

		return stack;
	}

	@GameTest
	public void diffNamesEachStatAndAffixThatChanged(final GameTestHelper helper) {
		GearCompare.Rolls held = new GearCompare.Rolls(
			Map.of("str", 5, "agi", 2, "luk", 3), Map.of("damage_pct", 30, "damage_flat", 4)
		);
		GearCompare.Rolls equipped = new GearCompare.Rolls(
			Map.of("str", 3, "vit", 4, "luk", 3), Map.of("damage_pct", 10, "hp_flat", 2)
		);
		String result = summary(GearCompare.diff(held, equipped));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=gearCompareDiff RESULT {}", result);
		helper.assertTrue(
			result.equals("affix:damage_flat=4 affix:damage_pct=20 affix:hp_flat=-2 stat:agi=2 stat:str=2 stat:vit=-4"),
			"the diff read: " + result
		);
		helper.succeed();
	}

	@GameTest
	public void anEqualPairHasNoLines(final GameTestHelper helper) {
		GearCompare.Rolls same = new GearCompare.Rolls(Map.of("str", 5), Map.of("hp_pct", 15));
		helper.assertTrue(GearCompare.diff(same, same).isEmpty(), "an equal pair left lines");
		helper.succeed();
	}

	@GameTest
	public void anEmptySlotCountsAsZeroOnEveryRoll(final GameTestHelper helper) {
		GearCompare.Rolls held = new GearCompare.Rolls(Map.of("int", 7), Map.of());
		String result = summary(GearCompare.diff(held, GearCompare.Rolls.EMPTY));
		helper.assertTrue(result.equals("stat:int=7"), "the diff against nothing read: " + result);
		helper.succeed();
	}

	@GameTest
	public void percentAffixesReadInTenthsAndFlatOnesDoNot(final GameTestHelper helper) {
		String percent = GearCompare.amount(new GearCompare.Line(true, "damage_pct", 15));
		String whole = GearCompare.amount(new GearCompare.Line(true, "hp_pct", 30));
		String loss = GearCompare.amount(new GearCompare.Line(true, "armor_pen", -5));
		String flat = GearCompare.amount(new GearCompare.Line(true, "hp_flat", 4));
		String stat = GearCompare.amount(new GearCompare.Line(false, "str", -3));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=gearCompareAmount RESULT {} {} {} {} {}", percent, whole, loss, flat, stat);
		helper.assertTrue(percent.equals("+1.5%"), "15 tenths read " + percent);
		helper.assertTrue(whole.equals("+3%"), "30 tenths read " + whole);
		helper.assertTrue(loss.equals("-0.5%"), "-5 tenths read " + loss);
		helper.assertTrue(flat.equals("+4"), "a flat 4 read " + flat);
		helper.assertTrue(stat.equals("-3"), "a stat of -3 read " + stat);
		helper.succeed();
	}

	@GameTest
	public void theStacksComparePerTheRollComponents(final GameTestHelper helper) {
		ItemStack held = rolled(new ItemStack(Items.DIAMOND_CHESTPLATE), Map.of("vit", 6), Map.of("hp_pct", 25));
		ItemStack equipped = rolled(new ItemStack(Items.IRON_CHESTPLATE), Map.of("vit", 2), Map.of());
		String result = summary(GearCompare.compare(held, equipped));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=gearCompareStacks RESULT {}", result);
		helper.assertTrue(result.equals("affix:hp_pct=25 stat:vit=4"), "the stack compare read: " + result);
		helper.succeed();
	}

	@GameTest
	public void anUnrolledHeldItemHasNothingToCompare(final GameTestHelper helper) {
		ItemStack equipped = rolled(new ItemStack(Items.IRON_SWORD), Map.of("str", 2), Map.of());
		helper.assertTrue(GearCompare.compare(new ItemStack(Items.DIAMOND_SWORD), equipped).isEmpty(), "an unrolled item compared");
		helper.assertTrue(GearCompare.compare(ItemStack.EMPTY, equipped).isEmpty(), "an empty stack compared");
		helper.succeed();
	}

	@GameTest
	public void anUnrolledEquippedItemCountsAsZero(final GameTestHelper helper) {
		ItemStack held = rolled(new ItemStack(Items.DIAMOND_SWORD), Map.of("str", 4), Map.of());
		String result = summary(GearCompare.compare(held, new ItemStack(Items.WOODEN_SWORD)));
		helper.assertTrue(result.equals("stat:str=4"), "the compare read: " + result);
		helper.succeed();
	}

	@GameTest
	public void armorCompareAgainstItsOwnSlotAndWeaponsAgainstTheMainHand(final GameTestHelper helper) {
		EquipmentSlot chest = GearCompare.slotOf(new ItemStack(Items.DIAMOND_CHESTPLATE));
		EquipmentSlot feet = GearCompare.slotOf(new ItemStack(Items.IRON_BOOTS));
		EquipmentSlot sword = GearCompare.slotOf(new ItemStack(Items.DIAMOND_SWORD));
		EquipmentSlot bow = GearCompare.slotOf(new ItemStack(Items.BOW));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=gearCompareSlots RESULT chest={} feet={} sword={} bow={}", chest, feet, sword, bow);
		helper.assertTrue(chest == EquipmentSlot.CHEST, "a chestplate read " + chest);
		helper.assertTrue(feet == EquipmentSlot.FEET, "boots read " + feet);
		helper.assertTrue(sword == EquipmentSlot.MAINHAND, "a sword read " + sword);
		helper.assertTrue(bow == EquipmentSlot.MAINHAND, "a bow read " + bow);
		helper.succeed();
	}
}
