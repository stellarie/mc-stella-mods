package com.stella.rpg.mixin;

import com.stella.rpg.roll.CraftSum;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SmithingTransformRecipe.class)
public abstract class RpgSmithingSumMixin {
	@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SmithingRecipeInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
	private void stellaRpg$sumAddition(final SmithingRecipeInput input, final CallbackInfoReturnable<ItemStack> callback) {
		callback.setReturnValue(CraftSum.smithed(callback.getReturnValue(), input.addition()));
	}
}
