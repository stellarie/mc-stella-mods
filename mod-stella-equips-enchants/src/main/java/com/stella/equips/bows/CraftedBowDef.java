package com.stella.equips.bows;

import net.minecraft.world.item.Item;

/**
 * One crafted bow or crossbow. The multipliers are against the vanilla weapon: {@code time} scales the draw or the
 * charge, {@code damage} the damage an arrow deals when it hits, and {@code speed} how fast the arrow flies.
 */
public record CraftedBowDef(String id, String name, boolean crossbow, int durability, double time, double damage, double speed,
	Signature signature, int basePierce, boolean knockback, Item repair) {
}
