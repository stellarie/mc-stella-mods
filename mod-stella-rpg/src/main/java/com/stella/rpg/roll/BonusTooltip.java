package com.stella.rpg.roll;

import net.minecraft.world.item.ItemStack;

/** The tooltip rules for bonuses. The client prints the lines, and these rules decide which lines and which suffix. */
public final class BonusTooltip {
	private BonusTooltip() {
	}

	/** A stack with either map shows lines. A material can carry affixes without stats. */
	public static boolean hasBonus(final ItemStack stack) {
		return stack.has(WeaponRolls.STAT_ROLLS) || stack.has(WeaponRolls.AFFIX_ROLLS);
	}

	/** "weapon" or "armor" for a material line that only one gear kind can use, and null for any other line. */
	public static String kindSuffix(final ItemStack stack, final Affix affix) {
		if (!BonusCarrier.isMaterial(stack)) {
			return null;
		}
		boolean weapon = affix.rollsOn(Affix.Kind.WEAPON);
		boolean armor = affix.rollsOn(Affix.Kind.ARMOR);
		if (weapon == armor) {
			return null;
		}
		return weapon ? "weapon" : "armor";
	}
}
