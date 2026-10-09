package com.stella.rpg.api;

import com.stella.rpg.Stat;
import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.BonusMath;
import com.stella.rpg.roll.WeaponRolls;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Publishes the stat-bonus grant under {@code mod-stella-rpg:grant-bonus}.
 * The value is a {@code Function<Object[], Boolean>} taking {@code {ItemStack stack, Map<String, Integer> delta}}.
 * A delta key is a stat key ({@code str agi vit dex int luk}) or an affix key, with a positive amount in stored units.
 * Each amount adds to the stack's {@code stat_rolls} or {@code affix_rolls}, so repeated calls stack.
 * One key stops at {@link BonusMath#KEY_CAP}.
 * A key is skipped when it is unknown, not positive, or an affix the gear kind cannot roll.
 * The call returns true when at least one key applied, and false for any other input. It never throws.
 * A gear stack with no {@code stat_rolls} is marked rolled first, so it forfeits its random roll.
 * It uses JDK and vanilla types only, so a reader needs no class from this mod.
 */
public final class BonusGrantShare {
	public static final String OBJECT_SHARE_KEY = "mod-stella-rpg:grant-bonus";

	private BonusGrantShare() {
	}

	public static void register() {
		Function<Object[], Boolean> share = BonusGrantShare::grant;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, share);
	}

	private static Boolean grant(final Object[] args) {
		if (args == null || args.length != 2 || !(args[0] instanceof ItemStack stack) || !(args[1] instanceof Map<?, ?> delta)) {
			return false;
		}
		Affix.Kind kind = stack.isEmpty() ? null : WeaponRolls.kindOf(stack);
		if (kind == null || delta.isEmpty()) {
			return false;
		}
		Map<String, Integer> stats = new LinkedHashMap<>(stack.getOrDefault(WeaponRolls.STAT_ROLLS, Map.of()));
		Map<String, Integer> affixes = new LinkedHashMap<>(stack.getOrDefault(WeaponRolls.AFFIX_ROLLS, Map.of()));
		boolean statsChanged = false;
		boolean affixesChanged = false;
		for (Map.Entry<?, ?> entry : delta.entrySet()) {
			if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Number amount) || amount.longValue() <= 0) {
				continue;
			}
			if (Stat.byKey(key).isPresent()) {
				stats.put(key, BonusMath.addClamped(stats.getOrDefault(key, 0), amount.longValue()));
				statsChanged = true;
			} else if (Affix.byKey(key).filter(affix -> affix.rollsOn(kind)).isPresent()) {
				affixes.put(key, BonusMath.addClamped(affixes.getOrDefault(key, 0), amount.longValue()));
				affixesChanged = true;
			}
		}
		if (!statsChanged && !affixesChanged) {
			return false;
		}
		// An empty stat map is the "rolled" mark that blocks the random roll.
		stack.set(WeaponRolls.STAT_ROLLS, Map.copyOf(stats));
		if (affixesChanged) {
			stack.set(WeaponRolls.AFFIX_ROLLS, Map.copyOf(affixes));
		}
		return true;
	}
}
