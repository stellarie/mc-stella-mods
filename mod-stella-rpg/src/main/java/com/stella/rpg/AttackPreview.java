package com.stella.rpg;

import java.util.List;
import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import com.stella.rpg.roll.ItemTiers;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

/**
 * Builds the Atk text shown on the stats screen. Attack damage is not a synced attribute, so the client reads the
 * held item's own modifiers instead of the player's attribute.
 */
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

	/**
	 * The melee hit range in K units: base and weapon, enchantments, then the STR and weapon dice times the dice
	 * multiplier, status ATK, flat affix, and damage %.
	 */
	public static double[] range(final LivingEntity player, final int str) {
		ItemStack held = player.getMainHandItem();
		double base = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		double attack = withEnchantments(held, held.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ATTACK_DAMAGE, base, EquipmentSlot.MAINHAND));
		RpgDice.Dice dice = RpgDice.of(str);
		RpgDice.Dice weapon = PhysicalDice.weaponDice(held, false);
		int low = dice.min() + (weapon == null ? 0 : weapon.min());
		int high = dice.max() + (weapon == null ? 0 : weapon.max());
		double multiplier = PhysicalDice.multiplier(player, held);
		double k = RpgScale.k();
		double extra = RpgCombat.statusAtk(str) + Affixes.total(player, Affix.DAMAGE_FLAT) / k;
		double factor = RpgCombat.damageFactor(player) * k;
		return new double[] {(attack + low * multiplier + extra) * factor, (attack + high * multiplier + extra) * factor};
	}

	/**
	 * The ranged shot range in K units for a held bow or crossbow: the same DEX dice, the bow's own die, status ATK,
	 * the flat affix and the damage percent that {@link RpgCombat} rolls for a shot. No weapon damage attribute is
	 * added, because a vanilla bow and crossbow carry none.
	 */
	public static double[] ranged(final LivingEntity player, final int dex) {
		ItemStack held = player.getMainHandItem();
		RpgDice.Dice dice = RpgDice.of(dex);
		RpgDice.Dice weapon = PhysicalDice.weaponDice(held, true);
		int low = dice.min() + (weapon == null ? 0 : weapon.min());
		int high = dice.max() + (weapon == null ? 0 : weapon.max());
		double multiplier = PhysicalDice.multiplier(player, held);
		double k = RpgScale.k();
		double extra = RpgCombat.statusAtk(dex) + Affixes.total(player, Affix.DAMAGE_FLAT) / k;
		double factor = RpgCombat.damageFactor(player) * k;
		return new double[] {(low * multiplier + extra) * factor, (high * multiplier + extra) * factor};
	}

	/** True when a held bow or crossbow makes the Atk line read the ranged number. */
	public static boolean heldRanged(final LivingEntity player) {
		ItemTiers.Info info = ItemTiers.of(player.getMainHandItem());
		return info != null && info.type() != null && info.type().ranged();
	}

	public static String format(final double[] range) {
		return number(range[0]) + "-" + number(range[1]);
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
