package com.stella.rpg.roll;

import com.stella.rpg.StellaRpg;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** What can carry a bonus: gear carries the maps it rolls, a material carries the maps it sums into gear. */
public final class BonusCarrier {
	public static final TagKey<Item> MATERIALS = TagKey.create(Registries.ITEM, StellaRpg.id("bonus_materials"));

	private BonusCarrier() {
	}

	/** The gear kind of the stack, or null when it is not gear. */
	public static Affix.Kind kind(final ItemStack stack) {
		return stack.isEmpty() ? null : WeaponRolls.kindOf(stack);
	}

	public static boolean isMaterial(final ItemStack stack) {
		return !stack.isEmpty() && stack.is(MATERIALS);
	}
}
