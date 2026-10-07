package com.stella.mobs.mixin;

import com.stella.mobs.behavior.SkeletonVolley;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A skeleton's single shot slows what it hits, and that is the only place a single shot's slow is applied.
 * The head is used because vanilla's own entity hit can discard the arrow. Vanilla's hit still runs in full after it.
 */
@Mixin(AbstractArrow.class)
public class ArrowVolleyHitMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void stella$onEntityHit(final EntityHitResult hitResult, final CallbackInfo info) {
		SkeletonVolley.onEntityHit((AbstractArrow) (Object) this, hitResult.getEntity());
	}

	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void stella$onBlockHit(final BlockHitResult hitResult, final CallbackInfo info) {
	}
}
