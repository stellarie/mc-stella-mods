package com.stella.spells.mixin;

import com.stella.spells.ArrowHits;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blast arrow bursts where it strikes a block. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowHitMixin {
	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void stellaSpells$blastOnBlock(final BlockHitResult hit, final CallbackInfo info) {
		ArrowHits.onHitBlock((AbstractArrow) (Object) this, hit.getLocation());
	}
}
