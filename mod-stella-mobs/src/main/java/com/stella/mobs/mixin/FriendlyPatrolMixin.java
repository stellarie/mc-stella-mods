package com.stella.mobs.mixin;

import com.stella.mobs.behavior.FriendlyPatrol;
import com.stella.mobs.entity.BlueSlime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No mob targets a blue slime, and an iron or snow golem never targets a friendly patrol. */
@Mixin(Mob.class)
public abstract class FriendlyPatrolMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$golemsSpareFriendlyPatrols(final LivingEntity target, final CallbackInfoReturnable<Boolean> callback) {
		if (BlueSlime.isHarmless(target) || (Object) this instanceof AbstractGolem && FriendlyPatrol.isFriendly(target)) {
			callback.setReturnValue(false);
		}
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$neverTargetBlueSlimes(final LivingEntity target, final CallbackInfo callback) {
		if (BlueSlime.isHarmless(target)) {
			callback.cancel();
		}
	}
}
