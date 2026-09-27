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
 * M2: a volley arrow leaves its cloud where it hits. The head is used because vanilla's own entity hit can
 * discard the arrow; the cloud is an addition, and vanilla's hit still runs in full after it.
 */
@Mixin(AbstractArrow.class)
public class ArrowVolleyHitMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void stella$volleyCloudOnEntity(final EntityHitResult hitResult, final CallbackInfo info) {
		SkeletonVolley.onHit((AbstractArrow) (Object) this, hitResult.getEntity().position());
		SkeletonVolley.onEntityHit((AbstractArrow) (Object) this, hitResult.getEntity());
		com.stella.mobs.behavior.StrayKit.onArrowHit((AbstractArrow) (Object) this, hitResult.getEntity().blockPosition());
	}

	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void stella$volleyCloudOnBlock(final BlockHitResult hitResult, final CallbackInfo info) {
		SkeletonVolley.onHit((AbstractArrow) (Object) this, hitResult.getLocation());
		com.stella.mobs.behavior.StrayKit.onArrowHit((AbstractArrow) (Object) this, hitResult.getBlockPos().relative(hitResult.getDirection()));
	}
}
