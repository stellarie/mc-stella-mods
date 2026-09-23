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
			"_note": "An older copy of this file is never upgraded. A key you set here wins and a key you delete falls back to this document. Armor reaches the vanilla ceiling of 30 at 125 distance points, which is 625 blocks; the rest of the armor curve is absorbed and max_health carries the far world.",
			"version": 1,
			"level_cap": 50,
			"points_per_level": { "base": 6, "per_level": 3 },
			"distance": { "blocks_per_point": 5, "max_points": 2000 },
			"depth": { "blocks_per_point": 16, "min_y": 63, "max_points": 8 },
			"kills": {
				"points_per_kill": 1,
				"points_per_boss": 4,
				"boss_types": [ "minecraft:wither", "minecraft:ender_dragon", "minecraft:warden" ]
			},
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
			"xp_multiplier": { "even_fight": 1.0, "per_level_gap": 0.15, "min": 0.05, "max": 4.0 },
			"bands": {
				"red_above": 20,
				"yellow_above": 10,
				"orange_above": 6,
				"green_within": 5
			},
			"tracker": { "scan_interval_ticks": 40, "heartbeat_ticks": 5 },
			"attributes": {
				"distance": { "max_health": 0.5, "armor": 0.24, "knockback_resistance": 0.006 },
				"combat": { "attack_damage_max": 20.0, "attack_speed_max": 1.0 }
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
