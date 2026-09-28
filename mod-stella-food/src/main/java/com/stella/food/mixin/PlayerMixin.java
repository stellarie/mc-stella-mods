package com.stella.food.mixin;

import com.stella.food.FoodBehavior;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float stellaFood$reduceExhaustion(final float amount) {
		return amount * FoodBehavior.exhaustionScale((Player) (Object) this);
	}
}
