package com.stella.equips.merchant;

import java.util.List;
import java.util.Map;

/**
 * One merchant weapon. {@code stats} and {@code affixes} are the fixed rolls the weapon carries from creation, under
 * the keys of mod-stella-rpg: a stat key, or an affix key whose percent value is stored in tenths of a percent.
 * {@code effect} and {@code milestones} are the tooltip lines of the effect and of the +7, +10, +12 and +15 bonuses.
 */
public record MerchantDef(String id, String name, MerchantKind kind, Map<String, Integer> stats, Map<String, Integer> affixes,
	List<String> effect, String milestones) {
}
