package com.stella.end.mixin;

import com.stella.end.Factions;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blow sets whom a mob answers; a blow from another castle mob sets nobody, so the retaliation goal never starts. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAngerMixin {
	@Inject(method = "setLastHurtByMob", at = @At("HEAD"), cancellable = true)
	private void stellaEnd$forgiveTheCastle(final LivingEntity attacker, final CallbackInfo ci) {
		if (attacker != null && Factions.sameSide((LivingEntity) (Object) this, attacker)) {
			ci.cancel();
		}
	}
}
