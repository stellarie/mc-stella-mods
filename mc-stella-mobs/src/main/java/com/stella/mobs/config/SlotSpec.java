package com.stella.mobs.config;

import java.util.Map;
import net.minecraft.resources.Identifier;

public record SlotSpec(Identifier item, Map<Identifier, Integer> enchantments) {
	public SlotSpec {
		enchantments = Map.copyOf(enchantments);
	}
}
