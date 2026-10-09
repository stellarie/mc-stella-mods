package com.stella.ruins.site;

import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.HybridBonus;
import com.stella.ruins.reward.RuinSets;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Bonuses for two pieces of one set and two of another. Each pair joins sets from two sites, so a full kit
 * takes a visit to both. The sets must be defined first.
 */
public final class Hybrids {
	private Hybrids() {
	}

	public static void register() {
		pair("pilgrim", "champion", AttributeBonus.add(Attributes.MAX_HEALTH, 4.0));
		pair("hawkeye", "starweave", AttributeBonus.add(Attributes.SAFE_FALL_DISTANCE, 3.0), AttributeBonus.multiplyBase(Attributes.JUMP_STRENGTH, 0.10));
		pair("silkweave", "deepsteel", AttributeBonus.add(Attributes.ARMOR_TOUGHNESS, 1.0), AttributeBonus.multiplyBase(Attributes.MOVEMENT_SPEED, 0.05));
		pair("pilgrim", "hawkeye", AttributeBonus.multiplyBase(Attributes.MOVEMENT_SPEED, 0.05), AttributeBonus.add(Attributes.LUCK, 1.0));
		pair("champion", "deepsteel", AttributeBonus.add(Attributes.ATTACK_DAMAGE, 1.0));
		pair("starweave", "silkweave", AttributeBonus.multiplyBase(Attributes.ATTACK_SPEED, 0.08));
	}

	private static void pair(final String first, final String second, final AttributeBonus... bonuses) {
		RuinSets.defineHybrid(new HybridBonus(first + "_" + second, Map.of(first, 2, second, 2), List.of(bonuses), null));
	}
}
