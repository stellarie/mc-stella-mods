package com.stella.rpg.mixin;

import com.stella.rpg.PercentEffects;
import com.stella.rpg.RpgScale;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.effect.AbsorptionMobEffect")
public abstract class RpgAbsorptionGrantMixin {
	@Redirect(
		method = "onEffectStarted",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V")
	)
	private void stellaRpg$grant(final LivingEntity target, final float vanilla, final LivingEntity mob, final int amplifier) {
		float grant = PercentEffects.absorption(target, amplifier) * RpgScale.kf();
		target.setAbsorptionAmount(Math.max(target.getAbsorptionAmount(), grant));
	}
}
