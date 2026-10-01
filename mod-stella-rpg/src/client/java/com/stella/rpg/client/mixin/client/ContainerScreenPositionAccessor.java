package com.stella.rpg.client.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenPositionAccessor {
	@Accessor("leftPos")
	int stellaRpgLeftPos();

	@Accessor("topPos")
	int stellaRpgTopPos();

	@Accessor("imageHeight")
	void stellaRpgSetImageHeight(int height);
}
