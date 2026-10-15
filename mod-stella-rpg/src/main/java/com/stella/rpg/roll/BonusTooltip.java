package com.stella.rpg.roll;

import com.stella.rpg.AttackPreview;
import com.stella.rpg.DescriptionLines;
import com.stella.rpg.StaffMatk;
import com.stella.rpg.Stat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

	/** The green lines of the rolled bonuses on a stack: the affixes first, then the stats. */
	public static List<Component> lines(final ItemStack stack) {
		List<Component> out = new ArrayList<>();
		if (StaffMatk.isStaff(stack)) {
			out.add(Component.translatable("tooltip.mod-stella-rpg.staff_matk", AttackPreview.number(StaffMatk.withEnchants(stack)))
				.withStyle(ChatFormatting.GREEN));
		}
		if (!hasBonus(stack)) {
			return out;
		}
		Map<String, Integer> rolls = stack.getOrDefault(WeaponRolls.STAT_ROLLS, Map.of());
		Map<String, Integer> affixes = stack.get(WeaponRolls.AFFIX_ROLLS);
		if (affixes != null) {
			for (Affix affix : Affix.values()) {
				Integer stored = affixes.get(affix.key());
				if (stored != null) {
					MutableComponent line = Component.translatable("tooltip.mod-stella-rpg.affix." + affix.key(),
						AttackPreview.number(affix.shown(stored)));
					String suffix = kindSuffix(stack, affix);
					if (suffix != null) {
						line.append(Component.translatable("tooltip.mod-stella-rpg.kind." + suffix));
					}
					DescriptionLines.add(out, line.withStyle(ChatFormatting.GREEN));
				}
			}
		}
		for (Stat stat : Stat.values()) {
			Integer value = rolls.get(stat.key());
			if (value != null) {
				DescriptionLines.add(out, Component.translatable("tooltip.mod-stella-rpg.roll", value,
					Component.translatable("tooltip.mod-stella-rpg.stat." + stat.key())).withStyle(ChatFormatting.GREEN));
			}
		}
		return out;
	}
}
