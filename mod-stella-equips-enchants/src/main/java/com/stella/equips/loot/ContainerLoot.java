package com.stella.equips.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Oniichan's container table. Each roll is its own draw on top of the vanilla loot: the jackpot, enchanted
 * equipment, ordinary equipment, food, a potion, and emeralds. Gear material follows the container level, the
 * mob floor at the chest from the mobs mod. Every enchantment is one the item can take, and none is a curse.
 */
public final class ContainerLoot {
	public static final double JACKPOT_CHANCE = 0.0001;
	public static final double ENCHANTED_CHANCE = 0.01;
	public static final double ORDINARY_CHANCE = 0.30;
	public static final double FOOD_CHANCE = 0.25;
	public static final double POTION_CHANCE = 0.05;
	public static final double EMERALD_CHANCE = 0.05;
	/** Each extra enchantment on enchanted equipment is a further draw at this chance. */
	public static final double EXTRA_ENCHANT_CHANCE = 0.02;
	public static final int JACKPOT_ENCHANTS = 5;
	public static final int COPPER_LEVEL = 10;
	public static final int IRON_LEVEL = 20;
	public static final int DIAMOND_LEVEL = 50;
	public static final int NETHERITE_LEVEL = 80;
	/** From the diamond and the netherite level up, that tier appears at this chance. */
	public static final double RARE_TIER_CHANCE = 0.05;
	/** min(64, 1 + floor(X)) with X exponential at this mean gives a mean of 16. */
	public static final double EMERALD_EXPONENTIAL_MEAN = 15.777;
	public static final int FOOD_MIN = 2;
	public static final int FOOD_MAX = 5;
	public static final String LEVEL_SHARE = "mod-stella-mobs:container-level";
	public static final int FALLBACK_OFFSET = 2;
	private static final TagKey<Item> SPELLBOOKS = TagKey.create(
		Registries.ITEM, Identifier.parse("mod-stella-spells:spellbooks")
	);
	private static final List<String> WEAPONS = List.of("sword", "axe", "spear");
	private static final List<String> TOOLS = List.of("pickaxe", "shovel", "hoe");
	private static final List<String> ARMOR = List.of("helmet", "chestplate", "leggings", "boots");
	private static final List<Item> FOODS = List.of(
		Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON,
		Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BAKED_POTATO, Items.APPLE,
		Items.CARROT, Items.PUMPKIN_PIE, Items.COOKIE, Items.GOLDEN_CARROT
	);
	private static final Set<Holder<Potion>> BASE_POTIONS = Set.of(Potions.WATER, Potions.AWKWARD, Potions.MUNDANE, Potions.THICK);

	public enum Tier {
		STONE("stone", "leather"),
		COPPER("copper", "copper"),
		IRON("iron", "iron"),
		DIAMOND("diamond", "diamond"),
		NETHERITE("netherite", "netherite");

		private final String tools;
		private final String armor;

		Tier(final String tools, final String armor) {
			this.tools = tools;
			this.armor = armor;
		}

		public String prefix(final String piece) {
			return ARMOR.contains(piece) ? this.armor : this.tools;
		}
	}

