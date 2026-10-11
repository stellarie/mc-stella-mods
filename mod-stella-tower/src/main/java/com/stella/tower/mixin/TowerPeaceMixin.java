package com.stella.tower.mixin;

import com.stella.tower.TowerPeace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hit that {@link TowerPeace} spares never lands: it is turned away before it hurts, knocks, marks its victim as hurt
 * by the attacker, or sets the victim against it. Every kind of hit passes here: a swing, an arrow, a wind charge, a blast.
 */
@Mixin(LivingEntity.class)
public abstract class TowerPeaceMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void stellaTower$keepThePeace(final ServerLevel level, final DamageSource source, final float amount, final CallbackInfoReturnable<Boolean> cir) {
		if (TowerPeace.spares((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}
}
