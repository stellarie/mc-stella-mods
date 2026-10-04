package com.stella.ruins.reward;

/** Materials, set pieces, affixes, built gear and unique effects. */
public final class RuinsRewards {
	private RuinsRewards() {
	}

	public static void register() {
		RuinsTab.register();
		RuinAffixes.register();
		RuinUniques.register();
		RuinSets.register();
		UniqueEffects.register();
	}
}
