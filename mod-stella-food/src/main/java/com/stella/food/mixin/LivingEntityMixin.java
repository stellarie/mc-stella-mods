package com.stella.food.mixin;

import com.stella.food.FoodEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "decreaseAirSupply", at = @At("HEAD"), cancellable = true)
	private void stellaFood$slowAirLoss(
		final int currentSupply,
		final CallbackInfoReturnable<Integer> callback
	) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity.hasEffect(FoodEffects.DEEP_BREATH) && (entity.tickCount & 1) == 0) {
			callback.setReturnValue(currentSupply);
		}
	}

	// Chains with the other mods' damage hooks; a cancelling inject would skip them.
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float stellaFood$reduceFireDamage(final float damage, final DamageSource source) {
		LivingEntity entity = (LivingEntity) (Object) this;
		return source.is(DamageTypeTags.IS_FIRE) && entity.hasEffect(FoodEffects.EMBER_SKIN) ? damage * 0.60F : damage;
	}
}
