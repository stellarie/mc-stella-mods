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
 * A skeleton's single shot slows what it hits, and a stray's arrow turns its landing spot into powder snow.
 * The head is used because vanilla's own entity hit can discard the arrow. Vanilla's hit still runs in full after it.
 */
@Mixin(AbstractArrow.class)
public class ArrowVolleyHitMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void stella$onEntityHit(final EntityHitResult hitResult, final CallbackInfo info) {
		SkeletonVolley.onEntityHit((AbstractArrow) (Object) this, hitResult.getEntity());
		com.stella.mobs.behavior.StrayKit.onArrowHit((AbstractArrow) (Object) this, hitResult.getEntity().blockPosition());
	}

	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void stella$onBlockHit(final BlockHitResult hitResult, final CallbackInfo info) {
		com.stella.mobs.behavior.StrayKit.onArrowHit((AbstractArrow) (Object) this, hitResult.getBlockPos().relative(hitResult.getDirection()));
	}
}
