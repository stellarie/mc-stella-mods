package com.stella.equips.unique.mixin.af;

import com.stella.equips.unique.af.PhasebiteBow;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the teleport at the arrow's first impact. */
@Mixin(AbstractArrow.class)
public abstract class PhasebiteArrowMixin {
	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void stella$phasebiteEntity(final EntityHitResult hit, final CallbackInfo ci) {
		PhasebiteBow.land((AbstractArrow) (Object) this, hit.getEntity().position());
	}

	@Inject(method = "onHitBlock", at = @At("TAIL"))
	private void stella$phasebiteBlock(final BlockHitResult hit, final CallbackInfo ci) {
		Direction face = hit.getDirection();
		Vec3 landing = hit.getLocation().add(face.getStepX() * 0.4, Math.max(0, face.getStepY()) * 0.01,
			face.getStepZ() * 0.4);
		PhasebiteBow.land((AbstractArrow) (Object) this, landing);
	}
}
