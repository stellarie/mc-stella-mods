package com.stella.rpg.mixin;

import com.stella.rpg.RpgCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class RpgOutgoingDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaRpg$scaleOutgoing(final float amount, final ServerLevel level, final DamageSource source) {
		return RpgCombat.scaleOutgoing((LivingEntity) (Object) this, source, amount);
	}
}
