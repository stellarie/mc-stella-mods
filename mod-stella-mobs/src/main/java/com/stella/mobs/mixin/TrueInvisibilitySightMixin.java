package com.stella.mobs.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.stella.mobs.behavior.TrueInvisibility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla only shrinks the sight range of an invisible player and keeps a two block floor under it. A hidden player
 * shows as zero visibility. An undead looker, or any looker once the player is revealed, sees the player as if the
 * effect were absent.
 */
@Mixin(LivingEntity.class)
public abstract class TrueInvisibilitySightMixin {
	@Inject(method = "getVisibilityPercent", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$hiddenFromLooker(final ServerLevel level, final Entity looker, final CallbackInfoReturnable<Double> callback) {
		if (TrueInvisibility.hides((LivingEntity) (Object) this, looker)) {
			callback.setReturnValue(0.0);
		}
	}

	@ModifyExpressionValue(
		method = "getVisibilityPercent",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisible()Z")
	)
	private boolean stellaMobs$seenDespiteInvisibility(final boolean invisible, @Local(argsOnly = true) final Entity looker) {
		return invisible && !((Object) this instanceof Player player && TrueInvisibility.seenDespiteInvisibility(player, looker));
	}
}
