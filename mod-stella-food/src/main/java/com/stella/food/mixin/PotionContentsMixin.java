package com.stella.food.mixin;

import com.stella.food.FoodItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PotionContents.class)
public abstract class PotionContentsMixin {
	// FoodBehavior applies a dish's potion; vanilla must not too.
	@Inject(method = "onConsume", at = @At("HEAD"), cancellable = true)
	private void stellaFood$dishPotionOnce(
		final Level level, final LivingEntity user, final ItemStack stack, final Consumable consumable, final CallbackInfo info
	) {
		if (FoodItems.isDish(stack.getItem())) {
			info.cancel();
		}
	}
}
