package com.stella.rpg.mixin;

import com.stella.rpg.EnvironmentDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class RpgEnvironmentDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaRpg$scaleEnvironment(final float amount, final ServerLevel level, final DamageSource source) {
		return EnvironmentDamage.scale((LivingEntity) (Object) this, source, amount);
	}
}
