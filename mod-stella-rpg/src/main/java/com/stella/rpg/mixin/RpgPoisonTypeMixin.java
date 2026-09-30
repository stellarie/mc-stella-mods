package com.stella.rpg.mixin;

import com.stella.rpg.RpgDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.PoisonMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PoisonMobEffect.class)
public abstract class RpgPoisonTypeMixin {
	@Redirect(
		method = "applyEffectTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSources;magic()Lnet/minecraft/world/damagesource/DamageSource;")
	)
	private DamageSource stellaRpg$poisonType(final DamageSources sources, final net.minecraft.server.level.ServerLevel level,
		final LivingEntity mob, final int amplification) {
		return RpgDamageTypes.poison(mob);
	}
}
