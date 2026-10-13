package com.stella.rpg.job;

import com.stella.rpg.Stat;
import java.util.Locale;
import java.util.Optional;

/** A job grows its main stat by 2 and its second stat by 1 for each level, and grants one innate skill. */
public enum Job {
	NONE(null, null, 1.0, Innate.NONE),
	ARCHER(Stat.DEX, Stat.AGI, 1.0, Innate.FALCON_GUARD),
	FIGHTER(Stat.STR, Stat.VIT, 1.3, Innate.DOUBLE_STRIKE),
	MAGE(Stat.INT, Stat.DEX, 0.85, Innate.ARCANE_WARD),
	PRIEST(Stat.VIT, Stat.INT, 1.1, Innate.SANCTUARY);

	public static final int MAIN_PER_LEVEL = 2;
	public static final int SECOND_PER_LEVEL = 1;

	private final Stat main;
	private final Stat second;
	private final double hpFactor;
	private final Innate innate;

	Job(final Stat main, final Stat second, final double hpFactor, final Innate innate) {
		this.main = main;
		this.second = second;
		this.hpFactor = hpFactor;
		this.innate = innate;
	}

	/** The innate skill this job grants. */
	public Innate innate() {
		return this.innate;
	}

	/** The default max health factor. The config key hp.job_factors overrides it. */
	public double hpFactor() {
		return this.hpFactor;
	}

	/** Null for {@link #NONE}. */
	public Stat mainStat() {
		return this.main;
	}

	/** Null for {@link #NONE}. */
	public Stat secondStat() {
		return this.second;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public int perLevel(final Stat stat) {
		if (stat == this.main) {
			return MAIN_PER_LEVEL;
		}
		return stat == this.second ? SECOND_PER_LEVEL : 0;
	}

	public static Optional<Job> byKey(final String key) {
		for (Job job : values()) {
			if (job.key().equalsIgnoreCase(key)) {
				return Optional.of(job);
			}
		}
		return Optional.empty();
	}
}
