package com.stella.mobs.mixin;

import com.stella.mobs.behavior.StatusResist;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every source of an effect asks canBeAffected, so one gate covers spells, potions and debuffs. */
@Mixin(LivingEntity.class)
public class ElitePoiseMixin {
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void stella$resist(final MobEffectInstance instance, final CallbackInfoReturnable<Boolean> cir) {
		if (StatusResist.resists((LivingEntity) (Object) this, instance)) {
			cir.setReturnValue(false);
		}
	}
}
