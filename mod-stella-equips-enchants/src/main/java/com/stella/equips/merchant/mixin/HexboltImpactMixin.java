package com.stella.equips.merchant.mixin;

import com.stella.equips.merchant.MerchantWeapons;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Hexbolt bursts where it lands, on a body or on a block, before the arrow's own hit runs. */
@Mixin(AbstractArrow.class)
public abstract class HexboltImpactMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void stella$hexboltBodyImpact(final EntityHitResult hit, final CallbackInfo ci) {
		MerchantWeapons.impact((AbstractArrow) (Object) this, hit.getLocation(), hit.getEntity());
	}

	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void stella$hexboltBlockImpact(final BlockHitResult hit, final CallbackInfo ci) {
		MerchantWeapons.impact((AbstractArrow) (Object) this, hit.getLocation(), null);
	}
}
