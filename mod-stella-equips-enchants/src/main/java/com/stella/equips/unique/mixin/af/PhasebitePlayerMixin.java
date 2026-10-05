package com.stella.equips.unique.mixin.af;

import com.stella.equips.unique.af.PhasebiteBow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps teleport fall protection after the player changes weapons. */
@Mixin(ServerPlayer.class)
public abstract class PhasebitePlayerMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void stella$phasebiteFall(final ServerLevel level, final DamageSource source, final float damage,
		final CallbackInfoReturnable<Boolean> cir) {
		if (PhasebiteBow.protects((ServerPlayer) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}
}
