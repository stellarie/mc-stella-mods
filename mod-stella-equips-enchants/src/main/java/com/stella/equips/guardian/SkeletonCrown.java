package com.stella.equips.guardian;

import com.stella.equips.StellaEquips;
import com.stella.equips.refine.Refine;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The helmet of the ranged Tower Guardian: netherite stats, its own worn model. Arrows of the wearer deal +25
 * percent damage, and 10 percent more at each of +7, +10, +12 and +15, so 65 percent at +15.
 */
public final class SkeletonCrown {
	public static final String ID = "skeleton_crown";
	public static final double BASE_BONUS = 0.25;
	public static final double BONUS_PER_TIER = 0.10;
	public static final int[] TIERS = {7, 10, 12, 15};

	private static Item item;

	private SkeletonCrown() {
	}

	static void register() {
		Identifier id = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, ID);
		ArmorMaterial netherite = ArmorMaterials.NETHERITE;
		ArmorMaterial material = new ArmorMaterial(netherite.durability(), netherite.defense(), netherite.enchantmentValue(), netherite.equipSound(),
			netherite.toughness(), netherite.knockbackResistance(), netherite.repairIngredient(), ResourceKey.create(EquipmentAssets.ROOT_ID, id));
		Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))
			.humanoidArmor(material, ArmorType.HELMET)
			.fireResistant()
			.rarity(Rarity.EPIC)
			.component(DataComponents.ITEM_NAME, Component.translatableWithFallback("item." + StellaEquips.MOD_ID + "." + ID, "Skeleton Crown"));
		item = Registry.register(BuiltInRegistries.ITEM, id, new Item(properties));
	}

	public static Item item() {
		return item;
	}

	public static boolean is(final ItemStack stack) {
		return item != null && stack.is(item);
	}

	/** The share of extra arrow damage a crown gives at a refine level. */
	public static double bonusAt(final int refine) {
		double bonus = BASE_BONUS;
		for (int tier : TIERS) {
			if (refine >= tier) {
				bonus += BONUS_PER_TIER;
			}
		}
		return bonus;
	}

	/** The share of extra arrow damage that the crown on a wearer's head gives, or 0 without one. */
	static double wornBonus(final LivingEntity wearer) {
		ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
		return is(head) ? bonusAt(Refine.level(head)) : 0.0;
	}
}
