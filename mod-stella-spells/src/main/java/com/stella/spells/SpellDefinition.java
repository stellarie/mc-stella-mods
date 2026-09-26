package com.stella.spells;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public record SpellDefinition(
	Identifier id,
	Item ingredient,
	int baseCooldownTicks,
	int tintRgb,
	TargetMode targetMode,
	SpellEffect effect
) {
}
