package com.stella.end.mixin;

import com.stella.end.DraconicSmithingRecipe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taking a draconic piece pays the whole cost in the addition slot; vanilla's own step that follows pays the last one. */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void stellaEnd$payTheFullCost(final Player player, final ItemStack carried, final CallbackInfo ci) {
		ItemCombinerAccess access = (ItemCombinerAccess) this;
		RecipeHolder<?> used = access.stellaEnd$resultSlots().getRecipeUsed();
		if (used != null && used.value() instanceof DraconicSmithingRecipe recipe) {
			ItemStack addition = access.stellaEnd$inputSlots().getItem(SmithingMenu.ADDITIONAL_SLOT);
			if (!addition.isEmpty()) {
				addition.shrink(recipe.additionCount() - 1);
			}
		}
	}
}
