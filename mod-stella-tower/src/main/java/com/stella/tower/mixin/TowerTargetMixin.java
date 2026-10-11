package com.stella.tower.mixin;

import com.stella.tower.TowerPeace;
import com.stella.tower.TowerSafety;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob is never given a player standing in a safe zone as a target, and a tower mob is never given a fellow. Both
 * {@code setTarget} and {@code getTarget} go through {@code asValidTarget}, so a target already held reads as none the
 * moment its player steps in, before the sweep lets go of it, and no goal, whatever its kind, can take one up.
 * {@code canAttack} is where the targeting goals ask first, so a goal never so much as picks a fellow.
 */
@Mixin(Mob.class)
public abstract class TowerTargetMixin {
	@Inject(method = "asValidTarget", at = @At("HEAD"), cancellable = true)
	private void stellaTower$noAggroInASafeZone(final LivingEntity target, final CallbackInfoReturnable<LivingEntity> cir) {
		if (TowerSafety.sheltered(target) || TowerPeace.fellows((Mob) (Object) this, target)) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void stellaTower$noQuarrelWithAFellow(final LivingEntity target, final CallbackInfoReturnable<Boolean> cir) {
		if (TowerPeace.fellows((Mob) (Object) this, target)) {
			cir.setReturnValue(false);
		}
	}
}
