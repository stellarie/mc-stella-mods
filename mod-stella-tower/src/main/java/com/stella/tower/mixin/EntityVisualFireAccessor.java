package com.stella.tower.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The flag that draws fire on an entity that is not burning: no damage, no panic, no ignition. */
@Mixin(Entity.class)
public interface EntityVisualFireAccessor {
	@Accessor("hasVisualFire")
	void stellaTower$setHasVisualFire(boolean visual);

	@Accessor("hasVisualFire")
	boolean stellaTower$hasVisualFire();
}
