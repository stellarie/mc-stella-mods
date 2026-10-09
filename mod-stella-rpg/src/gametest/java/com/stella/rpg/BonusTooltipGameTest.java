package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.BonusTooltip;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The tooltip rules for material bonuses. The server side holds the rules, and the client only prints them. */
public final class BonusTooltipGameTest {
	private static Affix affix(final String key) {
		return Affix.byKey(key).orElseThrow();
	}

	@GameTest
	public void materialAffixLinesNameTheirKind(final GameTestHelper helper) {
		ItemStack ingot = BonusTestSupport.withAffixes(new ItemStack(Items.IRON_INGOT), Map.of("damage_flat", 3, "hp_flat", 9, "durability_pct", 40));
		RpgTestSupport.result("materialAffixLinesNameTheirKind", "damage=" + BonusTooltip.kindSuffix(ingot, affix("damage_flat"))
			+ " hp=" + BonusTooltip.kindSuffix(ingot, affix("hp_flat")) + " durability=" + BonusTooltip.kindSuffix(ingot, affix("durability_pct")));
		helper.assertTrue("weapon".equals(BonusTooltip.kindSuffix(ingot, affix("damage_flat"))), "damage_flat suffix");
		helper.assertTrue("weapon".equals(BonusTooltip.kindSuffix(ingot, affix("armor_pen"))), "armor_pen suffix");
		helper.assertTrue("armor".equals(BonusTooltip.kindSuffix(ingot, affix("hp_flat"))), "hp_flat suffix");
		helper.assertTrue("armor".equals(BonusTooltip.kindSuffix(ingot, affix("reduction_pct"))), "reduction_pct suffix");
		helper.assertTrue(BonusTooltip.kindSuffix(ingot, affix("durability_pct")) == null, "a line for both kinds needs no suffix");
		helper.succeed();
	}

	@GameTest
	public void gearLinesNeedNoSuffix(final GameTestHelper helper) {
		ItemStack sword = BonusTestSupport.withAffixes(new ItemStack(Items.IRON_SWORD), Map.of("damage_flat", 3));
		helper.assertTrue(BonusTooltip.kindSuffix(sword, affix("damage_flat")) == null, "a sword line carried a suffix");
		helper.succeed();
	}

	@GameTest
	public void anAffixOnlyMaterialShowsItsLines(final GameTestHelper helper) {
		ItemStack affixOnly = BonusTestSupport.withAffixes(new ItemStack(Items.RAW_IRON), Map.of("hp_flat", 9));
		ItemStack statOnly = BonusTestSupport.rolled(Items.RAW_IRON, 1, Map.of("str", 1));
		helper.assertTrue(BonusTooltip.hasBonus(affixOnly) && BonusTooltip.hasBonus(statOnly), "a material with a bonus shows no lines");
		helper.assertTrue(!BonusTooltip.hasBonus(new ItemStack(Items.RAW_IRON)), "a plain material shows lines");
		helper.succeed();
	}

	/**
	 * The headless harness has no client, so the printed lines cannot be observed here.
	 * By hand: hold a mined iron ingot with a damage affix and confirm the line ends with the word weapon in brackets.
	 * Hold one with only an affix and confirm its lines appear.
	 */
	@GameTest(required = false)
	public void theClientLinesNeedAClientRequiresManualTest(final GameTestHelper helper) {
		helper.assertTrue(false, "by hand: read the tooltip of a mined ingot with a typed affix, and of an affix-only material");
	}
}
