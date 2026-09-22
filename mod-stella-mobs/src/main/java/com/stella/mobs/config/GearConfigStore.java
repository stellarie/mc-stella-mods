package com.stella.mobs.config;

import com.stella.mobs.StellaMobs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class GearConfigStore {
	private static final String FILE_NAME = "loadouts.json";
	private static final String DEFAULT_JSON = """
		{
			"version": 1,
			"entities": {
				"minecraft:zombie": {
					"slots": {
						"mainhand": {
							"item": "minecraft:diamond_sword",
							"enchantments": { "minecraft:sharpness": 3 }
						},
						"offhand": { "item": "minecraft:shield" },
						"head": { "item": "minecraft:iron_helmet" },
						"chest": { "item": "minecraft:iron_chestplate" },
						"legs": { "item": "minecraft:iron_leggings" },
						"feet": { "item": "minecraft:iron_boots" }
					},
					"drop_chance": 0.085
				},
				"minecraft:husk": {
					"slots": {
						"mainhand": {
							"item": "minecraft:iron_sword",
							"enchantments": { "minecraft:sharpness": 2 }
						},
						"head": { "item": "minecraft:iron_helmet" }
					},
					"drop_chance": 0.085
				},
				"minecraft:skeleton": {
					"slots": {
						"mainhand": {
							"item": "minecraft:bow",
							"enchantments": { "minecraft:power": 2 }
						},
						"chest": { "item": "minecraft:leather_chestplate" }
					},
					"drop_chance": 0.085
				},
				"minecraft:wither_skeleton": {
					"slots": {
						"mainhand": {
							"item": "minecraft:stone_sword",
							"enchantments": { "minecraft:sharpness": 2 }
						},
						"head": { "item": "minecraft:iron_helmet" }
					},
					"drop_chance": 0.085
				}
			},
			"roles": {
				"minecraft:evoker": "magic"
			},
			"rolls": {
				"roll_chance": 0.15,
				"drop_chance_multiplier": 1.0
			}
		}""";
	private static volatile GearConfig config = GearConfig.EMPTY;

	private GearConfigStore() {
	}

	public static GearConfig get() {
		return config;
	}

	public static void set(final GearConfig value) {
		config = value;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaMobs.MOD_ID).resolve(FILE_NAME);
	}

	public static GearConfig load() {
		return loadFrom(path());
	}

	public static GearConfig loadFrom(final Path file) {
		try {
			if (Files.notExists(file)) {
				writeDefault(file);
			}

			config = GearConfigParser.parse(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			StellaMobs.LOGGER.warn("Could not read the gear config at {}: {}", file, e.getMessage());
			config = GearConfig.EMPTY;
		}

		return config;
	}

	private static void writeDefault(final Path file) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		Files.writeString(file, DEFAULT_JSON, StandardCharsets.UTF_8);
	}
}
