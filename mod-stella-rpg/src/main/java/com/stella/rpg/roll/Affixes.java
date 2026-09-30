package com.stella.rpg.roll;

import com.stella.rpg.StellaRpg;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The sum of the affixes an entity wears or holds. Flat sums are in K units. Percent sums are fractions. */
public final class Affixes {
	public static final Identifier HP_FLAT_ID = StellaRpg.id("affix_hp_flat");
	public static final Identifier HP_PCT_ID = StellaRpg.id("affix_hp_pct");
	public static final String REFINE_KEY = "stella_refine";
	public static final double MATK_PER_REFINE = 12.0;

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private Affixes() {
	}

	public static double total(final LivingEntity entity, final Affix affix) {
		double sum = 0.0;
		if (affix.rollsOn(Affix.Kind.ARMOR)) {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				sum += stored(entity.getItemBySlot(slot), affix, Affix.Kind.ARMOR);
			}
			sum += stored(entity.getItemBySlot(EquipmentSlot.OFFHAND), affix, Affix.Kind.ARMOR);
			return sum;
		}
		ItemStack held = entity.getItemBySlot(EquipmentSlot.MAINHAND);
		Affix.Kind kind = WeaponRolls.kindOf(held);
		return kind != null && affix.rollsOn(kind) ? stored(held, affix, kind) : 0.0;
	}

	/** Flat MATK from affixes and from the refine level of the main-hand spellbook. K units. */
	public static double matkFlat(final LivingEntity entity) {
		ItemStack held = entity.getItemBySlot(EquipmentSlot.MAINHAND);
		double refine = WeaponRolls.kindOf(held) == Affix.Kind.BOOK ? MATK_PER_REFINE * refineLevel(held) : 0.0;
		return total(entity, Affix.MATK_FLAT) + refine;
	}

	public static int refineLevel(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : Math.max(0, data.copyTag().getIntOr(REFINE_KEY, 0));
	}

	/** Puts the worn HP affixes on max health as modifiers in vanilla units. */
	public static void refreshHealth(final LivingEntity entity, final double k) {
		AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		set(health, HP_FLAT_ID, total(entity, Affix.HP_FLAT) / k, AttributeModifier.Operation.ADD_VALUE);
		set(health, HP_PCT_ID, total(entity, Affix.HP_PCT), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void set(
		final AttributeInstance instance, final Identifier id, final double amount, final AttributeModifier.Operation operation
	) {
		AttributeModifier existing = instance.getModifier(id);
		if (amount <= 0.0) {
			if (existing != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (existing == null || existing.amount() != amount) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static double stored(final ItemStack stack, final Affix affix, final Affix.Kind kind) {
		Map<String, Integer> rolls = stack.isEmpty() ? null : stack.get(WeaponRolls.AFFIX_ROLLS);
		if (rolls == null || WeaponRolls.kindOf(stack) != kind) {
			return 0.0;
		}
		return affix.amount(Math.max(0, rolls.getOrDefault(affix.key(), 0)));
	}
}
