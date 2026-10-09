package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.AffixSpec;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.PeriodicEffect;
import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.SetBonus;
import com.stella.ruins.reward.SetSpec;
import com.stella.ruins.reward.UniqueEffects;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.Equippable;

/** The exclusive items of the crater: four materials, the Starweave mage set, and the Fallen Star. */
public final class CraterItems {
	public static final String SET_ID = "starweave";
	/** The attack speed the 2-piece bonus adds, as a share of the base. */
	public static final double TWO_PIECE_ATTACK_SPEED = 0.10;
	/** The share of magic damage the 4-piece bonus adds. */
	public static final double FOUR_PIECE_MAGIC = 0.15;
	public static final int FIRE_RESISTANCE_TICKS = 60;
	public static final TagKey<Item> STARMETAL_REPAIR = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, "starmetal_repair"));

	public static Item RAW_STARMETAL;
	public static Item STARMETAL_INGOT;
	public static Item STAR_FRAGMENT;
	public static Item EMBER_CORE;
	public static RuinSets.SetDefinition STARWEAVE;
	public static Item FALLEN_STAR;
	public static RuinAffixes.Affix EMBERWARD;
	public static final EmberwardEffect EMBERWARD_EFFECT = new EmberwardEffect();
	/** Armor toughness the Emberward affix adds. */
	public static final double EMBERWARD_TOUGHNESS = 1.0;

	private CraterItems() {
	}

	public static void register() {
		RAW_STARMETAL = RuinItems.register("raw_starmetal", Item::new, new Item.Properties());
		STARMETAL_INGOT = RuinItems.register("starmetal_ingot", Item::new, new Item.Properties());
		STAR_FRAGMENT = RuinItems.register("star_fragment", Item::new, new Item.Properties());
		EMBER_CORE = RuinItems.register("ember_core", Item::new, new Item.Properties());
		STARWEAVE = RuinSets.define(SET_ID, SetSpec.builder()
			.durabilityFactor(25)
			.defense(2, 6, 5, 2)
			.toughness(1.0F)
			.knockbackResistance(0.0F)
			.enchantValue(18)
			.repair(STARMETAL_REPAIR)
			.equipmentAsset(StellaRuins.id(SET_ID))
			.bonus(new SetBonus(2, List.of(AttributeBonus.multiplyBase(Attributes.ATTACK_SPEED, TWO_PIECE_ATTACK_SPEED))))
			.bonus(new SetBonus(4, List.of(), new PeriodicEffect(MobEffects.FIRE_RESISTANCE, 0, FIRE_RESISTANCE_TICKS)))
			.build());
		UniqueEffects.define(StarfallEffect.ID, new StarfallEffect());
		UniqueEffects.define(EmberwardEffect.ID, EMBERWARD_EFFECT);
		EMBERWARD = RuinAffixes.define("emberward", new AffixSpec(
			CraterItems::isArmor, Ingredient.of(EMBER_CORE),
			List.of(AttributeBonus.add(Attributes.ARMOR_TOUGHNESS, EMBERWARD_TOUGHNESS)), EmberwardEffect.ID));
		FALLEN_STAR = RuinUniques.register("fallen_star", Item::new, new Item.Properties().stacksTo(1), StarfallEffect.ID);
	}

	/** Any piece of humanoid armor. */
	static boolean isArmor(final ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}
}
