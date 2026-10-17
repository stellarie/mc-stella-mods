package com.stella.learn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The tunable numbers, each a starting value. A bad entry logs and keeps its default. */
public record LearnConfig(
	double calloutRadius, int maxResponders, int calloutCooldownTicks, long profileHalfLifeTicks, double rushBandGain, double rushSaturation,
	double shieldSaturation, double shieldThreshold, int shieldPatienceTicks, int shieldRestTicks,
	double rangedSaturation, double coverThreshold, int coverRestTicks,
	double retreatSaturation, double chokeThreshold, int chokeGroup, int chokePatienceTicks, int chokeRestTicks,
	boolean behaviorLog, int logSummaryTicks, boolean calloutFx, boolean callouts,
	double moveSaturation, double moveThreshold, int interceptLeadTicks, double critSaturation, double chargeTighten,
	double rlAlpha, double rlEpsilon, int rlWindowTicks
) {
	public static final double DEFAULT_CALLOUT_RADIUS = 16.0;
	public static final int DEFAULT_MAX_RESPONDERS = 4;
	public static final int DEFAULT_CALLOUT_COOLDOWN_TICKS = 100;
	public static final long DEFAULT_PROFILE_HALF_LIFE_TICKS = 6000L;
	/** Blocks an archer's near band grows by against a player whose rush count has reached the saturation. */
	public static final double DEFAULT_RUSH_BAND_GAIN = 6.0;
	/** Melee hits, decayed, at which a player counts as a full rusher. */
	public static final double DEFAULT_RUSH_SATURATION = 8.0;
	/** Blocks, decayed, at which a player counts as a full turtle. */
	public static final double DEFAULT_SHIELD_SATURATION = 5.0;
	/** The share of a full turtle at which mobs start waiting out a raised shield. */
	public static final double DEFAULT_SHIELD_THRESHOLD = 0.5;
	/** Ticks a mob waits out one raised shield before it attacks anyway. */
	public static final int DEFAULT_SHIELD_PATIENCE_TICKS = 100;
	/** Ticks a mob that ran out of patience rests before it waits again. */
	public static final int DEFAULT_SHIELD_REST_TICKS = 40;
	/** Ranged hits, decayed, at which a player counts as a full shooter. */
	public static final double DEFAULT_RANGED_SATURATION = 6.0;
	/** The share of a full shooter at which melee mobs start advancing from cover to cover. */
	public static final double DEFAULT_COVER_THRESHOLD = 0.5;
	/** Ticks a melee mob rests between two hops from cover to cover. */
	public static final int DEFAULT_COVER_REST_TICKS = 30;
	/** Retreats, decayed, at which a player counts as one who always falls back. */
	public static final double DEFAULT_RETREAT_SATURATION = 4.0;
	/** The share of a full retreater at which mobs stop entering a chokepoint one at a time. */
	public static final double DEFAULT_CHOKE_THRESHOLD = 0.5;
	/** Mobs that must have gathered outside a chokepoint before they go in together. */
	public static final int DEFAULT_CHOKE_GROUP = 3;
	/** Ticks a mob waits outside a chokepoint for its group before it goes in anyway. */
	public static final int DEFAULT_CHOKE_PATIENCE_TICKS = 200;
	public static final int DEFAULT_CHOKE_REST_TICKS = 40;
	/** Whether the mod writes its `[BEHAVIOR]` lines to the server log. */
	public static final boolean DEFAULT_BEHAVIOR_LOG = true;
	/** Ticks between two profile summary lines per player; 0 switches the summary off. */
	public static final int DEFAULT_LOG_SUMMARY_TICKS = 600;
	/** Whether a callout plays its particles and sound, so a player can see a call go out. */
	public static final boolean DEFAULT_CALLOUT_FX = true;
	/** Whether monsters call each other in: the hurt callout and shared sight. Formations form either way. */
	public static final boolean DEFAULT_CALLOUTS = false;
	/** Movement samples, decayed, at which a strafe, a charge or a kite counts as a full habit. */
	public static final double DEFAULT_MOVE_SATURATION = 6.0;
	/** The share of a full movement habit at which mobs adapt to it. */
	public static final double DEFAULT_MOVE_THRESHOLD = 0.5;
	/** Ticks ahead of a moving player that a melee mob aims when it leads its target. */
	public static final int DEFAULT_INTERCEPT_LEAD_TICKS = 12;
	/** Critical hits, decayed, at which a player counts as a full crit-jumper. */
	public static final double DEFAULT_CRIT_SATURATION = 4.0;
	/** Share a melee flank slot's distance shrinks by against a full charger. */
	public static final double DEFAULT_CHARGE_TIGHTEN = 0.4;
	/** How far one outcome moves a tactic's learned value toward that outcome, from 0 to 1. */
	public static final double DEFAULT_RL_ALPHA = 0.25;
	/** The share of decisions that still try a random tactic once a mob has plenty of experience. */
	public static final double DEFAULT_RL_EPSILON = 0.05;
	/** Ticks after a tactic is chosen over which its outcome is measured. */
	public static final int DEFAULT_RL_WINDOW_TICKS = 30;
	static final String CALLOUT_RADIUS = "callout_radius";
	static final String MAX_RESPONDERS = "max_responders";
	static final String CALLOUT_COOLDOWN_TICKS = "callout_cooldown_ticks";
	static final String PROFILE_HALF_LIFE_TICKS = "profile_half_life_ticks";
	static final String RUSH_BAND_GAIN = "rush_band_gain";
	static final String RUSH_SATURATION = "rush_saturation";
	static final String SHIELD_SATURATION = "shield_saturation";
	static final String SHIELD_THRESHOLD = "shield_threshold";
	static final String SHIELD_PATIENCE_TICKS = "shield_patience_ticks";
	static final String SHIELD_REST_TICKS = "shield_rest_ticks";
	static final String RANGED_SATURATION = "ranged_saturation";
	static final String COVER_THRESHOLD = "cover_threshold";
	static final String COVER_REST_TICKS = "cover_rest_ticks";
	static final String RETREAT_SATURATION = "retreat_saturation";
	static final String CHOKE_THRESHOLD = "choke_threshold";
	static final String CHOKE_GROUP = "choke_group";
	static final String CHOKE_PATIENCE_TICKS = "choke_patience_ticks";
	static final String CHOKE_REST_TICKS = "choke_rest_ticks";
	static final String BEHAVIOR_LOG = "behavior_log";
	static final String LOG_SUMMARY_TICKS = "log_summary_ticks";
	static final String CALLOUT_FX = "callout_fx";
	static final String CALLOUTS = "callouts";
	static final String MOVE_SATURATION = "move_saturation";
	static final String MOVE_THRESHOLD = "move_threshold";
	static final String INTERCEPT_LEAD_TICKS = "intercept_lead_ticks";
	static final String CRIT_SATURATION = "crit_saturation";
	static final String CHARGE_TIGHTEN = "charge_tighten";
	static final String RL_ALPHA = "rl_alpha";
	static final String RL_EPSILON = "rl_epsilon";
	static final String RL_WINDOW_TICKS = "rl_window_ticks";

	public LearnConfig withCalloutRadius(final double value) {
		return new LearnConfig(value, this.maxResponders, this.calloutCooldownTicks, this.profileHalfLifeTicks, this.rushBandGain,
			this.rushSaturation, this.shieldSaturation, this.shieldThreshold, this.shieldPatienceTicks, this.shieldRestTicks,
			this.rangedSaturation, this.coverThreshold, this.coverRestTicks,
			this.retreatSaturation, this.chokeThreshold, this.chokeGroup, this.chokePatienceTicks, this.chokeRestTicks,
			this.behaviorLog, this.logSummaryTicks, this.calloutFx, this.callouts,
			this.moveSaturation, this.moveThreshold, this.interceptLeadTicks, this.critSaturation, this.chargeTighten,
			this.rlAlpha, this.rlEpsilon, this.rlWindowTicks);
	}

	public LearnConfig withMaxResponders(final int value) {
		return new LearnConfig(this.calloutRadius, value, this.calloutCooldownTicks, this.profileHalfLifeTicks, this.rushBandGain,
			this.rushSaturation, this.shieldSaturation, this.shieldThreshold, this.shieldPatienceTicks, this.shieldRestTicks,
			this.rangedSaturation, this.coverThreshold, this.coverRestTicks,
			this.retreatSaturation, this.chokeThreshold, this.chokeGroup, this.chokePatienceTicks, this.chokeRestTicks,
			this.behaviorLog, this.logSummaryTicks, this.calloutFx, this.callouts,
			this.moveSaturation, this.moveThreshold, this.interceptLeadTicks, this.critSaturation, this.chargeTighten,
			this.rlAlpha, this.rlEpsilon, this.rlWindowTicks);
	}

	public LearnConfig withCalloutCooldownTicks(final int value) {
		return new LearnConfig(this.calloutRadius, this.maxResponders, value, this.profileHalfLifeTicks, this.rushBandGain,
			this.rushSaturation, this.shieldSaturation, this.shieldThreshold, this.shieldPatienceTicks, this.shieldRestTicks,
			this.rangedSaturation, this.coverThreshold, this.coverRestTicks,
			this.retreatSaturation, this.chokeThreshold, this.chokeGroup, this.chokePatienceTicks, this.chokeRestTicks,
			this.behaviorLog, this.logSummaryTicks, this.calloutFx, this.callouts,
			this.moveSaturation, this.moveThreshold, this.interceptLeadTicks, this.critSaturation, this.chargeTighten,
			this.rlAlpha, this.rlEpsilon, this.rlWindowTicks);
	}

	public LearnConfig withBehaviorLog(final boolean value) {
		return new LearnConfig(this.calloutRadius, this.maxResponders, this.calloutCooldownTicks, this.profileHalfLifeTicks, this.rushBandGain,
			this.rushSaturation, this.shieldSaturation, this.shieldThreshold, this.shieldPatienceTicks, this.shieldRestTicks,
			this.rangedSaturation, this.coverThreshold, this.coverRestTicks,
			this.retreatSaturation, this.chokeThreshold, this.chokeGroup, this.chokePatienceTicks, this.chokeRestTicks,
			value, this.logSummaryTicks, this.calloutFx, this.callouts,
			this.moveSaturation, this.moveThreshold, this.interceptLeadTicks, this.critSaturation, this.chargeTighten,
			this.rlAlpha, this.rlEpsilon, this.rlWindowTicks);
	}

	public LearnConfig withCalloutFx(final boolean value) {
		return new LearnConfig(this.calloutRadius, this.maxResponders, this.calloutCooldownTicks, this.profileHalfLifeTicks, this.rushBandGain,
			this.rushSaturation, this.shieldSaturation, this.shieldThreshold, this.shieldPatienceTicks, this.shieldRestTicks,
			this.rangedSaturation, this.coverThreshold, this.coverRestTicks,
			this.retreatSaturation, this.chokeThreshold, this.chokeGroup, this.chokePatienceTicks, this.chokeRestTicks,
			this.behaviorLog, this.logSummaryTicks, value, this.callouts,
			this.moveSaturation, this.moveThreshold, this.interceptLeadTicks, this.critSaturation, this.chargeTighten,
			this.rlAlpha, this.rlEpsilon, this.rlWindowTicks);
	}

	public LearnConfig withCallouts(final boolean value) {
		return new LearnConfig(this.calloutRadius, this.maxResponders, this.calloutCooldownTicks, this.profileHalfLifeTicks, this.rushBandGain,
			this.rushSaturation, this.shieldSaturation, this.shieldThreshold, this.shieldPatienceTicks, this.shieldRestTicks,
			this.rangedSaturation, this.coverThreshold, this.coverRestTicks,
			this.retreatSaturation, this.chokeThreshold, this.chokeGroup, this.chokePatienceTicks, this.chokeRestTicks,
			this.behaviorLog, this.logSummaryTicks, this.calloutFx, value,
			this.moveSaturation, this.moveThreshold, this.interceptLeadTicks, this.critSaturation, this.chargeTighten,
			this.rlAlpha, this.rlEpsilon, this.rlWindowTicks);
	}

	public static LearnConfig defaults() {
		return new LearnConfig(
			DEFAULT_CALLOUT_RADIUS, DEFAULT_MAX_RESPONDERS, DEFAULT_CALLOUT_COOLDOWN_TICKS, DEFAULT_PROFILE_HALF_LIFE_TICKS,
			DEFAULT_RUSH_BAND_GAIN, DEFAULT_RUSH_SATURATION, DEFAULT_SHIELD_SATURATION, DEFAULT_SHIELD_THRESHOLD,
			DEFAULT_SHIELD_PATIENCE_TICKS, DEFAULT_SHIELD_REST_TICKS, DEFAULT_RANGED_SATURATION, DEFAULT_COVER_THRESHOLD,
			DEFAULT_COVER_REST_TICKS, DEFAULT_RETREAT_SATURATION, DEFAULT_CHOKE_THRESHOLD, DEFAULT_CHOKE_GROUP,
			DEFAULT_CHOKE_PATIENCE_TICKS, DEFAULT_CHOKE_REST_TICKS, DEFAULT_BEHAVIOR_LOG, DEFAULT_LOG_SUMMARY_TICKS,
			DEFAULT_CALLOUT_FX, DEFAULT_CALLOUTS, DEFAULT_MOVE_SATURATION, DEFAULT_MOVE_THRESHOLD, DEFAULT_INTERCEPT_LEAD_TICKS, DEFAULT_CRIT_SATURATION,
			DEFAULT_CHARGE_TIGHTEN,
			DEFAULT_RL_ALPHA, DEFAULT_RL_EPSILON, DEFAULT_RL_WINDOW_TICKS
		);
	}

	/** An exact entry wins over its default; a missing or bad entry keeps the default and a broken file gives them all. */
	public static LearnConfig parse(final String json) {
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				StellaLearn.LOGGER.warn("Learning config is not an object; using the defaults");
				return defaults();
			}
			root = parsed.getAsJsonObject();
		} catch (RuntimeException exception) {
			StellaLearn.LOGGER.warn("Learning config is not valid JSON; using the defaults: {}", exception.getMessage());
			return defaults();
		}

		return new LearnConfig(
			number(root, CALLOUT_RADIUS, DEFAULT_CALLOUT_RADIUS),
			(int) number(root, MAX_RESPONDERS, DEFAULT_MAX_RESPONDERS),
			(int) number(root, CALLOUT_COOLDOWN_TICKS, DEFAULT_CALLOUT_COOLDOWN_TICKS),
			(long) number(root, PROFILE_HALF_LIFE_TICKS, DEFAULT_PROFILE_HALF_LIFE_TICKS),
			number(root, RUSH_BAND_GAIN, DEFAULT_RUSH_BAND_GAIN),
			number(root, RUSH_SATURATION, DEFAULT_RUSH_SATURATION),
			number(root, SHIELD_SATURATION, DEFAULT_SHIELD_SATURATION),
			number(root, SHIELD_THRESHOLD, DEFAULT_SHIELD_THRESHOLD),
			(int) number(root, SHIELD_PATIENCE_TICKS, DEFAULT_SHIELD_PATIENCE_TICKS),
			(int) number(root, SHIELD_REST_TICKS, DEFAULT_SHIELD_REST_TICKS),
			number(root, RANGED_SATURATION, DEFAULT_RANGED_SATURATION),
			number(root, COVER_THRESHOLD, DEFAULT_COVER_THRESHOLD),
			(int) number(root, COVER_REST_TICKS, DEFAULT_COVER_REST_TICKS),
			number(root, RETREAT_SATURATION, DEFAULT_RETREAT_SATURATION),
			number(root, CHOKE_THRESHOLD, DEFAULT_CHOKE_THRESHOLD),
			(int) number(root, CHOKE_GROUP, DEFAULT_CHOKE_GROUP),
			(int) number(root, CHOKE_PATIENCE_TICKS, DEFAULT_CHOKE_PATIENCE_TICKS),
			(int) number(root, CHOKE_REST_TICKS, DEFAULT_CHOKE_REST_TICKS),
			flag(root, BEHAVIOR_LOG, DEFAULT_BEHAVIOR_LOG),
			(int) count(root, LOG_SUMMARY_TICKS, DEFAULT_LOG_SUMMARY_TICKS),
			flag(root, CALLOUT_FX, DEFAULT_CALLOUT_FX),
			flag(root, CALLOUTS, DEFAULT_CALLOUTS),
			number(root, MOVE_SATURATION, DEFAULT_MOVE_SATURATION),
			number(root, MOVE_THRESHOLD, DEFAULT_MOVE_THRESHOLD),
			(int) number(root, INTERCEPT_LEAD_TICKS, DEFAULT_INTERCEPT_LEAD_TICKS),
			number(root, CRIT_SATURATION, DEFAULT_CRIT_SATURATION),
			number(root, CHARGE_TIGHTEN, DEFAULT_CHARGE_TIGHTEN),
			number(root, RL_ALPHA, DEFAULT_RL_ALPHA),
			count(root, RL_EPSILON, DEFAULT_RL_EPSILON),
			(int) number(root, RL_WINDOW_TICKS, DEFAULT_RL_WINDOW_TICKS)
		);
	}

	private static boolean flag(final JsonObject root, final String key, final boolean fallback) {
		JsonElement element = root.get(key);
		if (element == null) {
			return fallback;
		}

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			StellaLearn.LOGGER.warn("Learning config entry {} must be true or false; keeping {}", key, fallback);
			return fallback;
		}

		return element.getAsBoolean();
	}

	/** Like `number`, but zero is a real value: it switches the thing off. */
	private static double count(final JsonObject root, final String key, final double fallback) {
		JsonElement element = root.get(key);
		if (element == null) {
			return fallback;
		}

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber() || element.getAsDouble() < 0.0) {
			StellaLearn.LOGGER.warn("Learning config entry {} must be zero or a positive number; keeping {}", key, fallback);
			return fallback;
		}

		return element.getAsDouble();
	}

	private static double number(final JsonObject root, final String key, final double fallback) {
		JsonElement element = root.get(key);
		if (element == null) {
			return fallback;
		}

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber() || element.getAsDouble() <= 0.0) {
			StellaLearn.LOGGER.warn("Learning config entry {} must be a positive number; keeping {}", key, fallback);
			return fallback;
		}

		return element.getAsDouble();
	}
}
