package com.stella.mobs.mixin;

import com.stella.mobs.behavior.FriendlyPatrol;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An iron or snow golem never takes a friendly patrol as its target. */
@Mixin(Mob.class)
public abstract class FriendlyPatrolMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$golemsSpareFriendlyPatrols(final LivingEntity target, final CallbackInfoReturnable<Boolean> callback) {
		if ((Object) this instanceof AbstractGolem && FriendlyPatrol.isFriendly(target)) {
			callback.setReturnValue(false);
		}
	}
}
