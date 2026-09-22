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

		return new GearConfig(entities);
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
