package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.AffixSpec;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.GearSpec;
import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinGear;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.UniqueEffects;
import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.ArmorType;

/** The exclusive items of the camp: three materials, the Bandit's Mark affix, and the Cutthroat's Cloak. */
public final class CampItems {
	/** The damage the Bandit's Mark affix adds, as a share of the total. */
	public static final double MARK_DAMAGE = 0.10;
	/** The cloak is light armor: a chestplate worth 5, no toughness. */
	public static final GearSpec CLOAK = new GearSpec(
		1, 1.0F, 0.0F, BlockTags.INCORRECT_FOR_IRON_TOOL,
		15, 1, 5, 2, 1, 0.0F, 0.0F, 15,
		ItemTags.REPAIRS_LEATHER_ARMOR, StellaRuins.id("cutthroats"));

	public static Item BANDITS_MARK;
	public static Item STOLEN_COIN;
	public static Item CHIEFS_LEDGER;
	public static RuinAffixes.Affix BANDITS_MARK_AFFIX;
	public static Item CUTTHROATS_CLOAK;

	private CampItems() {
	}

	public static void register() {
		BANDITS_MARK = RuinItems.register("bandits_mark", Item::new, new Item.Properties());
		STOLEN_COIN = RuinItems.register("stolen_coin", Item::new, new Item.Properties());
		CHIEFS_LEDGER = RuinItems.register("chiefs_ledger", Item::new, new Item.Properties());
		UniqueEffects.define(BanditsMarkEffect.ID, new BanditsMarkEffect());
		BANDITS_MARK_AFFIX = RuinAffixes.define("bandits_mark", new AffixSpec(
			CampItems::acceptsMark,
			Ingredient.of(BANDITS_MARK),
			List.of(AttributeBonus.multiplyTotal(Attributes.ATTACK_DAMAGE, MARK_DAMAGE)),
			BanditsMarkEffect.ID
		));
		UniqueEffects.define(CutthroatEffect.ID, new CutthroatEffect());
		CUTTHROATS_CLOAK = RuinUniques.register("cutthroats_cloak", Item::new,
			new Item.Properties().stacksTo(1).humanoidArmor(RuinGear.armorMaterial(CLOAK), ArmorType.CHESTPLATE), CutthroatEffect.ID);
	}

	/** Swords, axes, and maces. */
	public static boolean acceptsMark(final ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE);
	}
}
