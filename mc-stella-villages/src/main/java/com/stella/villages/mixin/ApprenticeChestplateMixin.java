package com.stella.villages.mixin;

import com.stella.villages.item.ApprenticeChestplate;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class ApprenticeChestplateMixin {
	// Chains with the other mods' damage hooks; a cancelling inject would skip them.
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float mcStellaVillages$darkGuard(final float damage, final DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		return damage * (1.0F - ApprenticeChestplate.reduction((LivingEntity) (Object) this));
	}
}
