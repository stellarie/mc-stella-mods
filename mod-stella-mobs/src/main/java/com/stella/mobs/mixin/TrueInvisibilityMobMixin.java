package com.stella.mobs.mixin;

import com.stella.mobs.behavior.TrueInvisibility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob that is not undead never takes an invisible player as its target, and drops one it already holds. Every
 * target goal and every custom caller ends in {@code setTarget}, so one gate covers them all.
 */
@Mixin(Mob.class)
public abstract class TrueInvisibilityMobMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$cannotAttackWhatItCannotSee(final LivingEntity target, final CallbackInfoReturnable<Boolean> callback) {
		if (TrueInvisibility.hides(target, (Mob) (Object) this)) {
			callback.setReturnValue(false);
		}
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$refuseHiddenTarget(final LivingEntity target, final CallbackInfo callback) {
		if (target != null && TrueInvisibility.hides(target, (Mob) (Object) this)) {
			callback.cancel();
		}
	}

	@Inject(method = "serverAiStep", at = @At("HEAD"))
	private void stellaMobs$dropHiddenTarget(final CallbackInfo callback) {
		Mob mob = (Mob) (Object) this;
		LivingEntity held = mob.getTargetUnchecked();
		if (held != null && TrueInvisibility.hides(held, mob)) {
			mob.setTarget(null);
		}
	}
}
