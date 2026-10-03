package com.stella.villages.mission;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * What the duke's elite, kill, and guard quests pay besides their emeralds. A mob type has a tier, from its
 * vanilla health and damage. The tier, elite status, and the mob's threat level set a budget in value points
 * (one iron ingot is one point). The budget buys stacks from a weighted pool: ores and ingots, enchanted
 * gear, enchanted books, and cooked food tipped with a beneficial effect. No reward holds netherite.
 */
public final class QuestRewards {
	public static final int MIN_TIER = 1;
	/** The highest ordinary tier. The ravager, evoker, and elder guardian sit here. */
	public static final int MAX_TIER = 5;
	/** Budget in value points by tier; index 0 is unused. */
	private static final int[] TIER_BUDGET = {0, 60, 90, 140, 220, 350};
	/** Upper bounds of tiers 1 to 4 on health plus three times attack damage. */
	private static final double[] TIER_POWER_LIMIT = {25.0, 40.0, 70.0, 120.0};
	private static final double DAMAGE_WEIGHT = 3.0;
	private static final Map<Identifier, Integer> TIER_FLOORS = Map.of(
		Identifier.parse("minecraft:ravager"), MAX_TIER,
		Identifier.parse("minecraft:evoker"), MAX_TIER,
		Identifier.parse("minecraft:elder_guardian"), MAX_TIER
	);
	public static final double ELITE_FACTOR = 2.0;
	/** A threat level past this adds nothing, so a far quest cannot flood the reward. */
	public static final int THREAT_CAP = 150;
	public static final int THREAT_DIVISOR = 50;

	private static final int MAX_PICKS = 6;
	private static final double MIN_SPEND = 10.0;
	private static final double MIN_SHARE = 0.35;
	private static final double SHARE_SPAN = 0.3;
	private static final double GEAR_MIN_BUDGET = 30.0;
	private static final double BOOK_MIN_BUDGET = 20.0;
	private static final double FOOD_MIN_BUDGET = 10.0;
	private static final double DIAMOND_GEAR_MIN_BUDGET = 70.0;
	private static final double IRON_GEAR_VALUE = 14.0;
	private static final double DIAMOND_GEAR_VALUE = 40.0;
	private static final double BOOK_VALUE = 6.0;
	private static final double ENCHANT_LEVEL_VALUE = 3.0;
	private static final double TIP_VALUE = 2.0;
	private static final int MIN_ENCHANT_COST = 5;
	private static final int MAX_ENCHANT_COST = 30;
	private static final int MIN_FOOD = 4;
	private static final int MAX_FOOD = 32;
	private static final int TIP_BASE_SECONDS = 30;
	private static final int TIP_SECONDS_PER_TIER = 15;
	private static final int STRONG_TIP_TIER = 4;

	private enum Category {
		ORE(40), GEAR(20), BOOK(20), FOOD(20);

		private final int weight;

		Category(final int weight) {
			this.weight = weight;
		}
	}

	private record Weighted<T>(T value, int weight) {
	}

	private static final List<Weighted<Item>> ORES = List.of(
		new Weighted<>(Items.IRON_INGOT, 4), new Weighted<>(Items.GOLD_INGOT, 4), new Weighted<>(Items.LAPIS_LAZULI, 4),
		new Weighted<>(Items.EMERALD, 3), new Weighted<>(Items.AMETHYST_SHARD, 3), new Weighted<>(Items.DIAMOND, 1)
	);
	private static final Map<Item, Double> ORE_VALUES = Map.of(
		Items.IRON_INGOT, 1.0, Items.GOLD_INGOT, 2.0, Items.LAPIS_LAZULI, 0.5,
		Items.EMERALD, 2.0, Items.AMETHYST_SHARD, 1.5, Items.DIAMOND, 10.0
	);
	private static final List<Item> IRON_GEAR = List.of(
		Items.IRON_SWORD, Items.IRON_AXE, Items.BOW, Items.CROSSBOW, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS
	);
	private static final List<Item> DIAMOND_GEAR = List.of(
		Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS
	);
	public static final List<Item> FOODS = List.of(
		Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON,
		Items.BAKED_POTATO, Items.COOKED_SALMON, Items.COOKED_COD
	);
	private static final List<Holder<MobEffect>> TIPS = List.of(
		MobEffects.REGENERATION, MobEffects.SPEED, MobEffects.HASTE, MobEffects.RESISTANCE, MobEffects.FIRE_RESISTANCE
	);
	/** Effects whose second level is still harmless; the rest stay at the first level. */
	private static final List<Holder<MobEffect>> STRONG_TIPS = List.of(MobEffects.REGENERATION, MobEffects.SPEED, MobEffects.HASTE);

