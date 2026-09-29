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
			"rush_saturation": %s,
			"shield_saturation": %s,
			"shield_threshold": %s,
			"shield_patience_ticks": %d,
			"shield_rest_ticks": %d,
			"ranged_saturation": %s,
			"cover_threshold": %s,
			"cover_rest_ticks": %d,
			"retreat_saturation": %s,
			"choke_threshold": %s,
			"choke_group": %d,
			"choke_patience_ticks": %d,
			"choke_rest_ticks": %d,
			"behavior_log": %s,
			"log_summary_ticks": %d,
			"callout_fx": %s
		}
		""".formatted(
		LearnConfig.DEFAULT_CALLOUT_RADIUS, LearnConfig.DEFAULT_MAX_RESPONDERS,
		LearnConfig.DEFAULT_CALLOUT_COOLDOWN_TICKS, LearnConfig.DEFAULT_PROFILE_HALF_LIFE_TICKS,
		LearnConfig.DEFAULT_RUSH_BAND_GAIN, LearnConfig.DEFAULT_RUSH_SATURATION, LearnConfig.DEFAULT_SHIELD_SATURATION,
		LearnConfig.DEFAULT_SHIELD_THRESHOLD, LearnConfig.DEFAULT_SHIELD_PATIENCE_TICKS, LearnConfig.DEFAULT_SHIELD_REST_TICKS,
		LearnConfig.DEFAULT_RANGED_SATURATION, LearnConfig.DEFAULT_COVER_THRESHOLD, LearnConfig.DEFAULT_COVER_REST_TICKS,
		LearnConfig.DEFAULT_RETREAT_SATURATION, LearnConfig.DEFAULT_CHOKE_THRESHOLD, LearnConfig.DEFAULT_CHOKE_GROUP,
		LearnConfig.DEFAULT_CHOKE_PATIENCE_TICKS, LearnConfig.DEFAULT_CHOKE_REST_TICKS,
		LearnConfig.DEFAULT_BEHAVIOR_LOG, LearnConfig.DEFAULT_LOG_SUMMARY_TICKS,
		LearnConfig.DEFAULT_CALLOUT_FX
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
			logLoaded(path);
		} catch (IOException exception) {
			StellaLearn.LOGGER.warn("Could not read {}; using the defaults: {}", path, exception.getMessage());
			config = LearnConfig.defaults();
		}
	}

	/** One line naming every value in force, so a log shows what a server is running on. */
	private static void logLoaded(final Path path) {
		LearnConfig c = config;
		LearnLog.info(c, "learnConfig", "LOAD", "path=" + path
			+ " callout_radius=" + LearnLog.num(c.calloutRadius()) + " max_responders=" + c.maxResponders()
			+ " callout_cooldown_ticks=" + c.calloutCooldownTicks() + " profile_half_life_ticks=" + c.profileHalfLifeTicks()
			+ " rush_band_gain=" + LearnLog.num(c.rushBandGain()) + " rush_saturation=" + LearnLog.num(c.rushSaturation())
			+ " shield_saturation=" + LearnLog.num(c.shieldSaturation()) + " shield_threshold=" + LearnLog.num(c.shieldThreshold())
			+ " shield_patience_ticks=" + c.shieldPatienceTicks() + " ranged_saturation=" + LearnLog.num(c.rangedSaturation())
			+ " cover_threshold=" + LearnLog.num(c.coverThreshold()) + " retreat_saturation=" + LearnLog.num(c.retreatSaturation())
			+ " choke_threshold=" + LearnLog.num(c.chokeThreshold()) + " choke_group=" + c.chokeGroup()
			+ " choke_patience_ticks=" + c.chokePatienceTicks() + " behavior_log=" + c.behaviorLog()
			+ " log_summary_ticks=" + c.logSummaryTicks() + " callout_fx=" + c.calloutFx());
	}
}
