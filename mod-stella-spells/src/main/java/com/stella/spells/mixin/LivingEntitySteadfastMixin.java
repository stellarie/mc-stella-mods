package com.stella.spells.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.stella.spells.SteadfastPrayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Steadfast Prayer, at the two vanilla doors it has to hold. Both addEffect overloads ask
 * {@code canBeAffected} first, so one refusal covers every harmful status a mod or the game applies; and
 * a blow a holder deals is scaled as it lands, whatever dealt it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySteadfastMixin {
	@Inject(method = "canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("HEAD"), cancellable = true)
	private void stella$refuseTheHarmfulStatus(final MobEffectInstance effect, final CallbackInfoReturnable<Boolean> cir) {
		if (SteadfastPrayer.refuses((LivingEntity) (Object) this, effect)) {
			cir.setReturnValue(false);
		}
	}

	@ModifyVariable(
		method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private float stella$weakenTheBlowOfAHolder(final float amount, @Local(argsOnly = true) final DamageSource source) {
		Entity dealer = source.getEntity();
		return dealer instanceof LivingEntity living ? SteadfastPrayer.outgoing(living, amount) : amount;
	}
}
