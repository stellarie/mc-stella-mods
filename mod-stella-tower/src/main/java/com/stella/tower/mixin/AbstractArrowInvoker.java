package com.stella.tower.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** An arrow's pierce level, which vanilla sets only from the weapon that fires it. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowInvoker {
	@Invoker("setPierceLevel")
	void stellaTower$setPierceLevel(byte level);
}
