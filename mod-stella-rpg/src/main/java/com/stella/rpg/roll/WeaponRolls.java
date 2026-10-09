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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
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
	/** Affix key to stored value. An item rolled before affixes existed has none. */
	public static final DataComponentType<Map<String, Integer>> AFFIX_ROLLS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		StellaRpg.id("affix_rolls"),
		DataComponentType.<Map<String, Integer>>builder()
			.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
			.networkSynchronized(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT))
			.build()
	);
	/** Items that roll only MATK affixes, at the diamond tier. Empty until a mod lists its books. */
	public static final TagKey<Item> SPELLBOOKS = TagKey.create(Registries.ITEM, StellaRpg.id("spellbooks"));
	/** Armor in this tag rolls its count from the weapon weights, like a weapon. */
	public static final TagKey<Item> MULTI_ROLL = TagKey.create(Registries.ITEM, StellaRpg.id("multi_roll"));
	public static final String BOOK_TIER = "diamond";

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private record Entry(Affix.Kind kind, int min, int max, Map<Affix, RpgConfig.AffixRange> affixes) {
	}

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
		Entry entry = entryOf(stack);
		if (entry == null) {
			return false;
		}
		RpgConfig.WeaponRolls config = RpgConfigStore.current().weaponRolls();
		boolean book = entry.kind() == Affix.Kind.BOOK;
		boolean single = entry.kind() == Affix.Kind.ARMOR && !stack.is(MULTI_ROLL);
		int count = single ? 1 : count(config.countWeights(), random);
		List<Stat> stats = new ArrayList<>(book ? List.of(Stat.INT) : List.of(Stat.values()));
		List<Affix> affixes = new ArrayList<>(Affix.pool(entry.kind()));
		if (!stack.is(ItemTags.FOOT_ARMOR)) {
			affixes.remove(Affix.MOVE_SPEED_PCT);
		}
		Map<String, Integer> statRolls = new LinkedHashMap<>();
		Map<String, Integer> affixRolls = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			boolean affix = !affixes.isEmpty() && (stats.isEmpty() || random.nextDouble() < config.affixChance());
			if (affix) {
				Affix picked = affixes.remove(random.nextInt(affixes.size()));
				affixRolls.put(picked.key(), rollAffix(picked, entry.affixes().get(picked), random));
			} else if (!stats.isEmpty()) {
				Stat stat = stats.remove(random.nextInt(stats.size()));
				statRolls.put(stat.key(), entry.min() + random.nextInt(entry.max() - entry.min() + 1));
			}
		}
		stack.set(STAT_ROLLS, Map.copyOf(statRolls));
		if (!affixRolls.isEmpty()) {
			stack.set(AFFIX_ROLLS, Map.copyOf(affixRolls));
		}
		return true;
	}

	/** Gives unrolled gear an empty stat map. The empty map blocks every later random roll. */
	public static boolean markRolled(final ItemStack stack) {
		if (stack.isEmpty() || stack.has(STAT_ROLLS) || kindOf(stack) == null) {
			return false;
		}
		stack.set(STAT_ROLLS, Map.of());
		return true;
	}

	private static int rollAffix(final Affix affix, final RpgConfig.AffixRange range, final RandomSource random) {
		int scale = affix.percent() ? Affix.TENTHS : 1;
		int low = (int) Math.round(range.min() * scale);
		int high = (int) Math.round(range.max() * scale);
		return low + random.nextInt(high - low + 1);
	}

	/** Rolls every unrolled rollable stack a player holds or wears. Catches trades, rewards, commands, and smithing. */
	public static void rollPossessed(final ServerPlayer player) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			roll(stack, player.getRandom());
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			roll(player.getItemBySlot(slot), player.getRandom());
		}
	}

	public static void rollEquipment(final Mob mob) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			roll(mob.getItemBySlot(slot), mob.getRandom());
		}
	}

	/** The kind of roll this stack carries, or null when it never rolls. */
	public static Affix.Kind kindOf(final ItemStack stack) {
		Entry entry = stack.isEmpty() ? null : entryOf(stack);
		return entry == null ? null : entry.kind();
	}

	@Override
	public int bonus(final LivingEntity entity, final Stat stat) {
		long sum = rolled(entity.getItemBySlot(EquipmentSlot.MAINHAND), Affix.Kind.WEAPON, stat);
		sum += rolled(entity.getItemBySlot(EquipmentSlot.OFFHAND), Affix.Kind.ARMOR, stat);
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			sum += rolled(entity.getItemBySlot(slot), Affix.Kind.ARMOR, stat);
		}
		return (int) Math.min(StatSheet.CEILING, sum);
	}

	private static int rolled(final ItemStack stack, final Affix.Kind kind, final Stat stat) {
		Map<String, Integer> rolls = stack.isEmpty() ? null : stack.get(STAT_ROLLS);
		if (rolls == null || kindOf(stack) != kind) {
			return 0;
		}
		return Math.max(0, rolls.getOrDefault(stat.key(), 0));
	}

	private static Entry entryOf(final ItemStack stack) {
		Map<String, RpgConfig.RollTier> tiers = RpgConfigStore.current().weaponRolls().tiers();
		ItemTiers.Info info = ItemTiers.of(stack);
		if (info != null && tiers.containsKey(info.tier())) {
			RpgConfig.RollTier tier = tiers.get(info.tier());
			return new Entry(info.kind(), tier.min(), tier.max(), tier.affixes());
		}
		if (stack.is(SPELLBOOKS)) {
			RpgConfig.RollTier tier = tiers.get(BOOK_TIER);
			return new Entry(Affix.Kind.BOOK, tier.min(), tier.max(), tier.affixes());
		}
		return null;
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
}
