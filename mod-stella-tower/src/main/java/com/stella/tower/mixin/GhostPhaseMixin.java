package com.stella.tower.mixin;

import com.stella.tower.GhostRunes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player phased by a ghost rune and a mob pass through each other: neither pushes the other. */
@Mixin(Entity.class)
public abstract class GhostPhaseMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void stellaTower$phase(final Entity other, final CallbackInfo ci) {
		if (GhostRunes.passThrough((Entity) (Object) this, other)) {
			ci.cancel();
		}
	}
}
