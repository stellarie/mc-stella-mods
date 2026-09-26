package com.stella.mobs.config;

import java.util.Map;
import net.minecraft.resources.Identifier;

public record GearConfig(
	Map<Identifier, Loadout> entities,
	Map<Identifier, RoleSpec> roles,
	RollSpec rollDefaults,
	Map<Identifier, RollSpec> rolls,
	RiderSpec riderDefaults,
	Map<Identifier, RiderSpec> riders,
	double spawnSafetyRadius
) {
	/** The code default leaves the spawn gate off, so a rig that parses its own config is never gated. */
	public static final double DEFAULT_SPAWN_SAFETY_RADIUS = 0.0;
	public static final GearConfig EMPTY = new GearConfig(
		Map.of(), Map.of(), RollSpec.DEFAULT, Map.of(), RiderSpec.DEFAULT, Map.of()
	);

	public GearConfig(
		final Map<Identifier, Loadout> entities,
		final Map<Identifier, RoleSpec> roles,
		final RollSpec rollDefaults,
		final Map<Identifier, RollSpec> rolls,
		final RiderSpec riderDefaults,
		final Map<Identifier, RiderSpec> riders
	) {
		this(entities, roles, rollDefaults, rolls, riderDefaults, riders, DEFAULT_SPAWN_SAFETY_RADIUS);
	}

	public GearConfig {
		entities = Map.copyOf(entities);
		roles = Map.copyOf(roles);
		rolls = Map.copyOf(rolls);
		riders = Map.copyOf(riders);
	}

	public Loadout loadoutFor(final Identifier entityType) {
		return this.entities.get(entityType);
	}

	public RoleSpec roleFor(final Identifier entityType) {
		return this.roles.get(entityType);
	}

	public RollSpec rollFor(final Identifier entityType) {
		return this.rolls.getOrDefault(entityType, this.rollDefaults);
	}

	public RiderSpec riderFor(final Identifier entityType) {
		return this.riders.getOrDefault(entityType, this.riderDefaults);
	}
}
