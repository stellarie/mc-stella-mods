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
			"_note": "Power follows the vanilla experience level. The mod reads the level and never writes experience, so kills, deaths and enchanting work exactly as in vanilla. The level is capped at level_cap. Attributes. Power gives health only. Health is a flat gain of health_per_level for every level above the first, from health_base, and stops at the engine ceiling of 1024, so the health value at level 1024 is health_base plus 1023 times health_per_level, up to the engine ceiling near level 403. The mod-stella-rpg mod owns damage, attack speed and damage reduction. Threat. The readout shows the level mod-stella-mobs rolled for a mob, and the colour compares it with your level; without mod-stella-mobs it shows no level. Upgrades. An older copy of this file is never upgraded: a key you set wins, a deleted key falls back to this document, and the retired curve, award, armor, knockback resistance, attack damage, attack speed and mob_level keys are reported and ignored.",
			"version": 1,
			"level_cap": 1024,
			"bands": {
				"red_above": 20,
				"yellow_above": 10,
				"orange_above": 6,
				"green_within": 5
			},
			"tracker": { "scan_interval_ticks": 40, "heartbeat_ticks": 5 },
			"attributes": {
				"health_base": 20.0,
				"health_per_level": 2.5
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
