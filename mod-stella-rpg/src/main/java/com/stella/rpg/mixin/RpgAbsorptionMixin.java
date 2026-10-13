package com.stella.rpg.mixin;

import com.stella.rpg.PercentEffects;
import com.stella.rpg.RpgScale;
import com.stella.rpg.job.ArcaneWard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Absorption grant follows the percent rule, and the Mage ward has a size of its own. The max must fit both, or the clamp cuts them. */
@Mixin(LivingEntity.class)
public abstract class RpgAbsorptionMixin {
	@Inject(method = "getMaxAbsorption", at = @At("RETURN"), cancellable = true)
	private void stellaRpg$roomForGrant(final CallbackInfoReturnable<Float> callback) {
		LivingEntity self = (LivingEntity) (Object) this;
		float max = callback.getReturnValue();
		MobEffectInstance effect = self.getEffect(MobEffects.ABSORPTION);
		if (effect != null) {
			max = Math.max(max, PercentEffects.absorption(self, effect.getAmplifier()) * RpgScale.kf());
		}
		if (self instanceof ServerPlayer player) {
			max = Math.max(max, ArcaneWard.capacity(player));
		}
		callback.setReturnValue(max);
	}
}
