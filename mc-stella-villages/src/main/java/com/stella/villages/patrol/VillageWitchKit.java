package com.stella.villages.patrol;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The village witch, who comes only with the spells mod: a full set of diamond armour, every piece
 * enchanted, the helmet and the chestplate also with that mod's spell-enhancing enchantments. The kit never
 * drops. Its casting is the spells mod's, keyed on the tag this mod gives it.
 */
public final class VillageWitchKit {
	public static final String TAG = "mc-stella-villages.village_witch";
	/** Tells the mobs mod to leave the witch's goals alone, so it throws no potions. */
	public static final String NO_BEHAVIOR_TAG = "mod-stella-mobs.no_behavior";
	public static final String LABEL = "Village Witch";
	/** How far from the bell the witch keeps. */
	public static final int BELL_RADIUS = 16;
	private static final Map<EquipmentSlot, Item> PIECES = Map.of(
		EquipmentSlot.HEAD, Items.DIAMOND_HELMET,
		EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE,
		EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS,
		EquipmentSlot.FEET, Items.DIAMOND_BOOTS
	);
	/** The spells mod's enchantments, by slot, at their top level; read by id so no compile-time link is needed. */
	private static final Map<EquipmentSlot, List<Map.Entry<String, Integer>>> SPELL_ENCHANTS = Map.of(
		EquipmentSlot.HEAD, List.of(Map.entry("arcane_sight", 3), Map.entry("clear_mind", 3), Map.entry("doublecast", 1)),
		EquipmentSlot.CHEST, List.of(Map.entry("arcane_weave", 3), Map.entry("spellguard", 3))
	);
	/** A seam for tests; play reads whether the spells mod is loaded. */
	private static volatile Boolean forced;

	private VillageWitchKit() {
	}

	/** Whether villages get a witch at all: only with the spells mod, which gives it its spells. */
	public static boolean enabled() {
		Boolean pinned = forced;
		return pinned != null ? pinned : FabricLoader.getInstance().isModLoaded("mod-stella-spells");
	}

	public static void force(final Boolean enabled) {
		forced = enabled;
	}

	public static void apply(final Mob witch) {
		witch.addTag(TAG);
		witch.addTag(NO_BEHAVIOR_TAG);
		for (Map.Entry<EquipmentSlot, Item> piece : PIECES.entrySet()) {
			ItemStack stack = new ItemStack(piece.getValue());
			enchant(witch, stack, Enchantments.PROTECTION, 4);
			enchant(witch, stack, Enchantments.UNBREAKING, 3);
			for (Map.Entry<String, Integer> spell : SPELL_ENCHANTS.getOrDefault(piece.getKey(), List.of())) {
				spellEnchant(witch, stack, spell.getKey(), spell.getValue());
			}
			witch.setItemSlot(piece.getKey(), stack);
			witch.setDropChance(piece.getKey(), 0.0F);
		}
	}

	public static boolean isVillageWitch(final Mob mob) {
		return mob.entityTags().contains(TAG);
	}

	private static void enchant(final Mob mob, final ItemStack stack, final ResourceKey<Enchantment> key, final int level) {
		Holder<Enchantment> enchantment = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		stack.enchant(enchantment, level);
	}

	private static void spellEnchant(final Mob mob, final ItemStack stack, final String path, final int level) {
		Optional<Holder.Reference<Enchantment>> enchantment = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.get(Identifier.fromNamespaceAndPath("mod-stella-spells", path));
		enchantment.ifPresent(holder -> stack.enchant(holder, level));
	}
}
