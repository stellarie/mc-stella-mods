package com.stella.equips.mixin;

import com.stella.equips.anvil.AnvilCost;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {
	@Inject(method = "getCost", at = @At("RETURN"), cancellable = true)
	private void stellaEquips$clampCost(final CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(AnvilCost.clamp(cir.getReturnValueI()));
	}
}
