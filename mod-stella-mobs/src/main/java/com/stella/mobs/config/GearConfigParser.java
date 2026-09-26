package com.stella.mobs.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.stella.mobs.StellaMobs;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

public final class GearConfigParser {
	private static final int MAX_ENCHANTMENT_LEVEL = 255;

	private GearConfigParser() {
	}

	public static GearConfig parse(final String json) {
		JsonObject root;
		try {
			JsonElement element = StrictJsonParser.parse(json);
			if (!element.isJsonObject()) {
				warn("the config root is not a JSON object");
				return GearConfig.EMPTY;
			}

			root = element.getAsJsonObject();
		} catch (JsonSyntaxException | IllegalStateException e) {
			warn("the config is not valid JSON: " + e.getMessage());
			return GearConfig.EMPTY;
		}

		JsonElement entitiesElement = root.get("entities");
		if (entitiesElement == null || !entitiesElement.isJsonObject()) {
			warn("the 'entities' key is missing or is not a JSON object");
			return GearConfig.EMPTY;
		}

		Map<Identifier, Loadout> entities = new HashMap<>();

		for (Map.Entry<String, JsonElement> entry : entitiesElement.getAsJsonObject().entrySet()) {
			Identifier entityType = Identifier.tryParse(entry.getKey());
			if (entityType == null) {
				warn("skipping entity type '" + entry.getKey() + "': the key is not a valid identifier");
				continue;
			}

			Loadout loadout = parseLoadout(entry.getKey(), entry.getValue());
			if (loadout != null) {
				entities.put(entityType, loadout);
			}
		}

		Map<Identifier, RoleSpec> roles = new HashMap<>();
		JsonElement rolesElement = root.get("roles");

		if (rolesElement != null && rolesElement.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : rolesElement.getAsJsonObject().entrySet()) {
				Identifier entityType = Identifier.tryParse(entry.getKey());
				if (entityType == null) {
					warn("skipping role for '" + entry.getKey() + "': the key is not a valid identifier");
					continue;
				}

				RoleSpec spec = parseRole(entry.getKey(), entry.getValue());
				if (spec != null) {
					roles.put(entityType, spec);
				}
			}
		}

		RollSpec rollDefaults = RollSpec.DEFAULT;
		Map<Identifier, RollSpec> rolls = new HashMap<>();
		double spawnSafetyRadius = GearConfig.DEFAULT_SPAWN_SAFETY_RADIUS;
		JsonElement rollsElement = root.get("rolls");

