package com.stella.spells.test.mixin;

import com.stella.spells.KScaleHarness;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Test harness only: K multiplies the damage that reaches health, as the RPG mod does. */
@Mixin(LivingEntity.class)
public abstract class KScaleDamageMixin {
	@ModifyVariable(
		method = "actuallyHurt",
		at = @At(
			value = "INVOKE_ASSIGN",
			target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"
		),
		argsOnly = true
	)
	private float stellaSpellsTest$scaleDamage(final float damage) {
		return damage * KScaleHarness.factor();
	}
}
