package com.stella.ruins.world;

/** The level of a site mob: the area level at the core, plus bonuses for the site and the kind. */
public final class SiteLevels {
	private SiteLevels() {
	}

	/** The level a mob of a site takes from the area level, the site size, the tier, and whether its kind is a boss. */
	public static int level(final int area, final SiteSize size, final int tier, final boolean boss, final RuinConfig.Scaling scaling) {
		int bonus = (size == SiteSize.LARGE ? scaling.largeBonus() : 0) + Math.max(0, tier) * scaling.tierLevels() + (boss ? scaling.bossBonus() : 0);
		return area + bonus;
	}
}
