package com.stella.tower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Everything the ghost merchant can sell, in one place. A merchant offers {@value #MIN_OFFERS} or {@value #MAX_OFFERS} of
 * these, drawn at random when it appears. Another task adds goods with {@link #register}: a good is an id, a price in
 * emeralds, the item shown on the shelf, and what the buyer is handed.
 */
public final class MerchantGoods {
	public static final int MIN_OFFERS = 5;
	public static final int MAX_OFFERS = 6;
	/** The floors above a player's best that a bought Floor Key may open, at most. */
	public static final int KEY_REACH = 5;
	public static final int PHANTOM_ARROWS = 32;
	public static final Identifier SHIELD_GENERATOR_ID = Identifier.fromNamespaceAndPath("mod-stella-mobs", "shield_generator");

	/** What a sale knows: where it happens, who buys, and a source of chance. */
	public record Sale(ServerLevel level, ServerPlayer buyer, RandomSource random) {
	}

	/**
	 * One good. {@code shown} is the item on the shelf; {@code make} is what the buyer receives, rolled at the sale; {@code stocked}
	 * is false for a good whose item this install does not have, and a merchant never offers one.
	 */
	public record Good(Identifier id, int price, Supplier<ItemStack> shown, Function<Sale, ItemStack> make, BooleanSupplier stocked) {
		/** A good that is the same item every time. */
		public static Good fixed(final Identifier id, final int price, final Supplier<ItemStack> item) {
			return new Good(id, price, item, sale -> item.get(), () -> true);
		}
	}

	private static final Map<Identifier, Good> GOODS = new LinkedHashMap<>();

	public static final Good PERFECT_INVISIBILITY = register(Good.fixed(StellaTower.id("perfect_invisibility"), 32, PerfectVeil::potion));
	public static final Good SOUL_TONIC = register(Good.fixed(StellaTower.id("soul_tonic"), 24, () -> new ItemStack(MerchantItems.SOUL_TONIC)));
	public static final Good PHANTOM_ARROWS_GOOD = register(Good.fixed(StellaTower.id("phantom_arrow"), 16,
		() -> new ItemStack(MerchantItems.PHANTOM_ARROW, PHANTOM_ARROWS)));
	public static final Good GHOST_LANTERN = register(Good.fixed(StellaTower.id("ghost_lantern"), 64, () -> new ItemStack(MerchantItems.GHOST_LANTERN)));
	/** A rune of a random element, of the floor the buyer has climbed to. */
	public static final Good TOWER_RUNE = register(new Good(StellaTower.id("tower_rune"), 64, () -> new ItemStack(TowerItems.RUNE),
		sale -> TowerRunes.rune(TowerElement.values()[sale.random().nextInt(TowerElement.values().length)], TowerMerchants.bestFloor(sale.buyer())),
		() -> true));
	/** A key to a floor from one to {@value #KEY_REACH} above the buyer's best. */
	public static final Good FLOOR_KEY = register(new Good(StellaTower.id("floor_key"), 48, () -> new ItemStack(TowerItems.FLOOR_KEY),
		sale -> FloorKeyItem.forFloor(TowerMerchants.bestFloor(sale.buyer()) + 1 + sale.random().nextInt(KEY_REACH)), () -> true));
	public static final Good ECTOPLASM_CHARM = register(Good.fixed(StellaTower.id("ectoplasm_charm"), 128, () -> new ItemStack(MerchantItems.ECTOPLASM_CHARM)));
	public static final Good TOWER_WHETSTONE = register(Good.fixed(StellaTower.id("tower_whetstone"), 192, () -> new ItemStack(MerchantItems.TOWER_WHETSTONE)));
	/** The mobs mod's block, by id: a merchant offers it only where that mod is present. */
	public static final Good SHIELD_GENERATOR = register(ofItem(StellaTower.id("shield_generator"), SHIELD_GENERATOR_ID, 192));
	/** The equips mod's Arcane Shortsword: a weapon of one damage per hit and 100 MATK, so it costs far less than the six. */
	public static final Identifier ARCANE_SHORTSWORD_ID = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "arcane_shortsword");
	public static final Good ARCANE_SHORTSWORD = register(ofItem(StellaTower.id("arcane_shortsword"), ARCANE_SHORTSWORD_ID, 96));
	/** The equips mod's Arcane Bow: no arrows needed, one damage per arrow hit, 100 MATK. */
	public static final Identifier ARCANE_BOW_ID = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "arcane_bow");
	public static final Good ARCANE_BOW = register(ofItem(StellaTower.id("arcane_bow"), ARCANE_BOW_ID, 96));
	/** The equips mod's six merchant weapons, ten stacks of emeralds each; absent without that mod. */
	public static final int WEAPON_PRICE = 640;
	public static final List<String> WEAPONS = List.of("soulreaver", "gravecleaver", "wraithstring", "hexbolt", "wraithpike", "archmage_staff");

	static {
		for (String weapon : WEAPONS) {
			register(ofItem(StellaTower.id(weapon), Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", weapon), WEAPON_PRICE));
		}
	}

	private MerchantGoods() {
	}

	/** A good that is another mod's item, found by its id; where that mod is absent the good is not in stock. */
	public static Good ofItem(final Identifier id, final Identifier item, final int price) {
		Supplier<ItemStack> stack = () -> BuiltInRegistries.ITEM.getOptional(item).map(ItemStack::new).orElse(ItemStack.EMPTY);
		return new Good(id, price, stack, sale -> stack.get(), () -> BuiltInRegistries.ITEM.containsKey(item));
	}

	/** Adds a good to the list every merchant draws from; a good of the same id replaces the old one. */
	public static Good register(final Good good) {
		GOODS.put(good.id(), good);
		return good;
	}

	/** Every good, in the order they were registered. */
	public static List<Good> all() {
		return List.copyOf(GOODS.values());
	}

	/** The goods this install can hand over. */
	public static List<Good> stocked() {
		List<Good> stocked = new ArrayList<>();
		for (Good good : GOODS.values()) {
			if (good.stocked().getAsBoolean()) {
				stocked.add(good);
			}
		}
		return stocked;
	}

	public static @Nullable Good byId(final Identifier id) {
		return GOODS.get(id);
	}

	/** The goods one merchant offers: five or six different ones of those in stock. */
	public static List<Good> roll(final RandomSource random) {
		List<Good> pool = stocked();
		Collections.shuffle(pool, new java.util.Random(random.nextLong()));
		int count = Math.min(pool.size(), MIN_OFFERS + random.nextInt(MAX_OFFERS - MIN_OFFERS + 1));
		return List.copyOf(pool.subList(0, count));
	}
}
