package com.stella.rpg.mixin;

import com.stella.rpg.PercentEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.WitherMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WitherMobEffect.class)
public abstract class RpgWitherAmountMixin {
	@Redirect(
		method = "applyEffectTick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean stellaRpg$witherAmount(
		final LivingEntity target, final ServerLevel level, final DamageSource source, final float vanilla
	) {
		return target.hurtServer(level, source, PercentEffects.tick(target));
	}
}
