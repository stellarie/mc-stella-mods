package com.stella.equips.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** An arrow's base damage, which the game sets but never reads out, and its pierce level, which it never lets out. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Accessor("baseDamage")
	double stella$baseDamage();

	@Invoker("setPierceLevel")
	void stella$setPierceLevel(byte level);
}
