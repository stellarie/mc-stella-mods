package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.reward.AffixSpec;
import com.stella.ruins.reward.AttributeBonus;
import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.UniqueEffects;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** The exclusive items of the grotto: three materials, the Resonant affix, and the Echo Prism. */
public final class GrottoItems {
	public static final String SPELLS_NAMESPACE = "mod-stella-spells";
	/** The attack speed the Resonant affix adds, as a share of the base. */
	public static final double RESONANT_ATTACK_SPEED = 0.10;

	public static Item RESONANT_SHARD;
	public static Item PRISM_GEL;
	public static Item RESONANT_CRYSTAL_CORE;
	public static RuinAffixes.Affix RESONANT;
	public static Item ECHO_PRISM;

	private GrottoItems() {
	}

	public static void register() {
		RESONANT_SHARD = RuinItems.register("resonant_shard", Item::new, new Item.Properties());
		PRISM_GEL = RuinItems.register("prism_gel", Item::new, new Item.Properties());
		RESONANT_CRYSTAL_CORE = RuinItems.register("resonant_crystal_core", Item::new, new Item.Properties());
		RESONANT = RuinAffixes.define("resonant", new AffixSpec(
			GrottoItems::acceptsResonant,
			Ingredient.of(RESONANT_SHARD),
			List.of(AttributeBonus.multiplyBase(Attributes.ATTACK_SPEED, RESONANT_ATTACK_SPEED)),
			null
		));
		UniqueEffects.define(EchoEffect.ID, new EchoEffect());
		ECHO_PRISM = RuinUniques.register("echo_prism", Item::new, new Item.Properties().stacksTo(1), EchoEffect.ID);
	}

	/** Swords, axes, bows, crossbows, and a spellbook of the spells mod when that mod is present. */
	public static boolean acceptsResonant(final ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW)
			|| isSpellbook(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/**
	 * A spellbook has an id in the spells mod's namespace. Its scrolls end in {@code _scroll}, and its other
	 * items are the return scroll and the scroll book. The grotto reads ids only, so it never loads that mod.
	 */
	public static boolean isSpellbook(final Identifier id) {
		if (!SPELLS_NAMESPACE.equals(id.getNamespace())) {
			return false;
		}
		String path = id.getPath();
		return !path.endsWith("_scroll") && !path.equals("scroll_book");
	}
}
