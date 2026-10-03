package com.stella.rpg.roll;

import com.stella.rpg.RpgConfig;
import com.stella.rpg.RpgConfigStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;

/**
 * The roll tier, the kind, and the dice material of an item. The tier comes from the repair material,
 * so a spear, copper armor, or a modded piece that uses the vanilla tags needs no list.
 */
public final class ItemTiers {
	public static final String VOID_NAMESPACE = "mod-stella-tower";
	public static final String VOID_PREFIX = "void_";
	public static final String VOID = "void";
	public static final String RANGED = "ranged";
	public static final String HEAVY = "heavy";
	public static final String IRON = "iron";

	/** What the item is. It picks the weapon die. */
	public enum Type {
		SWORD("sword"),
		AXE("axe"),
		SPEAR("spear"),
		MACE("mace"),
		TRIDENT("trident"),
		BOW("bow"),
		CROSSBOW("crossbow"),
		ARMOR("armor"),
		SHIELD("shield"),
		ELYTRA("elytra");

		private final String key;

		Type(final String key) {
			this.key = key;
		}

		public String key() {
			return this.key;
		}

		public boolean melee() {
			return this == SWORD || this == AXE || this == SPEAR || this == MACE || this == TRIDENT;
		}

		public boolean ranged() {
			return this == BOW || this == CROSSBOW;
		}
	}

	/** {@code tier} names a config tier. {@code material} names a dice material. {@code type} may be null. */
	public record Info(Affix.Kind kind, String tier, String material, Type type) {
	}

	private record Index(RpgConfig config, Map<Item, Optional<Info>> cache) {
	}

	private static final Map<String, String> REPAIR_TAGS = Map.ofEntries(
		Map.entry("wooden_tool_materials", "wood"),
		Map.entry("stone_tool_materials", "stone"),
		Map.entry("copper_tool_materials", "copper"),
		Map.entry("gold_tool_materials", "gold"),
		Map.entry("iron_tool_materials", "iron"),
		Map.entry("diamond_tool_materials", "diamond"),
		Map.entry("netherite_tool_materials", "netherite"),
		Map.entry("repairs_leather_armor", "wood"),
		Map.entry("repairs_chain_armor", "stone"),
		Map.entry("repairs_turtle_helmet", "stone"),
		Map.entry("repairs_copper_armor", "copper"),
		Map.entry("repairs_gold_armor", "gold"),
		Map.entry("repairs_iron_armor", "iron"),
		Map.entry("repairs_diamond_armor", "diamond"),
		Map.entry("repairs_netherite_armor", "netherite")
	);

	private static volatile Index index;

	private ItemTiers() {
	}

	/** The info of a stack, or null when the item never rolls. */
	public static Info of(final ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		RpgConfig config = RpgConfigStore.current();
		Index found = index;
		if (found == null || found.config() != config) {
			found = new Index(config, new HashMap<>());
			index = found;
		}
		Item item = stack.getItem();
		Map<Item, Optional<Info>> cache = found.cache();
		synchronized (cache) {
			Optional<Info> known = cache.get(item);
			if (known == null) {
				known = Optional.ofNullable(resolve(config, stack));
				cache.put(item, known);
			}
			return known.orElse(null);
		}
	}

	/** The void gear of the tower, read by id string so this mod needs no tower class. */
	public static Info byId(final Identifier id) {
		if (!id.getNamespace().equals(VOID_NAMESPACE) || !id.getPath().startsWith(VOID_PREFIX)) {
			return null;
		}
		String name = id.getPath().substring(VOID_PREFIX.length());
		for (String suffix : List.of("sword", "axe", "spear")) {
			if (name.equals(suffix)) {
				return new Info(Affix.Kind.WEAPON, VOID, VOID, Type.valueOf(suffix.toUpperCase(java.util.Locale.ROOT)));
			}
		}
		for (String suffix : List.of("helmet", "chestplate", "leggings", "boots")) {
			if (name.equals(suffix)) {
				return new Info(Affix.Kind.ARMOR, VOID, VOID, Type.ARMOR);
			}
		}
		return null;
	}

