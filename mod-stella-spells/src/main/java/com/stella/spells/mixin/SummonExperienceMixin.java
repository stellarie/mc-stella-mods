package com.stella.spells.mixin;

import com.stella.spells.SummonSkeleton;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A share of every point of experience a master gains is credited to the servant that walks beside it. */
@Mixin(Player.class)
public abstract class SummonExperienceMixin {
	@Inject(method = "giveExperiencePoints", at = @At("HEAD"))
	private void stellaSpells$shareExperience(final int amount, final CallbackInfo callbackInfo) {
		if ((Object) this instanceof ServerPlayer master) {
			SummonSkeleton.shareGain(master, amount);
		}
	}
}
