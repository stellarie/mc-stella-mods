package com.stella.equips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.equips.heart.SlimeHeart;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Runs after the other mods' damage hooks (priority 1100), so it splits the fully mitigated hit. */
@Mixin(value = LivingEntity.class, priority = 1100)
public abstract class SlimeHeartMixin {
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float stellaEquips$slimeHeart(final float damage, final DamageSource source) {
		return SlimeHeart.adjust((LivingEntity) (Object) this, source, damage);
	}
}
