package com.stella.rpg.mixin;

import com.stella.rpg.roll.CraftSum;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SingleItemRecipe.class)
public abstract class RpgSmeltSumMixin {
	@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
	private void stellaRpg$keepSmelted(final SingleRecipeInput input, final CallbackInfoReturnable<ItemStack> callback) {
		// Stonecutting is also a single item recipe. It must not copy a bonus.
		if ((Object) this instanceof AbstractCookingRecipe) {
			callback.setReturnValue(CraftSum.smelted(callback.getReturnValue(), input.item()));
		}
	}
}
