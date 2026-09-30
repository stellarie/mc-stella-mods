package com.stella.rpg.mixin;

import com.stella.rpg.PercentEffects;
import com.stella.rpg.RpgScale;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Absorption grant follows the percent rule. The max must fit the grant, or the clamp cuts it. */
@Mixin(LivingEntity.class)
public abstract class RpgAbsorptionMixin {
	@Inject(method = "getMaxAbsorption", at = @At("RETURN"), cancellable = true)
	private void stellaRpg$roomForGrant(final CallbackInfoReturnable<Float> callback) {
		LivingEntity self = (LivingEntity) (Object) this;
		MobEffectInstance effect = self.getEffect(MobEffects.ABSORPTION);
		if (effect != null) {
			float grant = PercentEffects.absorption(self, effect.getAmplifier()) * RpgScale.kf();
			callback.setReturnValue(Math.max(callback.getReturnValue(), grant));
		}
	}
}
