package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.SetBonus;
import com.stella.ruins.reward.SetSpec;
import com.stella.ruins.reward.UniqueEffects;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;

/** The exclusive items of the tower: three materials, the Hawkeye set, and the Hawkeye Longbow. */
public final class WatchtowerItems {
	public static final String SET_ID = "hawkeye";
	public static final String EFFECT_ID = "hawkeye";
	public static final TagKey<Item> HAWKFEATHER_REPAIR = TagKey.create(Registries.ITEM, StellaRuins.id("hawkfeather_repair"));
	/** The 2-piece bonus: movement speed, as a share of the base. */
	public static final double SET_SPEED = 0.10;
	public static final int BOW_DURABILITY = 480;

	public static Item HAWKFEATHER;
	public static Item SIGNAL_EMBER;
	public static Item CAPTAINS_INSIGNIA;
	public static RuinSets.SetDefinition HAWKEYE;
	public static Item LONGBOW;

	private WatchtowerItems() {
	}

	public static void register() {
		HAWKFEATHER = RuinItems.register("hawkfeather", Item::new, new Item.Properties());
		SIGNAL_EMBER = RuinItems.register("signal_ember", Item::new, new Item.Properties());
		CAPTAINS_INSIGNIA = RuinItems.register("captains_insignia", Item::new, new Item.Properties());
		HAWKEYE = RuinSets.define(SET_ID, SetSpec.builder()
			.durabilityFactor(25)
			.defense(3, 7, 5, 2)
			.toughness(1.0F)
			.knockbackResistance(0.0F)
			.enchantValue(14)
			.repair(HAWKFEATHER_REPAIR)
			.equipmentAsset(StellaRuins.id(SET_ID))
			.bonus(new SetBonus(2, List.of(AttributeBonus.multiplyBase(Attributes.MOVEMENT_SPEED, SET_SPEED))))
			.bonus(new SetBonus(4, List.of()))
			.build());
		UniqueEffects.define(EFFECT_ID, new HawkeyeEffect());
		HawkeyeEffect.registerSetBonus();
		LONGBOW = RuinUniques.register("hawkeye_longbow", BowItem::new, new Item.Properties().durability(BOW_DURABILITY), EFFECT_ID);
	}
}
