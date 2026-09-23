package com.stella.mobs.mixin;

import com.stella.mobs.behavior.SnowGolemCold;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * G06 and G08: vanilla's snowball hit deals zero to everything but a blaze and applies nothing at all, so
 * the payload is injected after it. The tail is what keeps the clause an addition: vanilla's own hit, its
 * zero, and its blaze exception all still run first.
 */
@Mixin(Snowball.class)
public class SnowballHitMixin {
	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void stella$coldPayload(final EntityHitResult hitResult, final CallbackInfo info) {
		SnowGolemCold.onSnowballHit((Snowball) (Object) this, hitResult.getEntity());
	}
}
