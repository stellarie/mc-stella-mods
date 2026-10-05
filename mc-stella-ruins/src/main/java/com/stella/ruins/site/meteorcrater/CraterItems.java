package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.PeriodicEffect;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;

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
		FALLEN_STAR = RuinUniques.register("fallen_star", Item::new, new Item.Properties().stacksTo(1), StarfallEffect.ID);
	}
}
