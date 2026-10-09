package com.stella.rpg.mixin;

import com.stella.rpg.roll.Durability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class RpgDurabilityMixin {
	// Every use damage of a tool or armor piece passes this method, after Unbreaking.
	@Inject(method = "processDurabilityChange", at = @At("RETURN"), cancellable = true)
	private void stellaRpg$skipPoints(
		final int amount, final ServerLevel level, final ServerPlayer player, final CallbackInfoReturnable<Integer> callback
	) {
		int points = callback.getReturnValueI();
		if (points > 0) {
			callback.setReturnValue(Durability.reduce(points, (ItemStack) (Object) this, level.getRandom()));
		}
	}
}
