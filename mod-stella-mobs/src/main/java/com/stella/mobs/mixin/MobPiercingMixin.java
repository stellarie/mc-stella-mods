package com.stella.mobs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stella.mobs.scaling.ArmorNormalizer;
import com.stella.mobs.scaling.MobPiercing;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mob piercing removes a share of armor, toughness, and Protection. Armor also judges a mob's hit at the mob's unscaled
 * damage ({@link ArmorNormalizer}); this one wrap owns the armor call, so the two rules apply once and together.
 */
@Mixin(LivingEntity.class)
public abstract class MobPiercingMixin {
	@WrapOperation(
		method = "getDamageAfterArmorAbsorb",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F")
	)
	private float stellaMobs$armorAgainstBaseHit(
		final LivingEntity victim, final float damage, final DamageSource source, final float armor, final float toughness,
		final Operation<Float> original
	) {
		float kept = 1.0F - MobPiercing.share(source);
		float base = (float) (damage / ArmorNormalizer.scale(source));
		float after = original.call(victim, base, source, armor * kept, toughness * kept);
		return base <= 0.0F ? after : damage * (after / base);
	}

	@WrapOperation(
		method = "getDamageAfterArmorAbsorb",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurtArmor(Lnet/minecraft/world/damagesource/DamageSource;F)V")
	)
	private void stellaMobs$wearOfBaseHit(final LivingEntity self, final DamageSource source, final float damage, final Operation<Void> original) {
		original.call(self, source, (float) (damage / ArmorNormalizer.scale(source)));
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
