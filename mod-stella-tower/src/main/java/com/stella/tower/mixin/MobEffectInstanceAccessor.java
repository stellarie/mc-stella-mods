package com.stella.tower.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The weaker dose a stronger one holds underneath, so taking a boss floor's buff away gives a player's own potion back. */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {
	@Accessor("hiddenEffect")
	MobEffectInstance stellaTower$hidden();
}
