package com.stella.rpg.mixin;

import com.stella.rpg.RpgScale;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** K multiplies the damage that reaches health, after armor, Protection, and the RPG defense. */
@Mixin(LivingEntity.class)
public abstract class RpgScaleDamageMixin {
	@ModifyVariable(
		method = "actuallyHurt",
		at = @At(
			value = "INVOKE_ASSIGN",
			target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"
		),
		argsOnly = true
	)
	private float stellaRpg$scaleDamage(final float damage) {
		return damage * RpgScale.kf();
	}
}
