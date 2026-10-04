package com.stella.ruins.reward;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** One attribute change that a set bonus or an affix grants. */
public record AttributeBonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
	public static AttributeBonus add(final Holder<Attribute> attribute, final double amount) {
		return new AttributeBonus(attribute, amount, AttributeModifier.Operation.ADD_VALUE);
	}

	public static AttributeBonus multiplyBase(final Holder<Attribute> attribute, final double amount) {
		return new AttributeBonus(attribute, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	public static AttributeBonus multiplyTotal(final Holder<Attribute> attribute, final double amount) {
		return new AttributeBonus(attribute, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** The tooltip line, in the wording that vanilla uses for an attribute modifier. */
	public Component describe() {
		boolean relative = operation != AttributeModifier.Operation.ADD_VALUE;
		double shown = relative ? amount * 100.0 : amount;
		String key = (amount < 0.0 ? "attribute.modifier.take." : "attribute.modifier.plus.") + operation.id();
		return Component.translatable(
			key,
			ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(Math.abs(shown)),
			Component.translatable(attribute.value().getDescriptionId())
		);
	}
}