	private QuestRewards() {
	}

	/** The mob type's tier from 1 to {@link #MAX_TIER}: its vanilla power, raised to the floor of a feared type. */
	public static int tierOf(final EntityType<?> type) {
		int derived = MIN_TIER;
		double power = power(type);
		while (derived < MAX_TIER && power >= TIER_POWER_LIMIT[derived - 1]) {
			derived++;
		}
		return Math.max(derived, TIER_FLOORS.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(type), MIN_TIER));
	}

	@SuppressWarnings("unchecked")
	private static double power(final EntityType<?> type) {
		if (!DefaultAttributes.hasSupplier(type)) {
			return 0.0;
		}
		AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
		double health = supplier.hasAttribute(Attributes.MAX_HEALTH) ? supplier.getBaseValue(Attributes.MAX_HEALTH) : 0.0;
		double attack = supplier.hasAttribute(Attributes.ATTACK_DAMAGE) ? supplier.getBaseValue(Attributes.ATTACK_DAMAGE) : 0.0;
		return health + DAMAGE_WEIGHT * attack;
	}

	/** Value points: the tier's base, doubled for an elite, scaled by the threat level up to its cap. */
	public static int budget(final int tier, final boolean elite, final int threatLevel) {
		double threat = 1.0 + Math.min(Math.max(threatLevel, 0), THREAT_CAP) / (double) THREAT_DIVISOR;
		int clamped = Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
		return (int) Math.round(TIER_BUDGET[clamped] * (elite ? ELITE_FACTOR : 1.0) * threat);
	}

	public static List<ItemStack> roll(final ServerLevel level, final EntityType<?> type, final boolean elite, final int threatLevel, final RandomSource random) {
		int tier = tierOf(type);
		double remaining = budget(tier, elite, threatLevel);
		List<ItemStack> reward = new ArrayList<>();
		for (int pick = 0; pick < MAX_PICKS && remaining >= MIN_SPEND; pick++) {
			double share = pick == MAX_PICKS - 1 ? remaining : remaining * (MIN_SHARE + SHARE_SPAN * random.nextDouble());
			List<ItemStack> bought = switch (pickCategory(remaining, random)) {
				case ORE -> ore(share, random);
				case GEAR -> gear(level, share, random);
				case BOOK -> book(level, share, random);
				case FOOD -> food(share, tier, random);
			};
			double spent = 0.0;
			for (ItemStack stack : bought) {
				spent += value(stack);
			}
			if (spent <= 0.0) {
				break;
			}
			remaining -= spent;
			reward.addAll(bought);
		}
		return reward;
	}

	private static Category pickCategory(final double remaining, final RandomSource random) {
		List<Weighted<Category>> open = new ArrayList<>();
		for (Category category : Category.values()) {
			double minimum = switch (category) {
				case GEAR -> GEAR_MIN_BUDGET;
				case BOOK -> BOOK_MIN_BUDGET;
				case FOOD -> FOOD_MIN_BUDGET;
				case ORE -> 0.0;
			};
			if (remaining >= minimum) {
				open.add(new Weighted<>(category, category.weight));
			}
		}
		return choose(open, random);
	}

	private static <T> T choose(final List<Weighted<T>> options, final RandomSource random) {
		int total = options.stream().mapToInt(Weighted::weight).sum();
		int roll = random.nextInt(total);
		for (Weighted<T> option : options) {
			roll -= option.weight();
			if (roll < 0) {
				return option.value();
			}
		}
		return options.getLast().value();
	}

	private static List<ItemStack> ore(final double share, final RandomSource random) {
		List<Weighted<Item>> affordable = ORES.stream().filter(entry -> ORE_VALUES.get(entry.value()) <= share).toList();
		Item item = choose(affordable, random);
		return split(item, Math.max(1, (int) (share / ORE_VALUES.get(item))));
	}

	private static List<ItemStack> split(final Item item, final int count) {
		List<ItemStack> stacks = new ArrayList<>();
		int left = count;
		while (left > 0) {
			int size = Math.min(left, item.getDefaultMaxStackSize());
			stacks.add(new ItemStack(item, size));
			left -= size;
		}
		return stacks;
	}

	private static List<ItemStack> gear(final ServerLevel level, final double share, final RandomSource random) {
		boolean diamond = share >= DIAMOND_GEAR_MIN_BUDGET && random.nextBoolean();
		List<Item> pool = diamond ? DIAMOND_GEAR : IRON_GEAR;
		Item item = pool.get(random.nextInt(pool.size()));
		double base = diamond ? DIAMOND_GEAR_VALUE : IRON_GEAR_VALUE;
		int cost = enchantCost(share - base);
		ItemStack stack = EnchantmentHelper.enchantItem(random, new ItemStack(item), cost, level.registryAccess(), Optional.empty());
		return List.of(stack);
	}

	private static List<ItemStack> book(final ServerLevel level, final double share, final RandomSource random) {
		int cost = enchantCost(share - BOOK_VALUE);
		return List.of(EnchantmentHelper.enchantItem(random, new ItemStack(Items.BOOK), cost, level.registryAccess(), Optional.empty()));
	}

	/** The table cost for the room left in the share: half of it, within the table's own range. */
	private static int enchantCost(final double room) {
		return Math.max(MIN_ENCHANT_COST, Math.min(MAX_ENCHANT_COST, (int) (room / 2.0)));
	}

	private static List<ItemStack> food(final double share, final int tier, final RandomSource random) {
		Item item = FOODS.get(random.nextInt(FOODS.size()));
		int tips = tier >= STRONG_TIP_TIER && random.nextBoolean() ? 2 : 1;
		List<Holder<MobEffect>> pool = new ArrayList<>(TIPS);
		List<MobEffectInstance> effects = new ArrayList<>();
		for (int tip = 0; tip < tips; tip++) {
			Holder<MobEffect> effect = pool.remove(random.nextInt(pool.size()));
			int amplifier = tier >= STRONG_TIP_TIER && STRONG_TIPS.contains(effect) ? 1 : 0;
			effects.add(new MobEffectInstance(effect, (TIP_BASE_SECONDS + TIP_SECONDS_PER_TIER * tier) * 20, amplifier));
		}
		int count = Math.max(MIN_FOOD, Math.min(MAX_FOOD, (int) (share / (1.0 + TIP_VALUE * effects.size()))));
		return List.of(tipped(item, count, effects));
	}

	/** Cooked food that also applies the effects when eaten; the food's own eating effects stay. */
	static ItemStack tipped(final Item food, final int count, final List<MobEffectInstance> effects) {
		ItemStack stack = new ItemStack(food, count);
		Consumable base = stack.getOrDefault(DataComponents.CONSUMABLE, Consumables.DEFAULT_FOOD);
		List<ConsumeEffect> consumed = new ArrayList<>(base.onConsumeEffects());
		consumed.add(new ApplyStatusEffectsConsumeEffect(effects));
		stack.set(DataComponents.CONSUMABLE, new Consumable(base.consumeSeconds(), base.animation(), base.sound(), base.hasConsumeParticles(), consumed));
		List<Component> lore = new ArrayList<>();
		for (MobEffectInstance effect : effects) {
			lore.add(Component.empty().append(effect.getEffect().value().getDisplayName()));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	/** The value points of a stack: what the budget pays for it. */
	public static double value(final ItemStack stack) {
		Item item = stack.getItem();
		double each;
		if (stack.is(Items.ENCHANTED_BOOK)) {
			each = BOOK_VALUE + ENCHANT_LEVEL_VALUE * levels(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
		} else if (stack.isEnchanted()) {
			each = (DIAMOND_GEAR.contains(item) ? DIAMOND_GEAR_VALUE : IRON_GEAR_VALUE)
				+ ENCHANT_LEVEL_VALUE * levels(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		} else if (stack.has(DataComponents.CONSUMABLE)) {
			each = 1.0 + TIP_VALUE * tips(stack);
		} else {
			each = ORE_VALUES.getOrDefault(item, 1.0);
		}
		return each * stack.getCount();
	}

	private static int levels(final ItemEnchantments enchantments) {
		return enchantments.entrySet().stream().mapToInt(Object2IntMap.Entry::getIntValue).sum();
	}

	private static int tips(final ItemStack stack) {
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		if (consumable == null) {
			return 0;
		}
		return consumable.onConsumeEffects().stream()
			.filter(ApplyStatusEffectsConsumeEffect.class::isInstance)
			.mapToInt(effect -> ((ApplyStatusEffectsConsumeEffect) effect).effects().size()).sum();
	}
}
