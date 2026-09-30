package com.stella.mobs.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.stella.mobs.scaling.MobPiercing;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class MobPiercingMixin {
	@ModifyArg(
		method = "getDamageAfterArmorAbsorb",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"),
		index = 3
	)
	private float stellaMobs$pierceArmor(final float armor, @Local(argsOnly = true) final DamageSource source) {
		return armor * (1.0F - MobPiercing.share(source));
	}

	@ModifyArg(
		method = "getDamageAfterArmorAbsorb",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"),
		index = 4
	)
	private float stellaMobs$pierceToughness(final float toughness, @Local(argsOnly = true) final DamageSource source) {
		return toughness * (1.0F - MobPiercing.share(source));
	}

	@ModifyArg(
		method = "getDamageAfterMagicAbsorb",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterMagicAbsorb(FF)F"),
		index = 1
	)
	private float stellaMobs$pierceProtection(final float protection, @Local(argsOnly = true) final DamageSource source) {
		return protection * (1.0F - MobPiercing.share(source));
	}
}
