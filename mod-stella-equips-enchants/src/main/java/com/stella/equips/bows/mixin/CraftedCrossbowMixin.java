package com.stella.equips.bows.mixin;

import com.stella.equips.bows.CraftedBows;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The vanilla charge duration is static and read by the pull animation too, so the crafted time is set at its return. */
@Mixin(CrossbowItem.class)
public abstract class CraftedCrossbowMixin {
	@Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
	private static void stella$craftedCharge(final ItemStack weapon, final LivingEntity holder, final CallbackInfoReturnable<Integer> cir) {
		if (CraftedBows.def(weapon) != null) {
			cir.setReturnValue(CraftedBows.scaledTicks(weapon, cir.getReturnValueI()));
		}
	}
}
