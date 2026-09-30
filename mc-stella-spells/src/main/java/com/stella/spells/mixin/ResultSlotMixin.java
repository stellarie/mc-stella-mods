package com.stella.spells.mixin;

import com.stella.spells.SpellLog;
import com.stella.spells.SpellbookItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
	@Shadow
	private int removeCount;

	@Shadow
	@Final
	private Player player;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void modStellaSpells$logCraft(final ItemStack result, final CallbackInfo ci) {
		if (!(this.player instanceof ServerPlayer)
			|| this.removeCount <= 0
			|| !(result.getItem() instanceof SpellbookItem)) {
			return;
		}

		ResultSlot slot = (ResultSlot) (Object) this;
		if (!(slot.container instanceof RecipeCraftingHolder holder)) {
			return;
		}

		RecipeHolder<?> recipe = holder.getRecipeUsed();
		if (recipe != null) {
			SpellLog.craft(recipe.id(), result);
		}
	}
}
