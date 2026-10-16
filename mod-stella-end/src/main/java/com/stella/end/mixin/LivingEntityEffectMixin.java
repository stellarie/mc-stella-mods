package com.stella.end.mixin;

import com.stella.end.DraconicGear;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Whoever wears all four pieces of the draconic set cannot be withered or slowed. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void stellaEnd$draconicSetRefusesWitherAndSlowness(final MobEffectInstance effect, final CallbackInfoReturnable<Boolean> cir) {
		if ((effect.is(MobEffects.WITHER) || effect.is(MobEffects.SLOWNESS)) && DraconicGear.wearsFullSet((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
