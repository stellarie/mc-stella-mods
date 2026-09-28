package com.stella.food.mixin;

import com.stella.food.FoodBehavior;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Consumable.class)
public abstract class ConsumableMixin {
	@Inject(method = "canConsume", at = @At("HEAD"), cancellable = true)
	private void stellaFood$allowFoodAtFullHunger(
		final LivingEntity user,
		final ItemStack stack,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (stack.get(DataComponents.FOOD) != null) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "onConsume", at = @At("HEAD"))
	private void stellaFood$applyFoodEffects(
		final Level level,
		final LivingEntity user,
		final ItemStack stack,
		final CallbackInfoReturnable<ItemStack> callback
	) {
		FoodBehavior.onConsumed(level, user, stack);
	}
}
