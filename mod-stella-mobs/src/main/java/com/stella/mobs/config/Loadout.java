package com.stella.mobs.config;

import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.EquipmentSlot;

public record Loadout(Map<EquipmentSlot, SlotSpec> slots, Optional<Float> dropChance) {
	public Loadout {
		slots = Map.copyOf(slots);
	}
}
