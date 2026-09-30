package com.stella.rpg.client.mixin.client;

import com.stella.rpg.RpgDamageTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A damage-over-time tick still plays the hurt sound, but the local camera does not tilt. */
@Mixin(LivingEntity.class)
public abstract class NoDotShakeLivingMixin {
	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void stellaRpg$noDotShake(final DamageSource source, final CallbackInfo callback) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self == Minecraft.getInstance().player && source.is(RpgDamageTypes.NO_HURT_SHAKE)) {
			self.hurtTime = 0;
		}
	}
}
