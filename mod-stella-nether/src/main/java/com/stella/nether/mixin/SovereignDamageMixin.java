package com.stella.nether.mixin;

import com.stella.nether.BlazeSovereign;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A Sovereign too big for the health attribute takes every hit through its divisor. */
@Mixin(LivingEntity.class)
public abstract class SovereignDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaNether$divide(final float amount, final ServerLevel level, final DamageSource source) {
		return BlazeSovereign.scaleDamageTaken((LivingEntity) (Object) this, amount);
	}
}
