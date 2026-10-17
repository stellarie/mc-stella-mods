package com.stella.end.mixin;

import com.stella.end.DamageRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every blow lands through this method, the dragon's own included (its override ends in a call to it), so the roost, the
 * crystals and the draconic blade change the damage here, before any other rule reads it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaEnd$scaleTheBlow(final float damage, final ServerLevel level, final DamageSource source) {
		return DamageRules.scale((LivingEntity) (Object) this, source, damage);
	}
}
