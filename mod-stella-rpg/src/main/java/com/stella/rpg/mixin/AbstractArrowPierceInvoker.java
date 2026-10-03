package com.stella.rpg.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowPierceInvoker {
	@Invoker("setPierceLevel")
	void stellaRpg$setPierce(byte level);
}
