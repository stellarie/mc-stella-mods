package com.stella.rpg.mixin;

import com.stella.rpg.RpgScale;
import com.stella.rpg.job.JobItems;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class RpgScaleHealMixin {
	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float stellaRpg$scaleHeal(final float heal) {
		return heal * RpgScale.kf();
	}

	@Inject(method = "heal", at = @At("HEAD"))
	private void stellaRpg$healPerks(final float heal, final CallbackInfo ci) {
		JobItems.onHeal((LivingEntity) (Object) this);
	}
}
