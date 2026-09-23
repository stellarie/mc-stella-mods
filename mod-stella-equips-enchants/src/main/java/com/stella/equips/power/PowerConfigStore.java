package com.stella.equips.power;

import com.stella.equips.StellaEquips;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class PowerConfigStore {
	public static final String FILE_NAME = "power.json";
	public static final String DEFAULT_JSON = """
		{
			"_note": "Kills are the only source of power. The player banks points from kills and nothing else; distance and depth buy no points, they only decide how strong the mob is, and a deep kill pays more than a shallow one. Curve. The cost of the step from level L to L+1 is base times growth to the power of L-1, so level 2 costs 1 point and level 50 costs 6275 in total. Award. A kill pays max(minimum, ceil(mobLevel times divisor)), so a level 0 mob pays 1, a level 32 mob pays 16 and the mob level cap of 36 pays 18. Death. A death removes 5 percent of the banked points and zero stays zero. Attributes. Every attribute key below is the value at level 50. Health is a flat gain of health_per_level for every level above the first, from health_base, and stops at the engine ceiling of 1024. The other four scale linearly from 0 at level 1, so there is one unit for those four. Upgrades. An older copy of this file is never upgraded: a key you set wins and a deleted key falls back to this document.",
			"version": 1,
			"level_cap": 50,
			"curve": { "base": 1.0, "growth": 1.15 },
			"award": { "divisor": 0.5, "minimum": 1 },
			"mob_level": {
				"blocks_per_level": 300,
				"cap": 32,
				"depth_bonus_cap": 4,
				"depth_bonus_blocks": 16,
				"depth_reference_y": 0,
				"level_cap": 36,
				"other_dimension_scale": 1.0,
				"dimension_scale": {
					"minecraft:the_nether": 8.0,
					"minecraft:overworld": 1.0,
					"minecraft:the_end": 1.0
				}
			},
			"bands": {
				"red_above": 20,
				"yellow_above": 10,
				"orange_above": 6,
				"green_within": 5
			},
			"tracker": { "scan_interval_ticks": 40, "heartbeat_ticks": 5 },
			"attributes": {
				"health_base": 20.0,
				"health_per_level": 4.0,
				"armor_at_cap": 11.76,
				"knockback_resistance_at_cap": 0.294,
				"attack_damage_at_cap": 20.0,
				"attack_speed_at_cap": 1.0
			},
			"readout": {
				"enabled_by_default": true,
				"radius": 32,
				"max_per_player": 40,
				"height_offset": 0.9,
				"view_range": 0.5
			}
		}""";
	private static volatile PowerConfig config;

	private PowerConfigStore() {
	}

	public static PowerConfig get() {
		PowerConfig current = config;
		if (current == null) {
			current = bundled();
			config = current;
		}

		return current;
	}

	public static PowerConfig bundled() {
		return PowerConfigParser.parseComplete(DEFAULT_JSON).orElseThrow(
			() -> new IllegalStateException("the bundled power.json is incomplete")
		);
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaEquips.MOD_ID).resolve(FILE_NAME);
	}

	public static PowerConfig load() {
		return loadFrom(path());
	}

	public static PowerConfig loadFrom(final Path file) {
		PowerConfig fallback = get();

		try {
			if (Files.notExists(file)) {
				writeDefault(file);
			}

			config = PowerConfigParser.parse(Files.readString(file, StandardCharsets.UTF_8), fallback);
		} catch (IOException e) {
			warn("could not read " + file + ": " + e.getMessage());
			config = fallback;
		}

		return config;
	}

	private static void warn(final String message) {
		StellaEquips.LOGGER.warn("Power config: {}", message);
	}

	private static void writeDefault(final Path file) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		Files.writeString(file, DEFAULT_JSON, StandardCharsets.UTF_8);
	}
}
