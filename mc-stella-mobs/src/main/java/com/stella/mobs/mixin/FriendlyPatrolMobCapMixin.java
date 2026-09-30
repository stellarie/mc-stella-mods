package com.stella.mobs.mixin;

import com.stella.mobs.behavior.FriendlyPatrol;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class FriendlyPatrolMobCapMixin {
	@Inject(method = "requiresCustomPersistence", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$outOfMobCap(final CallbackInfoReturnable<Boolean> callback) {
		if (FriendlyPatrol.isFriendly((Mob) (Object) this)) {
			callback.setReturnValue(true);
		}
	}
}
