package com.stella.end.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The slots of a combiner menu are protected fields of the parent of the smithing menu. */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerAccess {
	@Accessor("inputSlots")
	Container stellaEnd$inputSlots();

	@Accessor("resultSlots")
	ResultContainer stellaEnd$resultSlots();
}
