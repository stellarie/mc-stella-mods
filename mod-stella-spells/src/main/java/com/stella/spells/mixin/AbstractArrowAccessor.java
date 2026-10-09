package com.stella.spells.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The pierce count is private, and a diamond arrow sets it at launch. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("setPierceLevel")
	void stellaSpells$setPierceLevel(byte level);
}
