package com.stella.end.mixin;

import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla keeps the peek amount private: 0 is a shut shell, 100 is fully open. */
@Mixin(Shulker.class)
public interface ShulkerPeekAccessor {
	@Invoker("getRawPeekAmount")
	int stellaEnd$getRawPeekAmount();

	@Invoker("setRawPeekAmount")
	void stellaEnd$setRawPeekAmount(int amount);
}
