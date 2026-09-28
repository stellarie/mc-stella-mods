package com.stella.tower.mixin;

import com.stella.tower.GhostRunes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A ghost-runed weapon's blow passes a raised shield and meets half the target's armor and toughness. */
@Mixin(LivingEntity.class)
public abstract class GhostBlowMixin {
	@Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
	private void stellaTower$passShield(final ServerLevel level, final DamageSource source, final float amount, final CallbackInfoReturnable<Float> cir) {
		if (GhostRunes.ghostBlow(source)) {
			cir.setReturnValue(0.0F);
		}
	}

	@ModifyArg(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"), index = 3)
	private float stellaTower$pierceArmor(final LivingEntity victim, final float damage, final DamageSource source, final float armor, final float toughness) {
		return GhostRunes.ghostBlow(source) ? (float) (armor * (1.0 - GhostRunes.ARMOR_PIERCE)) : armor;
	}

	@ModifyArg(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"), index = 4)
	private float stellaTower$pierceToughness(final LivingEntity victim, final float damage, final DamageSource source, final float armor, final float toughness) {
		return GhostRunes.ghostBlow(source) ? (float) (toughness * (1.0 - GhostRunes.ARMOR_PIERCE)) : toughness;
	}
}
