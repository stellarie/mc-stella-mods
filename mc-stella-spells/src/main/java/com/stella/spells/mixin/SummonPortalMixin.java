package com.stella.spells.mixin;

import com.stella.spells.SummonSkeleton;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Summoned skeletons change worlds with their owner instead of entering portals themselves. */
@Mixin(Entity.class)
public abstract class SummonPortalMixin {
	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void stellaSpells$blockSummonPortal(final boolean allowPassengers, final CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Skeleton skeleton && skeleton.entityTags().contains(SummonSkeleton.SUMMON_TAG)) {
			cir.setReturnValue(false);
		}
	}
}
