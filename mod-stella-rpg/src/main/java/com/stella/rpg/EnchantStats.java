package com.stella.rpg;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Stats from the enchantments of the worn armor and the held item, for each level.
 * The config key {@code enchant_stats} maps an enchantment name to a stat map. Only vanilla
 * enchantments and those of {@code mod-stella-equips-enchants} count. The name is the last part of the id path.
 */
public final class EnchantStats implements StatBonuses.Provider {
	public static final String EQUIPS_NAMESPACE = "mod-stella-equips-enchants";

	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	public static void register() {
		StatBonuses.register(new EnchantStats());
	}

	@Override
	public int bonus(final LivingEntity entity, final Stat stat) {
		long sum = 0;
		Map<String, Map<Stat, Integer>> table = RpgConfigStore.current().tables().enchantStats();
		for (EquipmentSlot slot : SLOTS) {
			sum += of(entity.getItemBySlot(slot), stat, table);
		}
		return (int) Math.max(-StatSheet.CEILING, Math.min(StatSheet.CEILING, sum));
	}

	/** The stat an id grants per level, or 0 when the id is unknown. */
	public static int perLevel(final Identifier id, final Stat stat, final Map<String, Map<Stat, Integer>> table) {
		if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) && !id.getNamespace().equals(EQUIPS_NAMESPACE)) {
			return 0;
		}
		Map<Stat, Integer> stats = table.get(id.toString());
		if (stats == null) {
			String path = id.getPath();
			stats = table.get(path.substring(path.lastIndexOf('/') + 1));
		}
		return stats == null ? 0 : stats.getOrDefault(stat, 0);
	}

	private static long of(final ItemStack stack, final Stat stat, final Map<String, Map<Stat, Integer>> table) {
		if (stack.isEmpty()) {
			return 0;
		}
		ItemEnchantments enchantments = stack.getEnchantments();
		if (enchantments.isEmpty()) {
			return 0;
		}
		long sum = 0;
		for (var entry : enchantments.entrySet()) {
			Holder<Enchantment> holder = entry.getKey();
			Identifier id = holder.unwrapKey().map(key -> key.identifier()).orElse(null);
			if (id != null) {
				sum += (long) perLevel(id, stat, table) * entry.getIntValue();
			}
		}
		return sum;
	}
}
