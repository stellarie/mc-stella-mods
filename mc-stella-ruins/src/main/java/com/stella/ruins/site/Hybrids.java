package com.stella.ruins.site;

import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.HybridBonus;
import com.stella.ruins.reward.RuinSets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemLore;

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
		// Pieces register before the hybrids exist, so their tooltips gain the hybrid lines through the item defaults.
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item -> RuinSets.setOf(item).isPresent(), (builder, item) -> {
			List<Component> lines = new ArrayList<>();
			ItemLore old = item.components().get(DataComponents.LORE);
			if (old != null) {
				lines.addAll(old.lines());
			}
			lines.addAll(hybridLines(RuinSets.setOf(item).orElseThrow()));
			builder.set(DataComponents.LORE, new ItemLore(lines));
		}));
	}

	/** One tooltip line for each hybrid bonus that a set takes part in. */
	public static List<Component> hybridLines(final String setId) {
		List<Component> lines = new ArrayList<>();
		for (HybridBonus hybrid : RuinSets.hybrids()) {
			if (!hybrid.requires().containsKey(setId)) {
				continue;
			}
			Component partners = Component.empty();
			boolean first = true;
			for (String other : hybrid.requires().keySet()) {
				if (other.equals(setId)) {
					continue;
				}
				Component name = Component.translatableWithFallback("set_name.mod-stella-ruins." + other, other);
				partners = first ? name : Component.empty().append(partners).append(Component.literal(", ")).append(name);
				first = false;
			}
			Component bonus = Component.empty();
			for (int i = 0; i < hybrid.attributes().size(); i++) {
				Component part = hybrid.attributes().get(i).describe();
				bonus = i == 0 ? part : Component.empty().append(bonus).append(Component.literal(", ")).append(part);
			}
			lines.add(Component.translatable("ruins.reward.set.hybrid", partners, bonus).withStyle(ChatFormatting.DARK_AQUA));
		}
		return lines;
	}

	private static void pair(final String first, final String second, final AttributeBonus... bonuses) {
		RuinSets.defineHybrid(new HybridBonus(first + "_" + second, Map.of(first, 2, second, 2), List.of(bonuses), null));
	}
}
