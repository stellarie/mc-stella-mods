package com.stella.rpg.mixin;

import com.stella.rpg.PercentEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RpgRegenerationAmountMixin {
	@Redirect(
		method = "applyEffectTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V")
	)
	private void stellaRpg$regenerationAmount(final LivingEntity target, final float vanilla) {
		target.heal(PercentEffects.tick(target));
	}
}
