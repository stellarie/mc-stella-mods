package com.stella.learn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** The live config, read from `config/mod-stella-learn/learn.json`, which is written with the defaults if absent. */
public final class LearnConfigStore {
	private static final String FILE_NAME = "learn.json";
	private static final String DEFAULT_JSON = """
		{
			"callout_radius": %s,
			"max_responders": %d,
			"callout_cooldown_ticks": %d,
			"profile_half_life_ticks": %d,
			"rush_band_gain": %s,
			"rush_saturation": %s
		}
		""".formatted(
		LearnConfig.DEFAULT_CALLOUT_RADIUS, LearnConfig.DEFAULT_MAX_RESPONDERS,
		LearnConfig.DEFAULT_CALLOUT_COOLDOWN_TICKS, LearnConfig.DEFAULT_PROFILE_HALF_LIFE_TICKS,
		LearnConfig.DEFAULT_RUSH_BAND_GAIN, LearnConfig.DEFAULT_RUSH_SATURATION
	);
	private static volatile LearnConfig config = LearnConfig.defaults();

	private LearnConfigStore() {
	}

	public static LearnConfig get() {
		return config;
	}

	public static void set(final LearnConfig value) {
		config = value;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaLearn.MOD_ID).resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path.getParent());
				Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8);
			}

			config = LearnConfig.parse(Files.readString(path, StandardCharsets.UTF_8));
		} catch (IOException exception) {
			StellaLearn.LOGGER.warn("Could not read {}; using the defaults: {}", path, exception.getMessage());
			config = LearnConfig.defaults();
		}
	}
}
