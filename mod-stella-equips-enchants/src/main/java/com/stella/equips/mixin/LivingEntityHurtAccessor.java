package com.stella.equips.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityHurtAccessor {
	@Accessor("lastHurt")
	float stellaEquips$getLastHurt();

	@Accessor("lastHurt")
	void stellaEquips$setLastHurt(float lastHurt);
}
