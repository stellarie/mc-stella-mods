package com.stella.equips.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stella.equips.anvil.AnvilCost;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Accessor("cost")
	protected abstract DataSlot stellaEquips$costSlot();

	@ModifyExpressionValue(
		method = "createResult",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;get()I", ordinal = 1)
	)
	private int stellaEquips$clampBeforeTooExpensive(final int original) {
		return AnvilCost.clamp(original);
	}

	@Inject(method = "createResult", at = @At("RETURN"))
	private void stellaEquips$clampCost(final CallbackInfo ci) {
		DataSlot cost = this.stellaEquips$costSlot();
		cost.set(AnvilCost.clamp(cost.get()));
	}
}
