package com.stella.equips.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface EquipsContainerScreenAccessor {
	@Accessor("leftPos")
	int stellaEquipsLeftPos();

	@Accessor("topPos")
	int stellaEquipsTopPos();
}
