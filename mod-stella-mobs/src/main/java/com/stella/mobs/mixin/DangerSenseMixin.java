package com.stella.mobs.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stella.mobs.behavior.DangerSense;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Danger sense hears a hunt start where the target is stored. A target goal re-sets the same target every few ticks,
 * and the stored target can be null when the mob may not attack, so only a new stored target counts. A blocked target
 * returns early at the head, so it never reaches the tail.
 */
@Mixin(Mob.class)
public abstract class DangerSenseMixin {
	@Inject(method = "setTarget", at = @At("HEAD"))
	private void stellaMobs$targetBefore(final LivingEntity target, final CallbackInfo ci, @Share("dangerBefore") final LocalRef<LivingEntity> before) {
		before.set(((Mob) (Object) this).getTargetUnchecked());
	}

	@Inject(method = "setTarget", at = @At("TAIL"))
	private void stellaMobs$dangerSense(final LivingEntity target, final CallbackInfo ci, @Share("dangerBefore") final LocalRef<LivingEntity> before) {
		Mob mob = (Mob) (Object) this;
		LivingEntity stored = mob.getTargetUnchecked();
		if (stored != null && stored != before.get() && !mob.level().isClientSide()) {
			DangerSense.check(mob, stored);
		}
	}
}
