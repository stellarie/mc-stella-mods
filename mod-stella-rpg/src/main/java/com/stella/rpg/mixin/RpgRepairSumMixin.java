package com.stella.rpg.mixin;

import com.stella.rpg.roll.CraftSum;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RepairItemRecipe.class)
public abstract class RpgRepairSumMixin {
	@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
	private void stellaRpg$keepBetter(final CraftingInput input, final CallbackInfoReturnable<ItemStack> callback) {
		callback.setReturnValue(CraftSum.repaired(callback.getReturnValue(), input.items()));
	}
}
