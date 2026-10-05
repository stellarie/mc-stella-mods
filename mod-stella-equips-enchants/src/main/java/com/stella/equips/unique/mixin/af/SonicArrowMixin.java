package com.stella.equips.unique.mixin.af;

import com.stella.equips.unique.af.SonicBow;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuses the Sonic beam's critical sample for the arrow's own hit. */
@Mixin(AbstractArrow.class)
public abstract class SonicArrowMixin {
	@Redirect(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int stella$sonicCritical(final RandomSource random, final int bound) {
		return SonicBow.criticalBonus((AbstractArrow) (Object) this, random, bound);
	}
}
