package com.stella.mobs.config;

import java.util.Map;
import net.minecraft.resources.Identifier;

public record GearConfig(Map<Identifier, Loadout> entities, Map<Identifier, RoleSpec> roles) {
	public static final GearConfig EMPTY = new GearConfig(Map.of(), Map.of());

	public GearConfig {
		entities = Map.copyOf(entities);
		roles = Map.copyOf(roles);
	}

	public Loadout loadoutFor(final Identifier entityType) {
		return this.entities.get(entityType);
	}

	public RoleSpec roleFor(final Identifier entityType) {
		return this.roles.get(entityType);
	}
}
