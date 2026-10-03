package com.stella.rpg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stella.rpg.RpgCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class RpgArmorPenMixin {
	// A wrap chains with the mobs mod piercing wrap on the same call; a redirect would not.
	@WrapOperation(
		method = "getDamageAfterArmorAbsorb",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F")
	)
	private float stellaRpg$ignoreArmorShare(
		final LivingEntity victim, final float damage, final DamageSource source, final float armor, final float toughness,
		final Operation<Float> original
	) {
		float kept = 1.0F - (float) RpgCombat.armorIgnored(victim, source);
		return original.call(victim, damage, source, armor * kept, toughness * kept);
	}
}
