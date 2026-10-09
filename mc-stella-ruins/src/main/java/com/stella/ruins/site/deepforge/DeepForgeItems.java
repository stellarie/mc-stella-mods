package com.stella.ruins.site.deepforge;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.AffixSpec;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.GearSpec;
import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinGear;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.UniqueEffects;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.ArmorType;

/** The materials, the Deepsteel gear and the unique hammer of the Deep Forge. */
public final class DeepForgeItems {
	public static final TagKey<Item> DEEPSTEEL_REPAIR = TagKey.create(Registries.ITEM, StellaRuins.id("deepsteel_repair"));
	/** Every stat sits between diamond and netherite. */
	public static final GearSpec DEEPSTEEL = new GearSpec(
		1800, 8.5F, 3.5F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
		35, 3, 8, 6, 3, 2.5F, 0.05F, 14,
		DEEPSTEEL_REPAIR, StellaRuins.id("deepsteel"));
	public static final String HAMMER_EFFECT = "forgeheart";
	public static final float HAMMER_DAMAGE = 7.0F;
	public static final float HAMMER_SPEED = -3.2F;

	public static final Item FORGE_CORE = material("forge_core");
	public static final Item ORE_CHUNK = material("deepsteel_ore_chunk");
	public static final Item INGOT = material("deepsteel_ingot");
	public static final Item CINDER_DUST = material("cinder_dust");
	public static final Item FORGEHEART_SHARD = material("forgeheart_shard");

	public static final Item SWORD = RuinGear.sword("deepsteel_sword", DEEPSTEEL);
	public static final Item AXE = RuinGear.axe("deepsteel_axe", DEEPSTEEL);
	public static final Item PICKAXE = RuinGear.pickaxe("deepsteel_pickaxe", DEEPSTEEL);
	public static final Item HELMET = RuinGear.armorPiece("deepsteel_helmet", DEEPSTEEL, ArmorType.HELMET);
	public static final Item CHESTPLATE = RuinGear.armorPiece("deepsteel_chestplate", DEEPSTEEL, ArmorType.CHESTPLATE);
	public static final Item LEGGINGS = RuinGear.armorPiece("deepsteel_leggings", DEEPSTEEL, ArmorType.LEGGINGS);
	public static final Item BOOTS = RuinGear.armorPiece("deepsteel_boots", DEEPSTEEL, ArmorType.BOOTS);

	/** Mining efficiency the Deepdelver affix adds. */
	public static final double DEEPDELVER_EFFICIENCY = 2.0;
	public static RuinAffixes.Affix CINDERBRAND;
	public static RuinAffixes.Affix DEEPDELVER;

	public static final Item HAMMER = RuinUniques.register("forgeheart_hammer", Item::new,
		new Item.Properties().axe(RuinGear.toolMaterial(DEEPSTEEL), HAMMER_DAMAGE, HAMMER_SPEED), HAMMER_EFFECT);

	private DeepForgeItems() {
	}

	private static Item material(final String id) {
		return RuinItems.register(id, Item::new, new Item.Properties());
	}

	/** Loads the class, which registers every item above, then defines the set and the affixes that use the drops. */
	static void register() {
		RuinSets.adopt("deepsteel", DEEPSTEEL, Map.of(
			EquipmentSlot.HEAD, HELMET, EquipmentSlot.CHEST, CHESTPLATE, EquipmentSlot.LEGS, LEGGINGS, EquipmentSlot.FEET, BOOTS));
		UniqueEffects.define(CinderbrandEffect.ID, new CinderbrandEffect());
		CINDERBRAND = RuinAffixes.define("cinderbrand", new AffixSpec(
			RuinGear::isMeleeWeapon,
			Ingredient.of(CINDER_DUST), List.of(), CinderbrandEffect.ID));
		DEEPDELVER = RuinAffixes.define("deepdelver", new AffixSpec(
			RuinGear::isPickaxeOrAxe,
			Ingredient.of(FORGEHEART_SHARD), List.of(AttributeBonus.add(Attributes.MINING_EFFICIENCY, DEEPDELVER_EFFICIENCY)), null));
	}
}
