package com.stella.tower.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The last blow's size, which decides how much of the next blow within the cooldown lands. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("lastHurt")
	float stellaTower$lastHurt();

	@Accessor("lastHurt")
	void stellaTower$setLastHurt(float lastHurt);
}