	/** The material a repair tag or repair item stands for, or null. */
	public static String materialOf(final ItemStack stack) {
		Repairable repairable = stack.get(DataComponents.REPAIRABLE);
		if (repairable == null) {
			return null;
		}
		if (repairable.items() instanceof HolderSet.Named<Item> named) {
			String material = REPAIR_TAGS.get(named.key().location().getPath());
			if (material != null) {
				return material;
			}
		}
		return byRepairItem(repairable);
	}

	private static String byRepairItem(final Repairable repairable) {
		List<Map.Entry<Item, String>> samples = List.of(
			Map.entry(Items.OAK_PLANKS, "wood"), Map.entry(Items.COBBLESTONE, "stone"), Map.entry(Items.COPPER_INGOT, "copper"),
			Map.entry(Items.GOLD_INGOT, "gold"), Map.entry(Items.IRON_INGOT, "iron"), Map.entry(Items.DIAMOND, "diamond"),
			Map.entry(Items.NETHERITE_INGOT, "netherite"), Map.entry(Items.LEATHER, "wood"), Map.entry(Items.TURTLE_SCUTE, "stone")
		);
		for (Map.Entry<Item, String> sample : samples) {
			if (repairable.isValidRepairItem(new ItemStack(sample.getKey()))) {
				return sample.getValue();
			}
		}
		return null;
	}

	/** The tier of a dice material: a tier of its own name, except the ranged and heavy tiers. */
	public static String materialOfTier(final String tier) {
		return switch (tier) {
			case RANGED -> IRON;
			case HEAVY -> "diamond";
			default -> tier;
		};
	}

	private static Info resolve(final RpgConfig config, final ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		Type type = typeOf(stack);
		for (Map.Entry<String, RpgConfig.RollTier> tier : config.weaponRolls().tiers().entrySet()) {
			if (tier.getValue().items().contains(id)) {
				return new Info(Affix.Kind.WEAPON, tier.getKey(), materialOfTier(tier.getKey()), type);
			}
			if (tier.getValue().armorItems().contains(id)) {
				return new Info(Affix.Kind.ARMOR, tier.getKey(), materialOfTier(tier.getKey()), type == null ? Type.ARMOR : type);
			}
		}
		Info voidGear = byId(id);
		if (voidGear != null) {
			return voidGear;
		}
		if (type == Type.BOW || type == Type.CROSSBOW) {
			return new Info(Affix.Kind.WEAPON, RANGED, IRON, type);
		}
		if (type == Type.TRIDENT || type == Type.MACE) {
			return new Info(Affix.Kind.WEAPON, HEAVY, materialOfTier(HEAVY), type);
		}
		if (type == Type.SHIELD || type == Type.ELYTRA) {
			return new Info(Affix.Kind.ARMOR, IRON, IRON, type);
		}
		if (type == null) {
			return null;
		}
		String material = materialOf(stack);
		if (material == null) {
			return null;
		}
		return new Info(type == Type.ARMOR ? Affix.Kind.ARMOR : Affix.Kind.WEAPON, material, material, type);
	}

	private static Type typeOf(final ItemStack stack) {
		if (stack.is(ItemTags.SWORDS)) {
			return Type.SWORD;
		}
		if (stack.is(ItemTags.AXES)) {
			return Type.AXE;
		}
		if (stack.is(ItemTags.SPEARS)) {
			return Type.SPEAR;
		}
		if (stack.is(Items.MACE)) {
			return Type.MACE;
		}
		if (stack.is(Items.TRIDENT)) {
			return Type.TRIDENT;
		}
		if (stack.is(Items.BOW) || stack.getItem() instanceof com.stella.rpg.job.MechanicalBow) {
			return Type.BOW;
		}
		if (stack.is(Items.CROSSBOW)) {
			return Type.CROSSBOW;
		}
		if (stack.is(Items.SHIELD)) {
			return Type.SHIELD;
		}
		if (stack.is(Items.ELYTRA)) {
			return Type.ELYTRA;
		}
		if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)) {
			return Type.ARMOR;
		}
		return null;
	}
}
