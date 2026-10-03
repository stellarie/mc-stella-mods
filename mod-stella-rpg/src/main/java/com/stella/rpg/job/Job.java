package com.stella.rpg.job;

import com.stella.rpg.Stat;
import java.util.Locale;
import java.util.Optional;

/** A job grows its main stat by 2 and its second stat by 1 for each level. */
public enum Job {
	NONE(null, null),
	ARCHER(Stat.DEX, Stat.AGI),
	FIGHTER(Stat.STR, Stat.VIT),
	MAGE(Stat.INT, Stat.DEX),
	PRIEST(Stat.VIT, Stat.INT);

	public static final int MAIN_PER_LEVEL = 2;
	public static final int SECOND_PER_LEVEL = 1;

	private final Stat main;
	private final Stat second;

	Job(final Stat main, final Stat second) {
		this.main = main;
		this.second = second;
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
