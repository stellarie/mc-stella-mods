package com.stella.equips.mixin;

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

	@Inject(method = "createResult", at = @At("RETURN"))
	private void stellaEquips$clampCost(final CallbackInfo ci) {
		DataSlot cost = this.stellaEquips$costSlot();
		cost.set(AnvilCost.clamp(cost.get()));
	}
}
