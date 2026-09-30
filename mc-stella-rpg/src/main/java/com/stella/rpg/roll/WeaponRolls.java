package com.stella.rpg.roll;

import com.mojang.serialization.Codec;
import com.stella.rpg.RpgConfig;
import com.stella.rpg.RpgConfigStore;
import com.stella.rpg.Stat;
import com.stella.rpg.StatBonuses;
import com.stella.rpg.StatSheet;
import com.stella.rpg.StellaRpg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

public final class WeaponRolls implements StatBonuses.Provider {
	public static final DataComponentType<Map<String, Integer>> STAT_ROLLS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		StellaRpg.id("stat_rolls"),
		DataComponentType.<Map<String, Integer>>builder()
			.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
			.networkSynchronized(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT))
			.build()
	);

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private record Entry(boolean armor, int min, int max) {
	}

	private record Index(RpgConfig config, Map<Identifier, Entry> byItem) {
	}

	private static volatile Index index;

	private WeaponRolls() {
	}

	public static void register() {
		StatBonuses.register(new WeaponRolls());
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			for (ItemStack stack : drops) {
				roll(stack, context.getRandom());
			}
		});
	}

	public static boolean roll(final ItemStack stack, final RandomSource random) {
		if (stack.isEmpty() || stack.has(STAT_ROLLS)) {
			return false;
		}
		Entry entry = index().byItem().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		if (entry == null) {
			return false;
		}
		int count = entry.armor() ? 1 : count(RpgConfigStore.current().weaponRolls().countWeights(), random);
		List<Stat> pool = new ArrayList<>(List.of(Stat.values()));
		Map<String, Integer> rolls = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			Stat stat = pool.remove(random.nextInt(pool.size()));
			rolls.put(stat.key(), entry.min() + random.nextInt(entry.max() - entry.min() + 1));
		}
		stack.set(STAT_ROLLS, Map.copyOf(rolls));
		return true;
	}

	public static void rollEquipment(final Mob mob) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			roll(mob.getItemBySlot(slot), mob.getRandom());
		}
	}

	@Override
	public int bonus(final LivingEntity entity, final Stat stat) {
		long sum = rolled(entity.getItemBySlot(EquipmentSlot.MAINHAND), false, stat);
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			sum += rolled(entity.getItemBySlot(slot), true, stat);
		}
		return (int) Math.min(StatSheet.CEILING, sum);
	}

	private static int rolled(final ItemStack stack, final boolean armor, final Stat stat) {
		Map<String, Integer> rolls = stack.isEmpty() ? null : stack.get(STAT_ROLLS);
		if (rolls == null) {
			return 0;
		}
		Entry entry = index().byItem().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		if (entry == null || entry.armor() != armor) {
			return 0;
		}
		return Math.max(0, rolls.getOrDefault(stat.key(), 0));
	}

	private static int count(final List<Integer> weights, final RandomSource random) {
		int total = weights.stream().mapToInt(Integer::intValue).sum();
		int pick = random.nextInt(total);
		for (int i = 0; i < weights.size(); i++) {
			pick -= weights.get(i);
			if (pick < 0) {
				return i + 1;
			}
		}
		return weights.size();
	}

	private static Index index() {
		RpgConfig config = RpgConfigStore.current();
		Index found = index;
		if (found == null || found.config() != config) {
			Map<Identifier, Entry> byItem = new HashMap<>();
			config.weaponRolls().tiers().forEach((name, tier) -> {
				tier.items().forEach(id -> byItem.putIfAbsent(id, new Entry(false, tier.min(), tier.max())));
				tier.armorItems().forEach(id -> byItem.putIfAbsent(id, new Entry(true, tier.min(), tier.max())));
			});
			found = new Index(config, Map.copyOf(byItem));
			index = found;
		}
		return found;
	}
}
