package com.stella.end.mixin;

import com.stella.end.SoulCharges;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A small fireball that lands on a block sets fire to the cell beside it. The Sovereign rains charges over a hall of
 * wood, wool and books, so a soul charge sets none; it still hurts what it hits and withers it.
 */
@Mixin(SmallFireball.class)
public abstract class SmallFireballMixin {
	@Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
	private void stellaEnd$burnNothing(final BlockHitResult hit, final CallbackInfo ci) {
		if (SoulCharges.is((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
