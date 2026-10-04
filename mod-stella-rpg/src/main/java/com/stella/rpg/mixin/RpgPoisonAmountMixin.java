package com.stella.rpg.mixin;

import com.stella.rpg.PercentEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.PoisonMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PoisonMobEffect.class)
public abstract class RpgPoisonAmountMixin {
	@Redirect(
		method = "applyEffectTick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean stellaRpg$poisonAmount(
		final LivingEntity target, final ServerLevel level, final DamageSource source, final float vanilla
	) {
		return PercentEffects.fixedHit(() -> target.hurtServer(level, source, PercentEffects.survivableTick(target)));
	}
}
