package com.stella.rpg.mixin;

import com.stella.rpg.PercentEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Instant Health and Instant Damage use the percent rules in vanilla units. K applies in heal and damage. */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class RpgInstantEffectMixin {
	@Shadow
	@Final
	private boolean isHarm;

	@Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$tick(
		final ServerLevel level, final LivingEntity mob, final int amplification, final CallbackInfoReturnable<Boolean> callback
	) {
		this.stellaRpg$apply(level, null, null, mob, amplification, 1.0);
		callback.setReturnValue(true);
	}

	@Inject(method = "applyInstantaneousEffect", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$instant(
		final ServerLevel level, final @Nullable Entity source, final @Nullable Entity owner, final LivingEntity mob,
		final int amplification, final double scale, final CallbackInfo callback
	) {
		this.stellaRpg$apply(level, source, owner, mob, amplification, scale);
		callback.cancel();
	}

	private void stellaRpg$apply(
		final ServerLevel level, final @Nullable Entity source, final @Nullable Entity owner, final LivingEntity mob,
		final int amplification, final double scale
	) {
		if (this.isHarm == mob.isInvertedHealAndHarm()) {
			mob.heal((float) (PercentEffects.instantHeal(mob, amplification) * scale));
			return;
		}
		float amount = (float) (PercentEffects.instantHarm(mob, amplification) * scale);
		if (source == null) {
			mob.hurtServer(level, mob.damageSources().magic(), amount);
		} else {
			mob.hurtServer(level, mob.damageSources().indirectMagic(source, owner), amount);
		}
	}
}
