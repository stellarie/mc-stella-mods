package com.stella.rpg.client.mixin.client;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nausea makes attacks fail instead of warping the view: the spin, the wobble, and the overlay read this blend. */
@Mixin(LivingEntity.class)
public abstract class NauseaBlendMixin {
	@Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$noNauseaWarp(final Holder<MobEffect> effect, final float partialTicks, final CallbackInfoReturnable<Float> info) {
		if (effect.is(MobEffects.NAUSEA) && ((LivingEntity) (Object) this).level().isClientSide()) {
			info.setReturnValue(0.0F);
		}
	}
}