	private ContainerLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey()
			.filter(key -> ChestLoot.targetsChest(key) && !ChestLoot.isBonusChest(key))
			.ifPresent(key -> drops.addAll(roll(
				context.getLevel(), context.getOptional(LootContextParams.ORIGIN),
				context.getOptional(LootContextParams.THIS_ENTITY), context.getRandom()
			))));
	}

	/** The container level: the mobs share at the spot, else the opener's level minus 2, else 0. */
	@SuppressWarnings("unchecked")
	public static int level(final ServerLevel level, final Vec3 origin, final Entity opener) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(LEVEL_SHARE);
		if (origin != null && shared instanceof BiFunction<?, ?, ?> function) {
			Integer value = ((BiFunction<ServerLevel, Vec3, Integer>) function).apply(level, origin);
			if (value != null) {
				return Math.max(0, value);
			}
		}
		return opener instanceof Player player ? Math.max(0, player.experienceLevel - FALLBACK_OFFSET) : 0;
	}

	public static List<ItemStack> roll(final ServerLevel level, final Vec3 origin, final Entity opener, final RandomSource random) {
		int containerLevel = level(level, origin, opener);
		List<ItemStack> found = new ArrayList<>();
		if (random.nextDouble() < JACKPOT_CHANCE) {
			found.add(jackpot(level, random));
		}
		if (random.nextDouble() < ENCHANTED_CHANCE) {
			found.add(enchanted(level, containerLevel, random));
		}
		if (random.nextDouble() < ORDINARY_CHANCE) {
			found.add(new ItemStack(gear(tier(containerLevel, random), random)));
		}
		if (random.nextDouble() < FOOD_CHANCE) {
			found.add(new ItemStack(FOODS.get(random.nextInt(FOODS.size())), Mth.nextInt(random, FOOD_MIN, FOOD_MAX)));
		}
		if (random.nextDouble() < POTION_CHANCE) {
			found.add(potion(random));
		}
		if (random.nextDouble() < EMERALD_CHANCE) {
			found.add(new ItemStack(Items.EMERALD, emeraldCount(random)));
		}
		return found;
	}

	/** 0-9 stone and leather, 10-19 copper, 20-49 iron; diamond at 5% from 50, netherite at 5% from 80. */
	public static Tier tier(final int level, final RandomSource random) {
		if (level < COPPER_LEVEL) {
			return Tier.STONE;
		}
		if (level < IRON_LEVEL) {
			return Tier.COPPER;
		}
		if (level >= NETHERITE_LEVEL && random.nextDouble() < RARE_TIER_CHANCE) {
			return Tier.NETHERITE;
		}
		if (level >= DIAMOND_LEVEL && random.nextDouble() < RARE_TIER_CHANCE) {
			return Tier.DIAMOND;
		}
		return Tier.IRON;
	}

	public static int emeraldCount(final RandomSource random) {
		double exponential = -Math.log(1.0 - random.nextDouble()) * EMERALD_EXPONENTIAL_MEAN;
		return (int) Math.min(64L, 1L + (long) Math.floor(exponential));
	}

	public static ItemStack jackpot(final ServerLevel level, final RandomSource random) {
		Tier tier = random.nextBoolean() ? Tier.DIAMOND : Tier.NETHERITE;
		ItemStack stack = new ItemStack(gear(tier, random));
		enchant(level, stack, JACKPOT_ENCHANTS, random);
		return stack;
	}

	/** A weapon, tool, armor piece or spellbook with one enchantment and a 2% chain for each extra one. */
	public static ItemStack enchanted(final ServerLevel level, final int containerLevel, final RandomSource random) {
		Optional<HolderSet.Named<Item>> books = BuiltInRegistries.ITEM.get(SPELLBOOKS);
		boolean book = books.isPresent() && books.get().size() > 0 && random.nextInt(4) == 0;
		ItemStack stack = book
			? new ItemStack(books.get().get(random.nextInt(books.get().size())))
			: new ItemStack(gear(tier(containerLevel, random), random));
		int count = 1;
		while (random.nextDouble() < EXTRA_ENCHANT_CHANCE) {
			count++;
		}
		enchant(level, stack, count, random);
		return stack;
	}

	/** One piece of a tier: a weapon, a tool or armor, each group equally likely. Missing items fall back to iron. */
	public static Item gear(final Tier tier, final RandomSource random) {
		List<String> group = List.of(WEAPONS, TOOLS, ARMOR).get(random.nextInt(3));
		String piece = group.get(random.nextInt(group.size()));
		Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(Identifier.withDefaultNamespace(tier.prefix(piece) + "_" + piece));
		return item.map(Holder::value).orElseGet(() -> BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("iron_" + piece)));
	}

	/**
	 * Adds up to {@code count} distinct enchantments the item supports, never a curse and never two that conflict.
	 * An item that supports fewer gets them all.
	 */
	public static void enchant(final ServerLevel level, final ItemStack stack, final int count, final RandomSource random) {
		List<Holder<Enchantment>> pool = new ArrayList<>();
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(enchantment -> {
			if (!enchantment.is(EnchantmentTags.CURSE) && enchantment.value().canEnchant(stack) && !isTestFixture(enchantment)) {
				pool.add(enchantment);
			}
		});
		List<Holder<Enchantment>> granted = new ArrayList<>();
		while (granted.size() < count && !pool.isEmpty()) {
			Holder<Enchantment> pick = pool.remove(random.nextInt(pool.size()));
			if (granted.stream().allMatch(other -> Enchantment.areCompatible(other, pick))) {
				granted.add(pick);
				stack.enchant(pick, Mth.nextInt(random, pick.value().getMinLevel(), pick.value().getMaxLevel()));
			}
		}
	}

	private static boolean isTestFixture(final Holder<Enchantment> enchantment) {
		return enchantment.unwrapKey().map(key -> key.identifier().getNamespace().contains("-test-")).orElse(false);
	}

	public static ItemStack potion(final RandomSource random) {
		List<Holder.Reference<Potion>> potions = BuiltInRegistries.POTION.listElements()
			.filter(potion -> !BASE_POTIONS.contains(potion))
			.toList();
		ItemStack stack = new ItemStack(Items.POTION);
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potions.get(random.nextInt(potions.size()))));
		return stack;
	}
}
