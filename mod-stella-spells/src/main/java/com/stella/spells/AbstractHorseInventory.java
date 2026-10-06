package com.stella.spells;

import net.minecraft.world.SimpleContainer;

/**
 * The mount's own pack, which vanilla keeps behind a protected field. A summoned servant's horse carries its
 * armor and its pack in that same container, so this reads it rather than keeping a second copy anywhere.
 */
public interface AbstractHorseInventory {
	SimpleContainer stellaSpells$inventory();
}
