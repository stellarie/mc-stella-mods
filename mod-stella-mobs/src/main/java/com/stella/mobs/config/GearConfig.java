package com.stella.mobs.config;

import java.util.Map;
import net.minecraft.resources.Identifier;

public record GearConfig(Map<Identifier, Loadout> entities) {
	public static final GearConfig EMPTY = new GearConfig(Map.of());

	public GearConfig {
		entities = Map.copyOf(entities);
	}

	public Loadout loadoutFor(final Identifier entityType) {
		return this.entities.get(entityType);
	}
}
