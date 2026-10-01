package com.stella.villages.mixin;

import com.stella.villages.item.ApprenticeChestplate;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ApprenticeChestplateMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void mcStellaVillages$darkGuard(final DamageSource source, final float damage, final CallbackInfoReturnable<Float> callback) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		float reduction = ApprenticeChestplate.reduction((LivingEntity) (Object) this);
		if (reduction > 0.0F) {
			callback.setReturnValue(callback.getReturnValue() * (1.0F - reduction));
		}
	}
}
