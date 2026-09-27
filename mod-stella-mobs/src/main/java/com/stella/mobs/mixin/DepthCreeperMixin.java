package com.stella.mobs.mixin;

import com.stella.mobs.behavior.DepthMobs;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Depth creeper's blast leaves fire behind; the blast itself stays vanilla's. */
@Mixin(Creeper.class)
public class DepthCreeperMixin {
	@Inject(method = "explodeCreeper", at = @At("TAIL"))
	private void stella$depthFire(final CallbackInfo info) {
		DepthMobs.afterBlast((Creeper) (Object) this);
	}
}
