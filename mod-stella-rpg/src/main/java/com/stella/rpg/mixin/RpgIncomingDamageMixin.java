package com.stella.rpg.mixin;

import com.stella.rpg.RpgCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class RpgIncomingDamageMixin {
	// Chains with the other mods' damage hooks; a cancelling inject would skip them.
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float stellaRpg$scaleIncoming(final float damage, final DamageSource source) {
		return RpgCombat.scaleIncoming((LivingEntity) (Object) this, source, damage);
	}
}
