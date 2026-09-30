package com.stella.rpg;

import java.util.List;
import java.util.Locale;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

/** Builds the Atk text shown on the stats screen. */
public final class AttackPreview {
	private AttackPreview() {
	}

	/** Applies only the damage effects that need no target, such as Sharpness. */
	public static double withEnchantments(final ItemStack stack, final double attack) {
		float value = (float) attack;
		for (var entry : stack.getEnchantments().entrySet()) {
			Enchantment enchantment = entry.getKey().value();
			int level = entry.getIntValue();
			List<ConditionalEffect<EnchantmentValueEffect>> effects = enchantment.getEffects(EnchantmentEffectComponents.DAMAGE);
			for (ConditionalEffect<EnchantmentValueEffect> effect : effects) {
				if (effect.requirements().isEmpty()) {
					value = effect.effect().process(level, RandomSource.create(), value);
				}
			}
		}
		return value;
	}

	public static String number(final double value) {
		String text = String.format(Locale.ROOT, "%.1f", Math.round(value * 10.0) / 10.0);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	/** Dice add after multipliers, as in RpgCombat. */
	public static String format(final double attack, final RpgDice.Dice dice) {
		return number(attack + dice.min()) + "-" + number(attack + dice.max());
	}
}
