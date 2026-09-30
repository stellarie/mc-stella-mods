package com.stella.rpg;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ArmorSets implements StatBonuses.Provider, DefenseBonuses.Provider {
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private record Index(RpgConfig config, Map<Identifier, String> byItem) {
	}

	private static volatile Index index;

	public static void register() {
		ArmorSets sets = new ArmorSets();
		StatBonuses.register(sets);
		DefenseBonuses.register(sets);
	}

	public static String materialOf(final ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		return index().byItem().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	@Override
	public int bonus(final LivingEntity entity, final Stat stat) {
		RpgConfig config = RpgConfigStore.current();
		long sum = 0;
		String shared = null;
		boolean uniform = true;
		for (EquipmentSlot slot : SLOTS) {
			String material = materialOf(entity.getItemBySlot(slot));
			if (material == null || shared != null && !material.equals(shared)) {
				uniform = false;
			}
			if (material == null) {
				continue;
			}
			shared = shared == null ? material : shared;
			sum += config.armorSets().get(material).perPiece().getOrDefault(stat, 0);
		}
		if (uniform && shared != null) {
			sum += config.armorSets().get(shared).setBonus().getOrDefault(stat, 0);
		}
		return (int) Math.min(StatSheet.CEILING, sum);
	}

	@Override
	public float flat(final LivingEntity entity) {
		RpgConfig config = RpgConfigStore.current();
		double sum = 0.0;
		for (EquipmentSlot slot : SLOTS) {
			String material = materialOf(entity.getItemBySlot(slot));
			if (material != null) {
				sum += config.armorSets().get(material).flatPerPiece();
			}
		}
		return (float) sum;
	}

	private static Index index() {
		RpgConfig config = RpgConfigStore.current();
		Index found = index;
		if (found == null || found.config() != config) {
			Map<Identifier, String> byItem = new HashMap<>();
			config.armorSets().forEach((material, set) -> set.items().forEach(id -> byItem.putIfAbsent(id, material)));
			found = new Index(config, Map.copyOf(byItem));
			index = found;
		}
		return found;
	}
}
