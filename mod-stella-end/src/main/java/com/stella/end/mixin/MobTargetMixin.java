package com.stella.end.mixin;

import com.stella.end.Factions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every goal that picks a target ends in this method, so a castle mob that is offered another castle mob keeps no target. */
@Mixin(Mob.class)
public abstract class MobTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void stellaEnd$keepTheCastleTogether(final LivingEntity target, final CallbackInfo ci) {
		if (target != null && Factions.sameSide((Mob) (Object) this, target)) {
			ci.cancel();
		}
	}
}