		if (rollsElement != null && rollsElement.isJsonObject()) {
			JsonObject rollsObject = rollsElement.getAsJsonObject();
			Double radius = readMultiplier(rollsObject.get("spawn_safety_radius"));
			if (radius != null) {
				spawnSafetyRadius = radius;
			}
			rollDefaults = rollDefaults.overriddenBy(
				readChance(rollsObject.get("roll_chance")),
				readMultiplier(rollsObject.get("drop_chance_multiplier")),
				readChance(rollsObject.get("elite_chance")),
				readChance(rollsObject.get("boss_chance")),
				readMultiplier(rollsObject.get("boss_health_multiplier")),
				readMultiplier(rollsObject.get("boss_level_bonus")),
				readChance(rollsObject.get("war_call_brute_chance"))
			);
			JsonElement rollEntities = rollsObject.get("entities");

			if (rollEntities != null && rollEntities.isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : rollEntities.getAsJsonObject().entrySet()) {
					Identifier entityType = Identifier.tryParse(entry.getKey());
					if (entityType == null) {
						warn("skipping roll override for '" + entry.getKey() + "': the key is not a valid identifier");
						continue;
					}

					if (!entry.getValue().isJsonObject()) {
						warn("skipping roll override for entity type '" + entry.getKey() + "': the entry is not an object");
						continue;
					}

					JsonObject override = entry.getValue().getAsJsonObject();
					rolls.put(
						entityType,
						rollDefaults.overriddenBy(
							readChance(override.get("roll_chance")),
							readMultiplier(override.get("drop_chance_multiplier")),
							readChance(override.get("elite_chance")),
							readChance(override.get("boss_chance")),
							readMultiplier(override.get("boss_health_multiplier")),
							readMultiplier(override.get("boss_level_bonus")),
							readChance(override.get("war_call_brute_chance"))
						)
					);
				}
			}
		}

		Map<Identifier, RiderSpec> riders = new HashMap<>();
		RiderSpec riderDefaults = parseRiders(root, riders);

		return new GearConfig(entities, roles, rollDefaults, rolls, riderDefaults, riders, spawnSafetyRadius);
	}

	private static RiderSpec parseRiders(final JsonObject root, final Map<Identifier, RiderSpec> riders) {
		JsonElement ridersElement = root.get("riders");
		if (ridersElement == null || !ridersElement.isJsonObject()) {
			return RiderSpec.DEFAULT;
		}

		JsonObject ridersObject = ridersElement.getAsJsonObject();
		RiderSpec defaults = RiderSpec.DEFAULT.overriddenBy(readChance(ridersObject.get("chance")));
		JsonElement riderEntities = ridersObject.get("entities");

		if (riderEntities != null && riderEntities.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : riderEntities.getAsJsonObject().entrySet()) {
				Identifier entityType = Identifier.tryParse(entry.getKey());
				if (entityType == null) {
					warn("skipping rider override for '" + entry.getKey() + "': the key is not a valid identifier");
					continue;
				}

				if (!entry.getValue().isJsonObject()) {
					warn("skipping rider override for entity type '" + entry.getKey() + "': the entry is not an object");
					continue;
				}

				riders.put(
					entityType, defaults.overriddenBy(readChance(entry.getValue().getAsJsonObject().get("chance")))
				);
			}
		}

		return defaults;
	}

	private static @Nullable Double readChance(final @Nullable JsonElement element) {
		Double value = readDouble(element);
		if (value == null) {
			return null;
		}

		if (value < 0.0 || value > 1.0) {
			warn("ignoring a roll chance outside 0 to 1: " + value);
			return null;
		}

		return value;
	}

	private static @Nullable Double readMultiplier(final @Nullable JsonElement element) {
		Double value = readDouble(element);
		if (value == null) {
			return null;
		}

		if (value < 0.0) {
			warn("ignoring a negative drop chance multiplier: " + value);
			return null;
		}

		return value;
	}

	private static @Nullable Double readDouble(final @Nullable JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}

		return element.getAsDouble();
	}

	private static @Nullable RoleSpec parseRole(final String entityType, final JsonElement element) {
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			return buildRole(entityType, element.getAsString(), null, null);
		}

		if (!element.isJsonObject()) {
			warn("skipping role for entity type '" + entityType + "': the entry is not a role name or an object");
			return null;
		}

		JsonObject entry = element.getAsJsonObject();
		JsonElement roleElement = entry.get("role");
		if (roleElement == null || !roleElement.isJsonPrimitive() || !roleElement.getAsJsonPrimitive().isString()) {
			warn("skipping role for entity type '" + entityType + "': the 'role' key is missing or is not a string");
			return null;
		}

		return buildRole(
			entityType,
			roleElement.getAsString(),
			optionalDistance(entityType, entry.get("near")),
			optionalDistance(entityType, entry.get("far"))
		);
	}

	private static @Nullable RoleSpec buildRole(
		final String entityType, final String name, final @Nullable Double near, final @Nullable Double far
	) {
		CombatRole role = CombatRole.byName(name);
		if (role == null) {
			warn("skipping role for entity type '" + entityType + "': unknown role '" + name + "'");
			return null;
		}

		RoleSpec defaults = RoleSpec.of(role);
		double nearDistance = near != null ? near : defaults.nearDistance();
		double farDistance = far != null ? far : defaults.farDistance();
		if (nearDistance < 0.0 || farDistance < nearDistance) {
			warn("skipping role for entity type '" + entityType + "': the band " + nearDistance + " to " + farDistance + " is not valid");
			return null;
		}

		return defaults.withBand(nearDistance, farDistance);
	}

	private static @Nullable Double optionalDistance(final String entityType, final @Nullable JsonElement element) {
		if (element == null) {
			return null;
		}

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			warn("ignoring a band distance for entity type '" + entityType + "': the value is not a number");
			return null;
		}

		return element.getAsDouble();
	}

	private static Loadout parseLoadout(final String entityType, final JsonElement element) {
		if (!element.isJsonObject()) {
			warn("skipping entity type '" + entityType + "': the entry is not a JSON object");
			return null;
		}

		JsonObject entry = element.getAsJsonObject();
		Map<EquipmentSlot, SlotSpec> slots = new EnumMap<>(EquipmentSlot.class);
		JsonElement slotsElement = entry.get("slots");

		if (slotsElement != null) {
			if (!slotsElement.isJsonObject()) {
				warn("skipping 'slots' for entity type '" + entityType + "': it is not a JSON object");
			} else {
				for (Map.Entry<String, JsonElement> slotEntry : slotsElement.getAsJsonObject().entrySet()) {
					EquipmentSlot slot = EquipmentSlot.CODEC.byName(slotEntry.getKey());
					if (slot == null) {
						warn("skipping slot '" + slotEntry.getKey() + "' for entity type '" + entityType + "': unknown slot name");
						continue;
					}

					SlotSpec spec = parseSlot(entityType, slotEntry.getKey(), slotEntry.getValue());
					if (spec != null) {
						slots.put(slot, spec);
					}
				}
			}
		}

		Optional<Float> dropChance = parseDropChance(entityType, entry.get("drop_chance"));
		if (slots.isEmpty() && dropChance.isEmpty()) {
			warn("skipping entity type '" + entityType + "': no valid slot and no drop chance");
			return null;
		}

		return new Loadout(slots, dropChance);
	}

	private static SlotSpec parseSlot(final String entityType, final String slotName, final JsonElement element) {
		if (!element.isJsonObject()) {
			warn("skipping slot '" + slotName + "' for entity type '" + entityType + "': the slot is not a JSON object");
			return null;
		}

		JsonElement itemElement = element.getAsJsonObject().get("item");
		if (itemElement == null || !itemElement.isJsonPrimitive() || !itemElement.getAsJsonPrimitive().isString()) {
			warn("skipping slot '" + slotName + "' for entity type '" + entityType + "': the 'item' key is missing or is not a string");
			return null;
		}

		String itemName = itemElement.getAsString();
		Identifier item = Identifier.tryParse(itemName);
		if (item == null || BuiltInRegistries.ITEM.get(item).isEmpty()) {
			warn("skipping slot '" + slotName + "' for entity type '" + entityType + "': unknown item id '" + itemName + "'");
			return null;
		}

		return new SlotSpec(item, parseEnchantments(entityType, slotName, element.getAsJsonObject().get("enchantments")));
	}

	private static Map<Identifier, Integer> parseEnchantments(final String entityType, final String slotName, final JsonElement element) {
		Map<Identifier, Integer> enchantments = new LinkedHashMap<>();
		if (element == null) {
			return enchantments;
		}

		if (!element.isJsonObject()) {
			warn("skipping 'enchantments' for entity type '" + entityType + "' slot '" + slotName + "': it is not a JSON object");
			return enchantments;
		}

		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null) {
				warn("skipping enchantment '" + entry.getKey() + "' for entity type '" + entityType + "': the id is not a valid identifier");
				continue;
			}

			JsonElement levelElement = entry.getValue();
			if (levelElement == null || !levelElement.isJsonPrimitive() || !levelElement.getAsJsonPrimitive().isNumber()) {
				warn("skipping enchantment '" + entry.getKey() + "' for entity type '" + entityType + "': the level is not a number");
				continue;
			}

			int level = levelElement.getAsInt();
			if (level < 1) {
				warn("skipping enchantment '" + entry.getKey() + "' for entity type '" + entityType + "': the level " + level + " is below 1");
				continue;
			}

			if (level > MAX_ENCHANTMENT_LEVEL) {
				warn("clamping enchantment '" + entry.getKey() + "' for entity type '" + entityType + "' from " + level + " to " + MAX_ENCHANTMENT_LEVEL);
				level = MAX_ENCHANTMENT_LEVEL;
			}

			enchantments.put(id, level);
		}

		return enchantments;
	}

	private static Optional<Float> parseDropChance(final String entityType, final JsonElement element) {
		if (element == null) {
			return Optional.empty();
		}

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			warn("ignoring 'drop_chance' for entity type '" + entityType + "': it is not a number");
			return Optional.empty();
		}

		float chance = element.getAsFloat();
		if (!(chance >= 0.0F)) {
			warn("ignoring 'drop_chance' for entity type '" + entityType + "': the value is below 0");
			return Optional.empty();
		}

		return Optional.of(chance);
	}

	private static void warn(final String message) {
		StellaMobs.LOGGER.warn("Gear config: {}", message);
	}
}
