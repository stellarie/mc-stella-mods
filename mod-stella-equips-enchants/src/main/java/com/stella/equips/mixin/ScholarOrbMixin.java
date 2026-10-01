package com.stella.equips.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stella.equips.enchant.Scholar;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceOrb.class)
public abstract class ScholarOrbMixin {
	// The amount left after Mending.
	@WrapOperation(
		method = "playerTouch",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;giveExperiencePoints(I)V")
	)
	private void stellaEquips$scholar(final Player player, final int amount, final Operation<Void> original) {
		original.call(player, Scholar.scale(player, amount));
	}
}
