package com.stella.rpg.mixin;

import com.stella.rpg.RpgScale;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class RpgScaleHealMixin {
	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float stellaRpg$scaleHeal(final float heal) {
		return heal * RpgScale.kf();
	}
}
