package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.GearSpec;
import com.stella.ruins.reward.RuinGear;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.UniqueEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * The exclusive items of the camp: three materials, the
 * Prospector's Pick, the Lucky Headlamp and the compass.
 */
public final class CampItems {
	public static final TagKey<Item> REPAIR = TagKey.create(Registries.ITEM, StellaRuins.id("lost_miners_repair"));
	/** Diamond tier: durability 1561, speed 8, bonus damage 3. */
	public static final GearSpec PROSPECTOR = new GearSpec(
		1561, 8.0F, 3.0F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
		0, 0, 0, 0, 0, 0.0F, 0.0F, 10,
		REPAIR, StellaRuins.id("lost_miners_camp"));
	/** A helmet worth 2, on a lamp's mount. */
	public static final GearSpec HEADLAMP = new GearSpec(
		0, 1.0F, 0.0F, BlockTags.INCORRECT_FOR_WOODEN_TOOL,
		15, 2, 0, 0, 0, 0.0F, 0.0F, 12,
		REPAIR, StellaRuins.id("lucky_headlamp"));

	public static Item GLEAMING_ORE_CHUNK;
	public static Item RAT_TAIL;
	public static Item MINERS_JOURNAL_PAGE;
	public static Item PROSPECTORS_PICK;
	public static Item LUCKY_HEADLAMP;
	public static MinersCompassItem MINERS_COMPASS;

	private CampItems() {
	}

	public static void register() {
		GLEAMING_ORE_CHUNK = RuinItems.register("gleaming_ore_chunk", Item::new, new Item.Properties());
		RAT_TAIL = RuinItems.register("rat_tail", Item::new, new Item.Properties());
		MINERS_JOURNAL_PAGE = RuinItems.register("miners_journal_page", Item::new, new Item.Properties());
		UniqueEffects.define(ProspectorEffect.ID, new ProspectorEffect());
		PROSPECTORS_PICK = RuinUniques.register("prospectors_pick", Item::new,
			new Item.Properties().stacksTo(1).pickaxe(RuinGear.toolMaterial(PROSPECTOR), RuinGear.PICKAXE_DAMAGE, RuinGear.PICKAXE_SPEED),
			ProspectorEffect.ID);
		UniqueEffects.define(HeadlampEffect.ID, new HeadlampEffect());
		LUCKY_HEADLAMP = RuinUniques.register("lucky_headlamp", Item::new,
			new Item.Properties().stacksTo(1).humanoidArmor(RuinGear.armorMaterial(HEADLAMP), ArmorType.HELMET), HeadlampEffect.ID);
		MINERS_COMPASS = RuinItems.register("miners_compass", MinersCompassItem::new, new Item.Properties().stacksTo(1));
	}
}
