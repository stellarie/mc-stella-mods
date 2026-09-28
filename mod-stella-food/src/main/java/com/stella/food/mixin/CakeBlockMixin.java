package com.stella.food.mixin;

import com.stella.food.FoodBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {
	@Redirect(
		method = "eat",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;canEat(Z)Z")
	)
	private static boolean stellaFood$allowCakeAtFullHunger(final Player player, final boolean alwaysEdible) {
		return true;
	}

	@Inject(method = "eat", at = @At("RETURN"))
	private static void stellaFood$applyCakeEffects(
		final LevelAccessor level,
		final BlockPos pos,
		final BlockState state,
		final Player player,
		final CallbackInfoReturnable<InteractionResult> callback
	) {
		if (callback.getReturnValue().consumesAction() && level instanceof Level actualLevel) {
			FoodBehavior.onCakeConsumed(actualLevel, player);
		}
	}
}
