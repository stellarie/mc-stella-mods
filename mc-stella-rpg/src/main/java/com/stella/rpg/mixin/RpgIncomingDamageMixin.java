package com.stella.rpg.mixin;

import com.stella.rpg.RpgCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class RpgIncomingDamageMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void stellaRpg$scaleIncoming(
		final DamageSource source,
		final float damage,
		final CallbackInfoReturnable<Float> callback
	) {
		LivingEntity entity = (LivingEntity) (Object) this;
		callback.setReturnValue(RpgCombat.scaleIncoming(entity, source, callback.getReturnValue()));
	}
}
