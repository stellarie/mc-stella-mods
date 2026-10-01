package com.stella.mobs.mixin;

import com.stella.mobs.behavior.DangerSense;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blocked target returns early, so only a real new target reaches the tail. */
@Mixin(Mob.class)
public abstract class DangerSenseMixin {
	@Inject(method = "setTarget", at = @At("TAIL"))
	private void stellaMobs$dangerSense(final LivingEntity target, final CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		if (target != null && !mob.level().isClientSide()) {
			DangerSense.check(mob, target);
		}
	}
}
