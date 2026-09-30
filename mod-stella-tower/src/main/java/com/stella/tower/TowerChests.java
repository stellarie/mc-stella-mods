package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * The tower's chests: placing an indestructible double chest, and filling it.
 *
 * <p>A chest is filled by {@link #ROLLS} rolls; each roll lands in one {@link Category} by its weight out of
 * {@link #TOTAL_WEIGHT}, and the rest of the weight is a roll that finds nothing. A guaranteed chest also
 * holds one enchanted book and one Scroll of Return. The spells mod's items are found by name in the item
 * registry and skipped when absent, so this mod has no dependency on it.
 *
 * <p>For the placement step: {@link #placeDouble} to build one, then {@link #fill} to stock it.
 */
public final class TowerChests {
	/** Rolls made for one chest. */
	public static final int ROLLS = 10;
	/** What a roll's weights are measured against. */
	public static final int TOTAL_WEIGHT = 10_000;
	/** The spells mod's id, by name only. */
	public static final String SPELLS_NAMESPACE = "mod-stella-spells";
	/** The spells mod's return scroll. */
	public static final Identifier RETURN_SCROLL_ID = Identifier.fromNamespaceAndPath(SPELLS_NAMESPACE, "return_scroll");
	/** The spells mod's tag of its spellbooks; a scroll is named after its book, plus {@code _scroll}. */
	public static final TagKey<Item> SPELLBOOKS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(SPELLS_NAMESPACE, "spellbooks"));

	/** What a roll can find, and the weight out of {@link #TOTAL_WEIGHT} it does so with. */
	public enum Category {
		FOOD(1800),
		POTIONS(1400),
		SCROLLS(1400),
		RETURN_SCROLL(600),
		ENCHANTED_BOOK(900),
		SPELLBOOK(500),
		EMERALD(1100),
		MISC(700),
		DIAMOND(400),
		ANCIENT_DEBRIS(120),
		DIAMOND_GEAR(80),
		NETHERITE_GEAR(5),
		/** The weight no other category takes. */
		NOTHING(TOTAL_WEIGHT - 9005);

		public final int weight;

		Category(final int weight) {
			this.weight = weight;
		}
	}

	/** One roll: what it landed on, and what it gave; empty when its category has no item to give. */
	public record Draw(Category category, ItemStack stack) {
	}

	/** Where the spells mod's items come from; a test can stand in for the mod. */
	public interface SpellItems {
		Optional<Item> returnScroll();

		List<Item> scrolls();

		List<Item> books();
	}

	/** The real lookup: the item registry, by name, whatever it holds. */
	public static final SpellItems REGISTRY = new SpellItems() {
		@Override
		public Optional<Item> returnScroll() {
			return BuiltInRegistries.ITEM.getOptional(RETURN_SCROLL_ID);
		}

		@Override
		public List<Item> scrolls() {
			List<Item> scrolls = new ArrayList<>();
			for (Item book : books()) {
				Identifier id = BuiltInRegistries.ITEM.getKey(book);
				BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_scroll")).ifPresent(scrolls::add);
			}
			return scrolls;
		}

		@Override
		public List<Item> books() {
			List<Item> books = new ArrayList<>();
			for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(SPELLBOOKS)) {
				books.add(holder.value());
			}
			return books;
		}
	};

	private static volatile SpellItems spellItems = REGISTRY;

	private static final List<Item> FOODS = List.of(
		Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_SALMON, Items.BREAD, Items.BAKED_POTATO,
		Items.GOLDEN_CARROT, Items.PUMPKIN_PIE, Items.APPLE
	);
	private static final List<net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>> POTIONS = List.of(
		Potions.HEALING, Potions.STRONG_HEALING, Potions.REGENERATION, Potions.SWIFTNESS, Potions.STRENGTH,
		Potions.FIRE_RESISTANCE, Potions.NIGHT_VISION, Potions.WATER_BREATHING
	);
	private static final List<Item> MISC = List.of(
		Items.ARROW, Items.EXPERIENCE_BOTTLE, Items.ENDER_PEARL, Items.IRON_INGOT, Items.GOLD_INGOT, Items.LAPIS_LAZULI, Items.GUNPOWDER, Items.BLAZE_ROD
	);
	private static final List<Item> DIAMOND_GEAR = List.of(
		Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
		Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS
	);
	private static final List<Item> NETHERITE_GEAR = List.of(
		Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
		Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS
	);

	private TowerChests() {
	}

	/** The test seam: the source of the spells mod's items; null restores the registry. */
	public static void installSpellItems(final @Nullable SpellItems items) {
		spellItems = items == null ? REGISTRY : items;
	}

	/** The enchanting power of a chest's gear and books on a floor: deeper floors carry better. */
	public static int enchantLevel(final int floor) {
		return Math.min(39, 20 + Math.max(0, floor) / 5);
	}

	/**
	 * Places a double tower chest whose left half is at {@code left}, both halves facing {@code facing}; the
	 * right half stands clockwise of the left, as seen from the front. Returns the right half's position.
	 */
	public static BlockPos placeDouble(final ServerLevel level, final BlockPos left, final Direction facing) {
		BlockPos right = left.relative(facing.getClockWise());
		Block chest = TowerBlocks.TOWER_CHEST;
		level.setBlock(left, chest.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.LEFT), Block.UPDATE_ALL);
		level.setBlock(right, chest.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.RIGHT), Block.UPDATE_ALL);
		return right;
	}

	/**
	 * Fills the tower chest at {@code pos} (either half of a double, or a single) with {@link #ROLLS} rolls, and
	 * with a book and a Scroll of Return more when {@code guaranteed}. Items go to free slots, in random order.
	 * Returns the stacks put in; a chest with no room for one drops it.
	 */
	public static List<ItemStack> fill(final ServerLevel level, final BlockPos pos, final int floor, final boolean guaranteed, final RandomSource random) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ChestBlock chest)) {
			return List.of();
		}
		Container container = ChestBlock.getContainer(chest, state, level, pos, true);
		if (container == null) {
			return List.of();
		}
		List<ItemStack> loot = new ArrayList<>();
		for (int i = 0; i < ROLLS; i++) {
			ItemStack stack = roll(level, floor, random).stack();
			if (!stack.isEmpty()) {
				loot.add(stack);
			}
		}
		if (guaranteed) {
			loot.add(stackFor(level, Category.ENCHANTED_BOOK, floor, random));
			ItemStack scroll = stackFor(level, Category.RETURN_SCROLL, floor, random);
			if (!scroll.isEmpty()) {
				loot.add(scroll);
			}
		}
		List<Integer> free = new ArrayList<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).isEmpty()) {
				free.add(slot);
			}
		}
		List<ItemStack> put = new ArrayList<>();
		for (ItemStack stack : loot) {
			if (free.isEmpty()) {
				break;
			}
			container.setItem(free.remove(random.nextInt(free.size())), stack);
			put.add(stack);
		}
		container.setChanged();
		return put;
	}

	/** One roll: the category by weight, and its item; the item is empty when its category has none to give. */
	public static Draw roll(final ServerLevel level, final int floor, final RandomSource random) {
		int pick = random.nextInt(TOTAL_WEIGHT);
		Category chosen = Category.NOTHING;
		for (Category category : Category.values()) {
			if (pick < category.weight) {
				chosen = category;
				break;
			}
			pick -= category.weight;
		}
		return new Draw(chosen, stackFor(level, chosen, floor, random));
	}

		/** What a category gives on a floor, drawn from the random; empty when it has no item to give. */
	public static ItemStack stackFor(final ServerLevel level, final Category category, final int floor, final RandomSource random) {
		int power = enchantLevel(floor);
		int bonus = Math.max(0, floor) / 25;
		return switch (category) {
			case FOOD -> new ItemStack(pick(FOODS, random), 4 + random.nextInt(9));
			case POTIONS -> PotionContents.createItemStack(random.nextInt(4) == 0 ? Items.SPLASH_POTION : Items.POTION, POTIONS.get(random.nextInt(POTIONS.size())));
			case SCROLLS -> {
				List<Item> scrolls = spellItems.scrolls();
				yield scrolls.isEmpty() ? ItemStack.EMPTY : new ItemStack(pick(scrolls, random), 1 + random.nextInt(2));
			}
			case RETURN_SCROLL -> spellItems.returnScroll().map(ItemStack::new).orElse(ItemStack.EMPTY);
			case ENCHANTED_BOOK -> enchant(level, new ItemStack(Items.BOOK), power, random);
			case SPELLBOOK -> {
				List<Item> books = spellItems.books();
				yield books.isEmpty() ? ItemStack.EMPTY : new ItemStack(pick(books, random));
			}
			case EMERALD -> new ItemStack(Items.EMERALD, 3 + random.nextInt(7) + bonus);
			case MISC -> {
				Item item = pick(MISC, random);
				int count = item == Items.ARROW ? 16 : item == Items.ENDER_PEARL || item == Items.BLAZE_ROD ? 2 : 4 + random.nextInt(9);
				yield new ItemStack(item, count);
			}
			case DIAMOND -> new ItemStack(Items.DIAMOND, 1 + random.nextInt(3));
			case ANCIENT_DEBRIS -> new ItemStack(Items.ANCIENT_DEBRIS, 1 + random.nextInt(2));
			case DIAMOND_GEAR -> enchant(level, new ItemStack(pick(DIAMOND_GEAR, random)), power, random);
			case NETHERITE_GEAR -> enchant(level, new ItemStack(pick(NETHERITE_GEAR, random)), power, random);
			case NOTHING -> ItemStack.EMPTY;
		};
	}

	/** The stack enchanted as at an enchanting table with the table's own enchantments; a book becomes an enchanted book. */
	private static ItemStack enchant(final ServerLevel level, final ItemStack stack, final int power, final RandomSource random) {
		Optional<HolderSet.Named<Enchantment>> table = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
		return EnchantmentHelper.enchantItem(random, stack, power, level.registryAccess(), table);
	}

	/** True when the stack carries at least one enchantment, as gear or as a book. */
	public static boolean enchanted(final ItemStack stack) {
		ItemEnchantments enchantments = stack.getOrDefault(stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		return !enchantments.isEmpty();
	}

	private static <T> T pick(final List<T> list, final RandomSource random) {
		return list.get(random.nextInt(list.size()));
	}
}
