package com.stella.mobs.mixin;

import com.stella.mobs.shield.ShieldZones;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob never holds a player who stands in a Shield Generator's box. A hostile mob inside a box holds no target at
 * all. Every target goal and every custom caller ends in {@code setTarget}, so one gate covers them all, and a held
 * target is dropped on the next AI step.
 */
@Mixin(Mob.class)
public abstract class ShieldAggroMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$cannotAttackIntoShield(final LivingEntity target, final CallbackInfoReturnable<Boolean> callback) {
		if (ShieldZones.refusesTarget((Mob) (Object) this, target)) {
			callback.setReturnValue(false);
		}
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$refuseShieldedTarget(final LivingEntity target, final CallbackInfo callback) {
		if (ShieldZones.refusesTarget((Mob) (Object) this, target)) {
			callback.cancel();
		}
	}

	@Inject(method = "serverAiStep", at = @At("HEAD"))
	private void stellaMobs$dropShieldedTarget(final CallbackInfo callback) {
		Mob mob = (Mob) (Object) this;
		LivingEntity held = mob.getTargetUnchecked();
		if (held != null && ShieldZones.refusesTarget(mob, held)) {
			mob.setTarget(null);
		}
	}
}
