package com.stella.rpg.client.mixin.client;

import com.stella.rpg.RpgDamageTypes;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The health packet also tilts the camera; the damage event came first and set the source. */
@Mixin(LocalPlayer.class)
public abstract class NoDotShakeLocalPlayerMixin {
	@Inject(method = "hurtTo", at = @At("TAIL"))
	private void stellaRpg$noDotShake(final float newHealth, final CallbackInfo callback) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		DamageSource last = self.getLastDamageSource(1);
		if (last != null && last.is(RpgDamageTypes.NO_HURT_SHAKE)) {
			self.hurtTime = 0;
		}
	}
}
