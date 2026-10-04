package com.stella.ruins.site.sealedvault;

import com.stella.ruins.reward.AffixSpec;
import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.UniqueEffects;
import java.util.List;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** The exclusive items of the vault: three materials, the Void-touched affix, and the Abyssal Edge. */
public final class VaultItems {
	public static final String AFFIX_ID = "void_touched";

	public static Item VAULT_SEAL;
	public static Item VOID_ESSENCE;
	public static Item VAULT_DUST;
	public static RuinAffixes.Affix VOID_TOUCHED;
	public static Item ABYSSAL_EDGE;

	private VaultItems() {
	}

	public static void register() {
		VAULT_SEAL = RuinItems.register("vault_seal", Item::new, new Item.Properties().stacksTo(16));
		VOID_ESSENCE = RuinItems.register("void_essence", Item::new, new Item.Properties());
		VAULT_DUST = RuinItems.register("vault_dust", Item::new, new Item.Properties());
		UniqueEffects.define(VoidTouchedEffect.ID, new VoidTouchedEffect());
		VOID_TOUCHED = RuinAffixes.define(AFFIX_ID, new AffixSpec(
			VaultItems::acceptsVoidTouched,
			Ingredient.of(VOID_ESSENCE),
			List.of(),
			VoidTouchedEffect.ID
		));
		UniqueEffects.define(AbyssEffect.ID, new AbyssEffect());
		ABYSSAL_EDGE = RuinUniques.register("abyssal_edge", Item::new,
			new Item.Properties().stacksTo(1).sword(ToolMaterial.NETHERITE, 3.0F, -2.4F), AbyssEffect.ID);
	}

	/** Swords, axes, maces, tridents, bows, and crossbows. */
	public static boolean acceptsVoidTouched(final ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.TRIDENT)
			|| stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
	}
}
